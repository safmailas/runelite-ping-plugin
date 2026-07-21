package com.pingworlds;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
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

@Slf4j
@PluginDescriptor(
	name = "Ping Worlds",
	description = "Ping a smart, context-filtered set of worlds and see which are best to log into",
	tags = {"ping", "world", "latency", "login", "hop"}
)
public class PingWorldsPlugin extends Plugin
{
	// Hard guardrails enforced in code no matter what config says. Config can make pinging gentler,
	// never more aggressive: at least this many seconds between refreshes, at most this many worlds.
	private static final int MIN_INTERVAL_SECONDS = 1;
	private static final int MAX_ACTIVE_WORLDS = 8;

	// Worlds at or above this player count are treated as full (matches RuneLite's World Hopper).
	private static final int FULL_WORLD_PLAYERS = 1950;

	// Small delay before the first ping so the world list has a chance to load after startup.
	private static final int INITIAL_DELAY_SECONDS = 2;

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

	// The most recently computed active set, published for the panel to read.
	private volatile List<Integer> lastActive = Collections.emptyList();

	private ScheduledExecutorService executor;
	private ScheduledFuture<?> pingTask;

	// Per-world rolling stats. Written on the executor thread, read from the Swing thread (M6), so a
	// concurrent map plus WorldPingStats' own synchronization keeps it safe.
	private final Map<Integer, WorldPingStats> statsByWorld = new ConcurrentHashMap<>();

	@Override
	protected void startUp()
	{
		log.debug("Ping Worlds started");

		// Add the sidebar panel + its nav button (works logged out, so you can pick a world before
		// logging in).
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

		int intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, config.pingIntervalSeconds());
		pingTask = executor.scheduleWithFixedDelay(
			this::pingActiveWorlds, INITIAL_DELAY_SECONDS, intervalSeconds, TimeUnit.SECONDS);
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
		lastActive = Collections.emptyList();

		log.debug("Ping Worlds stopped");
	}

	/**
	 * Runs on the background executor thread (never the client thread). Each cycle pings EVERY world
	 * in the active set (sequentially) and folds each result into that world's rolling stats. The set
	 * is small (capped at MAX_ACTIVE_WORLDS) and only the user's selected worlds are pinged, so this
	 * stays gentle while feeling responsive.
	 */
	private void pingActiveWorlds()
	{
		try
		{
			WorldResult worldResult = worldService.getWorlds();
			if (worldResult == null)
			{
				return; // world list not loaded yet
			}

			List<Integer> active = activeWorldIds(worldResult);
			lastActive = active;

			StringBuilder summary = new StringBuilder();
			for (int worldId : active)
			{
				World world = worldResult.findWorld(worldId);
				if (world == null)
				{
					continue;
				}
				// true = force a plain TCP-connect ping: portable pure-Java, no native calls.
				int ping = Ping.ping(world, true);
				statsByWorld.computeIfAbsent(worldId, id -> new WorldPingStats(config.sampleWindow()))
					.record(ping);

				if (summary.length() > 0)
				{
					summary.append(", ");
				}
				summary.append(worldId).append(':').append(ping < 0 ? "x" : ping);
			}
			log.debug("Ping cycle [{}]", summary);

			updatePanel(worldResult);
		}
		catch (Exception e)
		{
			// A thrown task would silently stop the scheduler, so swallow and log instead.
			log.debug("Ping cycle failed", e);
		}
	}

	/**
	 * The worlds to actively ping: the smart selector's emptiest-N matches for the current filter,
	 * always including the selected world. Custom profile falls back to the hand-typed world list.
	 * Always capped at MAX_ACTIVE_WORLDS regardless of config.
	 */
	private List<Integer> activeWorldIds(WorldResult worldResult)
	{
		int seedId = client.getWorld();
		int count = Math.min(config.activeWorldCount(), MAX_ACTIVE_WORLDS);

		if (config.profile() == PingProfile.CUSTOM)
		{
			return customWorldIds(seedId, count);
		}

		// Build a pure snapshot the selector can reason about (and that we unit test).
		List<WorldInfo> infos = new ArrayList<>();
		for (World w : worldResult.getWorlds())
		{
			infos.add(new WorldInfo(w.getId(), w.getPlayers(), w.getRegion(), w.getTypes()));
		}

		WorldFilter filter = buildFilter(worldResult, seedId);
		return WorldSelector.select(infos, filter, seedId, count);
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

	/** Custom profile: ping the user's hand-typed world list, or the selected world if it is empty. */
	private List<Integer> customWorldIds(int seedId, int count)
	{
		List<Integer> ids = WorldListParser.parse(config.customWorlds());
		if (ids.isEmpty() && seedId > 0)
		{
			ids = Collections.singletonList(seedId);
		}
		return ids.size() > count ? ids.subList(0, count) : ids;
	}

	/**
	 * Builds the display model (one row per active world, with region/players/activity + rolling
	 * stats) and pushes it to the panel on the Swing thread. Rows are sorted best-first: worlds with
	 * data and the lowest average ping come first. Runs on the executor thread.
	 */
	private void updatePanel(WorldResult worldResult)
	{
		final PingWorldsPanel p = panel;
		if (p == null)
		{
			return;
		}

		final int avgThreshold = config.avgThresholdMs();
		final int jitterThreshold = config.jitterThresholdMs();
		final int currentWorld = client.getWorld();

		final List<WorldStatus> rows = new ArrayList<>();
		for (int id : lastActive)
		{
			World world = worldResult.findWorld(id);
			String activity = world != null && world.getActivity() != null ? world.getActivity() : "";
			int players = world != null ? world.getPlayers() : -1;
			String region = world != null ? regionLabel(world.getRegion()) : "";

			WorldPingStats stats = statsByWorld.get(id);
			if (stats == null || !stats.hasData())
			{
				rows.add(new WorldStatus(id, region, players, activity, false, 0, 0, 0, false));
			}
			else
			{
				boolean green = stats.isConsistent(avgThreshold, jitterThreshold);
				rows.add(new WorldStatus(id, region, players, activity, true,
					stats.average(), stats.jitter(), stats.sampleCount(), green));
			}
		}

		// Best-first: worlds with data before those still gathering, then lowest average ping.
		rows.sort(Comparator
			.comparing(WorldStatus::hasData).reversed()
			.thenComparingInt(WorldStatus::getAverage)
			.thenComparingInt(WorldStatus::getWorldId));

		final String status = config.profile() + " · " + config.region();
		SwingUtilities.invokeLater(() -> p.display(rows, currentWorld, status));
	}

	/** Switches (hops) to the given world. Safe to call from the Swing thread. */
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
