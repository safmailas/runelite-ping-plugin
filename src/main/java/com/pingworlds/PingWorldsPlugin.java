package com.pingworlds;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@PluginDescriptor(
	name = "Ping Worlds",
	description = "Scan worlds for ping and jitter and see which are the most stable to log into",
	tags = {"ping", "world", "latency", "login", "hop", "jitter"}
)
public class PingWorldsPlugin extends Plugin
{
	// Worlds at or above this player count are treated as full (matches RuneLite's World Hopper).
	private static final int FULL_WORLD_PLAYERS = 1950;

	// Small delay before the first ping so the world list has a chance to load after startup.
	private static final int INITIAL_DELAY_SECONDS = 2;

	// The sweep: process worlds in batches; ping each world in a batch this many times (rounds) before
	// moving to the next batch, and don't revisit a batch until the whole sweep has finished.
	private static final int BATCH_SIZE = 10;
	private static final int ROUNDS_PER_BATCH = 5;

	// Steady, gentle spacing between individual pings (~5 pings/second, to varied servers).
	private static final int PING_SPACING_MS = 200;

	// Don't rebuild the panel more often than this (the sweep ticks several times a second).
	private static final long PANEL_REFRESH_MS = 500;

	@Inject
	private Client client;

	@Inject
	private WorldService worldService;

	@Inject
	private PingWorldsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	private PingWorldsPanel panel;
	private NavigationButton navButton;

	private ScheduledExecutorService executor;
	private ScheduledFuture<?> pingTask;

	// Per-world rolling stats. Written on the executor thread, read from the Swing thread, so a
	// concurrent map plus WorldPingStats' own synchronization keeps it safe.
	private final Map<Integer, WorldPingStats> statsByWorld = new ConcurrentHashMap<>();

	// Worlds the user explicitly clicked. Written from the Swing thread, read from the executor
	// thread, so it is synchronized; insertion order preserved.
	private final Set<Integer> pinned = Collections.synchronizedSet(new LinkedHashSet<>());

	// Sweep state — only touched on the executor thread.
	private final List<Integer> sweepOrder = new ArrayList<>();
	private int batchIndex;
	private int roundIndex;
	private int posInBatch;
	private long lastPanelUpdateMs;

