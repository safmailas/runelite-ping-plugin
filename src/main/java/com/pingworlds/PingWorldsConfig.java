package com.pingworlds;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(PingWorldsConfig.GROUP)
public interface PingWorldsConfig extends Config
{
	/**
	 * The config group name. Kept as a constant so it is never re-typed (and so a future rename is a
	 * one-line change). Per AGENTS.md, this group must never be renamed without a migration, because
	 * renaming silently resets everyone's saved settings.
	 */
	String GROUP = "pingworlds";

	// ---------------------------------------------------------------------------------------------
	// What to ping (smart selection). The logic that reads these arrives in M4/M5.
	// ---------------------------------------------------------------------------------------------

	@ConfigItem(
		keyName = "profile",
		name = "Play-style profile",
		description = "Pick a goal; the plugin filters worlds to match it.",
		position = 0
	)
	default PingProfile profile()
	{
		return PingProfile.STABLE_BOSSING;
	}

	@ConfigItem(
		keyName = "region",
		name = "Region",
		description = "Which server region to consider. Auto matches your currently-selected world.",
		position = 1
	)
	default RegionOption region()
	{
		return RegionOption.AUTO;
	}

	@ConfigItem(
		keyName = "accountType",
		name = "Account type",
		description = "Members or free-to-play. Decides which worlds you can actually log into.",
		position = 2
	)
	default AccountType accountType()
	{
		return AccountType.MEMBERS;
	}

	@ConfigItem(
		keyName = "customWorlds",
		name = "Custom world list",
		description = "Comma-separated world numbers, used when the profile is Custom (e.g. 301, 302, 330).",
		position = 3
	)
	default String customWorlds()
	{
		return "";
	}

	@Range(min = 1, max = 8)
	@ConfigItem(
		keyName = "activeWorldCount",
		name = "Worlds to monitor",
		description = "How many worlds to actively ping (the emptiest matches are auto-picked). Capped at 8 for server safety.",
		position = 4
	)
	default int activeWorldCount()
	{
		return 5;
	}

	// ---------------------------------------------------------------------------------------------
	// How it pings (cadence + sampling). These feed the DDoS-safe scheduler in M2/M3.
	// ---------------------------------------------------------------------------------------------

	@Range(min = 3, max = 60)
	@ConfigItem(
		keyName = "pingIntervalSeconds",
		name = "Ping interval (seconds)",
		description = "Seconds between pings. One world is pinged per interval. A 3s floor is enforced in code for server safety.",
		position = 5
	)
	default int pingIntervalSeconds()
	{
		return 5;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "sampleWindow",
		name = "Samples per world",
		description = "How many recent pings to average per world.",
		position = 6
	)
	default int sampleWindow()
	{
		return 10;
	}

	// ---------------------------------------------------------------------------------------------
	// What earns a green check. Both thresholds must be met (M3).
	// ---------------------------------------------------------------------------------------------

	@Range(min = 10, max = 1000)
	@ConfigItem(
		keyName = "avgThresholdMs",
		name = "Max average ping (ms)",
		description = "A world is green only if its average ping is under this.",
		position = 7
	)
	default int avgThresholdMs()
	{
		return 80;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "jitterThresholdMs",
		name = "Max jitter (ms)",
		description = "A world is green only if its jitter (consistency) is under this.",
		position = 8
	)
	default int jitterThresholdMs()
	{
		return 15;
	}
}
