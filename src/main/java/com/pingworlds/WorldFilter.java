package com.pingworlds;

import net.runelite.http.api.worlds.WorldRegion;

/**
 * The rules for which worlds are candidates. A play-style profile (M5) is really just a preset
 * WorldFilter, so this is the shape that both config and profiles produce.
 */
public final class WorldFilter
{
	private final WorldRegion region;      // null = any region
	private final boolean requireMembers;  // true = members worlds only, false = free-to-play only
	private final boolean includePvp;      // include PvP / high-risk / deadman / LMS worlds
	private final boolean includeLeagues;  // include seasonal (Leagues) worlds
	private final int maxPlayers;          // worlds at or above this are "full" and excluded

	public WorldFilter(WorldRegion region, boolean requireMembers, boolean includePvp,
		boolean includeLeagues, int maxPlayers)
	{
		this.region = region;
		this.requireMembers = requireMembers;
		this.includePvp = includePvp;
		this.includeLeagues = includeLeagues;
		this.maxPlayers = maxPlayers;
	}

	public WorldRegion getRegion()
	{
		return region;
	}

	public boolean isRequireMembers()
	{
		return requireMembers;
	}

	public boolean isIncludePvp()
	{
		return includePvp;
	}

	public boolean isIncludeLeagues()
	{
		return includeLeagues;
	}

	public int getMaxPlayers()
	{
		return maxPlayers;
	}
}
