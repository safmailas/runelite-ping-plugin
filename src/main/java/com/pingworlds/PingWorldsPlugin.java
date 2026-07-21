package com.pingworlds;

import com.google.inject.Provides;
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
import net.runelite.http.api.worlds.WorldResult;

@Slf4j
@PluginDescriptor(
	name = "Ping Worlds",
	description = "Ping a smart, context-filtered set of worlds and see which are best to log into",
	tags = {"ping", "world", "latency", "login", "hop"}
)
public class PingWorldsPlugin extends Plugin
{
	// Hard floor enforced in code no matter what config says — a guardrail the plugin must never
	// forget. Config can make pinging gentler (higher interval), never more aggressive than this.
	private static final int MIN_INTERVAL_SECONDS = 3;

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

	@Override
	protected void startUp()
	{
		log.debug("Ping Worlds started");

		// A single background thread. Pings are blocking network IO, which must NEVER run on the
		// game (client) thread — that would freeze RuneLite's UI.
		executor = Executors.newSingleThreadScheduledExecutor();

		int intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, config.pingIntervalSeconds());
		pingTask = executor.scheduleWithFixedDelay(
			this::pingSeedWorld, INITIAL_DELAY_SECONDS, intervalSeconds, TimeUnit.SECONDS);
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

		log.debug("Ping Worlds stopped");
	}

	/**
	 * Runs on the background executor thread (never the client thread). Pings the world RuneLite
	 * currently has selected and logs the round-trip time. In M3 this becomes a round-robin over a
	 * smart-selected set of worlds.
	 */
	private void pingSeedWorld()
	{
		try
		{
			// Lightweight read of the currently-selected world. At the login screen this is the
			// world shown in the login world switcher, so we can ping before logging in.
			int worldId = client.getWorld();
			if (worldId <= 0)
			{
				return; // no world selected yet
			}

			// getWorlds() is cached and may be null until the first fetch completes. Safe to block
			// here because we are on the background thread, not the client thread.
			WorldResult worldResult = worldService.getWorlds();
			if (worldResult == null)
			{
				return; // world list not loaded yet
			}

			World world = worldResult.findWorld(worldId);
			if (world == null)
			{
				return; // unknown world number
			}

			// true = force a plain TCP-connect ping: portable pure-Java, no native calls in our code.
			int ping = Ping.ping(world, true);
			String result = ping < 0 ? "timeout" : ping + " ms";
			log.debug("Ping to world {}: {}", worldId, result);
		}
		catch (Exception e)
		{
			// A thrown task would silently stop the scheduler, so swallow and log instead.
			log.debug("Ping failed", e);
		}
	}

	@Provides
	PingWorldsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PingWorldsConfig.class);
	}
}
