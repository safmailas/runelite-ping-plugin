package com.pingworlds;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
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
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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
	// never more aggressive: at least this many seconds between pings, at most this many worlds.
	private static final int MIN_INTERVAL_SECONDS = 3;
	private static final int MAX_ACTIVE_WORLDS = 8;

	// Worlds at or above this player count are treated as full (matches RuneLite's World Hopper).
	private static final int FULL_WORLD_PLAYERS = 1950;

	// Small delay before the first ping so the world list has a chance to load after startup.
	private static final int INITIAL_DELAY_SECONDS = 3;

	@Inject
	private Client client;

	@Inject
	private WorldService worldService;

	@Inject
	private PingWorldsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	private PingWorldsPanel panel;
	private NavigationButton navButton;

	// The most recently computed active set, published for the panel to read.
	private volatile List<Integer> lastActive = Collections.emptyList();

	private ScheduledExecutorService executor;
	private ScheduledFuture<?> pingTask;

	// Per-world rolling stats. Written on the executor thread, read from the Swing thread (M6), so a
	// concurrent map plus WorldPingStats' own synchronization keeps it safe.
	private final Map<Integer, WorldPingStats> statsByWorld = new ConcurrentHashMap<>();

	// Round-robin position into the active world set. Only touched on the executor thread.
	private int cursor;

	@Override
	protected void startUp()
	{
		log.debug("Ping Worlds started");

		// Add the sidebar panel + its nav button (works logged out, so you can pick a world before
		// logging in).
		panel = new PingWorldsPanel();
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
			this::pingNextWorld, INITIAL_DELAY_SECONDS, intervalSeconds, TimeUnit.SECONDS);
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
		cursor = 0;

		log.debug("Ping Worlds stopped");
	}

	/**
	 * Runs on the background executor thread (never the client thread). Pings ONE world from the
	 * active set each interval, round-robin, and folds the result into that world's rolling stats.
	 * Pinging one world per interval (not all at once) is the DDoS-safe guardrail.
	 */
	private void pingNextWorld()
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
			if (active.isEmpty())
			{
				updatePanel();
				return; // nothing selected to ping
			}

			int worldId = active.get(cursor % active.size());
			cursor = (cursor + 1) % active.size();

			World world = worldResult.findWorld(worldId);
			if (world != null)
			{
				// true = force a plain TCP-connect ping: portable pure-Java, no native calls.
				int ping = Ping.ping(world, true);

				WorldPingStats stats = statsByWorld.computeIfAbsent(
					worldId, id -> new WorldPingStats(config.sampleWindow()));
				stats.record(ping);

				boolean good = stats.isConsistent(config.avgThresholdMs(), config.jitterThresholdMs());
				log.debug("World {}: {} (avg {} ms, jitter {} ms, {}) [{} samples]",
					worldId,
					ping < 0 ? "timeout" : ping + " ms",
					stats.average(), stats.jitter(),
					good ? "OK" : "unstable",
					stats.sampleCount());
			}

			updatePanel();
		}
		catch (Exception e)
		{
			// A thrown task would silently stop the scheduler, so swallow and log instead.
			log.debug("Ping failed", e);
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
	 * Builds the current display model from the active set + rolling stats and pushes it to the panel
	 * on the Swing thread. Runs on the executor thread; reads only thread-safe state.
	 */
	private void updatePanel()
	{
		final PingWorldsPanel p = panel;
		if (p == null)
		{
			return;
		}

		final List<Integer> active = lastActive;
		final int avgThreshold = config.avgThresholdMs();
		final int jitterThreshold = config.jitterThresholdMs();

		final List<WorldStatus> rows = new ArrayList<>();
		int bestWorld = -1;
		int bestJitter = Integer.MAX_VALUE;
		int bestAvg = Integer.MAX_VALUE;

		for (int id : active)
		{
			WorldPingStats stats = statsByWorld.get(id);
			if (stats == null || !stats.hasData())
			{
				rows.add(new WorldStatus(id, false, 0, 0, 0, false));
				continue;
			}

			int avg = stats.average();
			int jitter = stats.jitter();
			boolean green = stats.isConsistent(avgThreshold, jitterThreshold);
			rows.add(new WorldStatus(id, true, avg, jitter, stats.sampleCount(), green));

			// Recommend the steadiest green world (lowest jitter, then lowest average).
			if (green && (jitter < bestJitter || (jitter == bestJitter && avg < bestAvg)))
			{
				bestJitter = jitter;
				bestAvg = avg;
				bestWorld = id;
			}
		}

		final int recommended = bestWorld;
		final String status = config.profile() + " · " + config.region()
			+ " · " + active.size() + (active.size() == 1 ? " world" : " worlds");
		SwingUtilities.invokeLater(() -> p.display(rows, recommended, status));
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