	@Override
	protected void startUp()
	{
		log.debug("Ping Worlds started");

		// Sidebar panel + nav button (works logged out, so you can pick a world before logging in).
		panel = new PingWorldsPanel(this::switchToWorld);
		navButton = NavigationButton.builder()
			.tooltip("Ping Worlds")
			.icon(createNavIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// A single background thread. Pings are blocking network IO, which must NEVER run on the
		// game (client) thread — that would freeze RuneLite's UI.
		executor = Executors.newSingleThreadScheduledExecutor();
		pingTask = executor.schedule(this::tick, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown()
	{
		// Cancel the scheduled task AND stop the executor. Per AGENTS.md: explicitly cancel the
		// future, use shutdownNow(), and never block shutDown() with awaitTermination().
		if (pingTask != null)
		{
			pingTask.cancel(true);
			pingTask = null;
		}
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
		statsByWorld.clear();
		pinned.clear();
		sweepOrder.clear();
		batchIndex = 0;
		roundIndex = 0;
		posInBatch = 0;

		log.debug("Ping Worlds stopped");
	}

	/** One scheduler tick: ping a single world, then schedule the next tick a steady spacing later. */
	private void tick()
	{
		try
		{
			sweepStep();
		}
		catch (Exception e)
		{
			// A thrown task would silently stop the scheduler, so swallow and log instead.
			log.debug("Sweep step failed", e);
		}
		finally
		{
			ScheduledExecutorService ex = executor;
			if (ex != null && !ex.isShutdown())
			{
				pingTask = ex.schedule(this::tick, PING_SPACING_MS, TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Runs on the executor thread (never the client thread). Pings ONE world, advancing through the
	 * relevance-ordered sweep in batches: each world is pinged {@link #ROUNDS_PER_BATCH} times within
	 * its batch before we move on, and a batch is not revisited until the whole sweep completes.
	 */
	private void sweepStep()
	{
		WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null)
		{
			return; // world list not loaded yet
		}

		if (sweepOrder.isEmpty())
		{
			rebuildSweep(worldResult);
		}
		if (sweepOrder.isEmpty())
		{
			maybeUpdatePanel(worldResult);
			return;
		}

		int total = sweepOrder.size();
		int batchCount = (total + BATCH_SIZE - 1) / BATCH_SIZE;
		if (batchIndex >= batchCount)
		{
			// Full sweep complete — rebuild (worlds / pins / populations may have changed) and restart.
			rebuildSweep(worldResult);
			if (sweepOrder.isEmpty())
			{
				maybeUpdatePanel(worldResult);
				return;
			}
			total = sweepOrder.size();
			batchCount = (total + BATCH_SIZE - 1) / BATCH_SIZE;
		}

		int batchStart = batchIndex * BATCH_SIZE;
		int batchSize = Math.min(BATCH_SIZE, total - batchStart);
		if (posInBatch >= batchSize)
		{
			posInBatch = 0;
		}

		int worldId = sweepOrder.get(batchStart + posInBatch);
		World world = worldResult.findWorld(worldId);
		if (world != null)
		{
			// Reuse RuneLite World Hopper's ping: native ICMP first, TCP-connect fallback if it fails
			// (the true flag). The native code lives in runelite-client, so our plugin uses no JNA.
			int ping = Ping.ping(world, true);
			statsByWorld.computeIfAbsent(worldId, id -> new WorldPingStats(config.sampleWindow()))
				.record(ping);
		}

		advanceSweep(batchSize);
		maybeUpdatePanel(worldResult);
	}

	private void advanceSweep(int batchSize)
	{
		posInBatch++;
		if (posInBatch >= batchSize)
		{
			posInBatch = 0;
			roundIndex++;
			if (roundIndex >= ROUNDS_PER_BATCH)
			{
				roundIndex = 0;
				batchIndex++; // next step rebuilds/restarts if this passes the last batch
			}
		}
	}

	/**
	 * Rebuilds the relevance-ordered sweep list: the current world and any pinned worlds first, then
	 * every eligible world (all regions, both member/f2p subgroups) ordered by your region, then your
	 * account subgroup, then emptiest. Resets the sweep position.
	 */
	private void rebuildSweep(WorldResult worldResult)
	{
		sweepOrder.clear();
		batchIndex = 0;
		roundIndex = 0;
		posInBatch = 0;

		int current = client.getWorld();
		LinkedHashSet<Integer> order = new LinkedHashSet<>();
		if (current > 0)
		{
			order.add(current);
		}
		synchronized (pinned)
		{
			order.addAll(pinned);
		}

		if (config.profile() == PingProfile.CUSTOM)
		{
			order.addAll(WorldListParser.parse(config.customWorlds()));
			sweepOrder.addAll(order);
			return;
		}

		WorldFilter filter = buildFilter(worldResult, current);
		WorldRegion myRegion = filter.getRegion();
		boolean membersPref = config.accountType() == AccountType.MEMBERS;

		List<World> eligible = new ArrayList<>();
		for (World w : worldResult.getWorlds())
		{
			WorldInfo info = new WorldInfo(w.getId(), w.getPlayers(), w.getRegion(), w.getActivity(), w.getTypes());
			if (WorldSelector.eligible(info, filter))
			{
				eligible.add(w);
			}
		}
		eligible.sort(Comparator
			.comparing((World w) -> !(myRegion != null && w.getRegion() == myRegion)) // your region first
			.thenComparing((World w) -> membersPref != w.getTypes().contains(WorldType.MEMBERS)) // your subgroup first
			.thenComparingInt(World::getPlayers)); // emptiest first within each group

		for (World w : eligible)
		{
			order.add(w.getId());
		}
		sweepOrder.addAll(order);
	}

	/**
	 * Builds the world filter from config: the selected play-style profile provides the rules, and
	 * region AUTO resolves to the region of the currently selected world.
	 */
	private WorldFilter buildFilter(WorldResult worldResult, int seedId)
	{
		WorldRegion region;
		if (config.region() == RegionOption.AUTO)
		{
			World seed = worldResult.findWorld(seedId);
			region = seed != null ? seed.getRegion() : null;
		}
		else
		{
			region = config.region().toWorldRegion();
		}

		return ProfileFilter.forProfile(config.profile(), region, config.accountType(), FULL_WORLD_PLAYERS);
	}

	private void maybeUpdatePanel(WorldResult worldResult)
	{
		long now = System.currentTimeMillis();
		if (now - lastPanelUpdateMs >= PANEL_REFRESH_MS)
		{
			lastPanelUpdateMs = now;
			updatePanel(worldResult);
		}
	}

	/**
	 * Builds the display model — the current world, pinned worlds, and the best profile-relevant
	 * worlds (up to the configured count) — and pushes it to the panel on the Swing thread, sorted
	 * best-first. Runs on the executor thread.
	 */
	private void updatePanel(WorldResult worldResult)
	{
		final PingWorldsPanel p = panel;
		if (p == null)
		{
			return;
		}

		final int avgT = config.avgThresholdMs();
		final int jitT = config.jitterThresholdMs();
		final int current = client.getWorld();
		final int limit = config.worldsToShow();

		LinkedHashSet<Integer> shown = new LinkedHashSet<>();
		if (current > 0)
		{
			shown.add(current);
		}
		synchronized (pinned)
		{
			shown.addAll(pinned);
		}

		List<World> relevant = new ArrayList<>();
		if (config.profile() == PingProfile.CUSTOM)
		{
			for (int id : WorldListParser.parse(config.customWorlds()))
			{
				World w = worldResult.findWorld(id);
				if (w != null)
				{
					relevant.add(w);
				}
			}
		}
		else
		{
			WorldFilter filter = buildFilter(worldResult, current);
			for (World w : worldResult.getWorlds())
			{
				WorldInfo info = new WorldInfo(w.getId(), w.getPlayers(), w.getRegion(), w.getActivity(), w.getTypes());
				if (WorldSelector.passes(info, filter))
				{
					relevant.add(w);
				}
			}
		}

		relevant.sort(Comparator.comparingDouble((World w) -> displayScore(w.getId(), avgT, jitT)));
		for (World w : relevant)
		{
			if (shown.size() >= limit)
			{
				break;
			}
			shown.add(w.getId());
		}

		final List<WorldStatus> rows = new ArrayList<>();
		for (int id : shown)
		{
			rows.add(buildStatus(id, worldResult, avgT, jitT));
		}
		rows.sort(Comparator
			.comparing(WorldStatus::hasData).reversed()
			.thenComparingInt(WorldStatus::getAverage)
			.thenComparingInt(WorldStatus::getWorldId));

		final String status = config.profile() + " · " + config.region();
		SwingUtilities.invokeLater(() -> p.display(rows, current, status));
	}

	/** Lower = better (shown first): consistent worlds by average, then unstable, then no-data last. */
	private double displayScore(int id, int avgT, int jitT)
	{
		WorldPingStats s = statsByWorld.get(id);
		if (s == null || !s.hasData())
		{
			return 100_000;
		}
		return s.isConsistent(avgT, jitT) ? s.average() : 10_000 + s.average();
	}

	private WorldStatus buildStatus(int id, WorldResult worldResult, int avgT, int jitT)
	{
		World w = worldResult.findWorld(id);
		String activity = w != null && w.getActivity() != null ? w.getActivity() : "";
		int players = w != null ? w.getPlayers() : -1;
		String region = w != null ? regionLabel(w.getRegion()) : "";

		WorldPingStats s = statsByWorld.get(id);
		if (s == null || !s.hasData())
		{
			return new WorldStatus(id, region, players, activity, false, 0, 0, 0, false);
		}
		return new WorldStatus(id, region, players, activity, true,
			s.average(), s.jitter(), s.sampleCount(), s.isConsistent(avgT, jitT));
	}

	/** Switches (hops) to the given world AND pins it so it keeps being scanned. Called from the EDT. */
	private void switchToWorld(int worldId)
	{
		WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null)
		{
			return;
		}
		World world = worldResult.findWorld(worldId);
		if (world == null)
		{
			return;
		}

		pinned.add(worldId); // keep monitoring this world even after we move on

		// changeWorld must run on the client thread. Works at the login screen (sets the world you
		// will log into) and in-game (hops), mirroring RuneLite's own World Hopper.
		clientThread.invoke(() ->
		{
			net.runelite.api.World rsWorld = client.createWorld();
			rsWorld.setActivity(world.getActivity());
			rsWorld.setAddress(world.getAddress());
			rsWorld.setId(world.getId());
			rsWorld.setLocation(world.getLocation());
			rsWorld.setPlayerCount(world.getPlayers());
			rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
			client.changeWorld(rsWorld);
		});
	}

	/** Short region code for display, e.g. "US". Empty when unknown. */
	private static String regionLabel(WorldRegion region)
	{
		if (region == null)
		{
			return "";
		}
		switch (region)
		{
			case UNITED_STATES_OF_AMERICA:
				return "US";
			case UNITED_KINGDOM:
				return "UK";
			case GERMANY:
				return "DE";
			case AUSTRALIA:
				return "AU";
			default:
				return region.name().substring(0, Math.min(2, region.name().length()));
		}
	}

	/** A simple generated nav-button icon (a green "signal" dot). A polished icon.png comes in M7. */
	private static BufferedImage createNavIcon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(76, 175, 80));
		g.fillOval(5, 5, 14, 14);
		g.dispose();
		return image;
	}

	@Provides
	PingWorldsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PingWorldsConfig.class);
	}
}
