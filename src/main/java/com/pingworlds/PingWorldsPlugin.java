package com.pingworlds;

import com.google.inject.Provides;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.worldhopper.ping.Ping;
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
		statsByWorld.clear();
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
			if (active.isEmpty())
			{
				return; // nothing selected to ping
			}

			int worldId = active.get(cursor % active.size());
			cursor = (cursor + 1) % active.size();

			World world = worldResult.findWorld(worldId);
			if (world == null)
			{
				return; // unknown world number
			}

			// true = force a plain TCP-connect ping: portable pure-Java, no native calls in our code.
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

	@Provides
	PingWorldsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PingWorldsConfig.class);
	}
}
