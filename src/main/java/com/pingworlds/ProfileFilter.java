package com.pingworlds;

import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * Maps a play-style profile to a concrete {@link WorldFilter}. A profile is just a named preset of
 * filter rules for a goal, so this is the single place that decides what each profile means.
 * (The CUSTOM profile is handled separately by the plugin's hand-typed world list, not here.)
 */
public final class ProfileFilter
{
	private ProfileFilter()
	{
	}

	public static WorldFilter forProfile(PingProfile profile, WorldRegion region,
		AccountType accountType, int maxPlayers)
	{
		boolean members = accountType == AccountType.MEMBERS;

		switch (profile)
		{
			case FREE_TO_PLAY:
				// A free-to-play goal always means f2p worlds, regardless of the account-type setting.
				return new WorldFilter(region, false, false, false, maxPlayers);

			case PVP:
				// Show PvP / high-risk / deadman / LMS worlds too.
				return new WorldFilter(region, members, true, false, maxPlayers);

			case LEAGUES:
				// Leagues run on SEASONAL worlds; require that type and allow seasonal through.
				return new WorldFilter(region, members, false, true, maxPlayers, WorldType.SEASONAL);

			case STABLE_BOSSING:
			case CUSTOM:
			default:
				// Standard, stable worlds: no PvP, no Leagues, no special worlds.
				return new WorldFilter(region, members, false, false, maxPlayers);
		}
	}
}
