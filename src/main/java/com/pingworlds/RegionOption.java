package com.pingworlds;

import net.runelite.http.api.worlds.WorldRegion;

/**
 * Which server region to consider. AUTO means "match the world RuneLite currently has selected"
 * (resolved from Client.getWorld() at runtime) — the honest stand-in for auto-detecting your
 * location, since we cannot know it before you log in.
 */
public enum RegionOption
{
	AUTO("Auto (match my current world)", null),
	US("United States", WorldRegion.UNITED_STATES_OF_AMERICA),
	UK("United Kingdom", WorldRegion.UNITED_KINGDOM),
	AUSTRALIA("Australia", WorldRegion.AUSTRALIA),
	GERMANY("Germany", WorldRegion.GERMANY);

	private final String displayName;
	private final WorldRegion worldRegion; // null for AUTO

	RegionOption(String displayName, WorldRegion worldRegion)
	{
		this.displayName = displayName;
		this.worldRegion = worldRegion;
	}

	/** The RuneLite region this maps to, or null for AUTO (resolved from the current world at runtime). */
	public WorldRegion toWorldRegion()
	{
		return worldRegion;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
