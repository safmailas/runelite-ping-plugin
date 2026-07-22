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

	@Range(min = 3, max = 20)
	@ConfigItem(
		keyName = "activeWorldCount",
		name = "Worlds to show",
		description = "How many worlds to list in the panel (best-first). All eligible worlds are still scanned for ping/jitter in the background.",
		position = 4
	)
	default int worldsToShow()
	{
		return 10;
	}

	// ---------------------------------------------------------------------------------------------
	// Sampling. How many recent pings define a world's average + jitter.
	// ---------------------------------------------------------------------------------------------

	@Range(min = 3, max = 100)
	@ConfigItem(
		keyName = "sampleWindow",
		name = "Samples per world",
		description = "How many recent pings to average per world (also drives the jitter estimate).",
		position = 5
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
