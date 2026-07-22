package com.pingworlds;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * A plain, immutable snapshot of just the world facts the selector reasons about. Decoupling from
 * RuneLite's live World type keeps WorldSelector pure and unit-testable (we can build these by hand
 * in tests, no game client required).
 */
public final class WorldInfo
{
	private final int id;
	private final int players;         // -1 when the world is offline / player count unknown
	private final WorldRegion region;  // nullable
	private final String activity;     // world activity text; blank / "-" means a generic world
	private final Set<WorldType> types;

	public WorldInfo(int id, int players, WorldRegion region, String activity, Set<WorldType> types)
	{
		this.id = id;
		this.players = players;
		this.region = region;
		this.activity = activity == null ? "" : activity;
		this.types = (types == null || types.isEmpty())
			? EnumSet.noneOf(WorldType.class)
			: EnumSet.copyOf(types);
	}

	/** A generic world has no designated activity (blank or "-"). */
	public boolean isGeneric()
	{
		String a = activity.trim();
		return a.isEmpty() || a.equals("-");
	}

	public String getActivity()
	{
		return activity;
	}

	public int getId()
	{
		return id;
	}

	public int getPlayers()
	{
		return players;
	}

	public WorldRegion getRegion()
	{
		return region;
	}

	public Set<WorldType> getTypes()
	{
		return Collections.unmodifiableSet(types);
	}

	public boolean hasType(WorldType type)
	{
		return types.contains(type);
	}
}
