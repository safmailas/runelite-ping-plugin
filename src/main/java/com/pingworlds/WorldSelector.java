package com.pingworlds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.http.api.worlds.WorldType;

/**
 * Pure world-selection logic: take the full world list, keep the ones matching a filter, and pick
 * the emptiest N — always evaluating the currently-selected world too. No RuneLite client needed,
 * so every rule here is unit-tested.
 */
public final class WorldSelector
{
	// Hard cap regardless of requested count (the DDoS guardrail is also enforced in the plugin).
	static final int MAX_ACTIVE_WORLDS = 8;

	// PvP-style worlds: risky to log into by accident, excluded unless a profile opts in.
	private static final Set<WorldType> PVP_TYPES = EnumSet.of(
		WorldType.PVP, WorldType.PVP_ARENA, WorldType.HIGH_RISK, WorldType.BOUNTY,
		WorldType.LAST_MAN_STANDING, WorldType.DEADMAN);

	// Leagues run on SEASONAL worlds (there is no WorldType.LEAGUE).
	private static final Set<WorldType> SEASONAL_TYPES = EnumSet.of(WorldType.SEASONAL);

	// Non-standard worlds we never treat as a normal login target.
	private static final Set<WorldType> ALWAYS_EXCLUDED = EnumSet.of(
		WorldType.SKILL_TOTAL, WorldType.TOURNAMENT, WorldType.BETA_WORLD,
		WorldType.FRESH_START_WORLD, WorldType.NOSAVE_MODE, WorldType.QUEST_SPEEDRUNNING);

	private WorldSelector()
	{
	}

	/**
	 * @param all         every known world (as pure snapshots)
	 * @param filter      the rules for which worlds are candidates
	 * @param seedWorldId the currently-selected world; always included first if online (even if it
	 *                    fails the filter), so you can always compare your current world
	 * @param count       how many worlds to return (clamped to [0, MAX_ACTIVE_WORLDS])
	 * @return world ids, seed first, then the emptiest matching worlds
	 */
	public static List<Integer> select(List<WorldInfo> all, WorldFilter filter, int seedWorldId, int count)
	{
		int limit = Math.min(Math.max(count, 0), MAX_ACTIVE_WORLDS);
		LinkedHashSet<Integer> result = new LinkedHashSet<>();
		if (all == null || all.isEmpty() || limit == 0)
		{
			return new ArrayList<>(result);
		}

		// 1) Always evaluate the currently-selected world (if online), even if it fails the filter.
		WorldInfo seed = findById(all, seedWorldId);
		if (seed != null && seed.getPlayers() >= 0)
		{
			result.add(seed.getId());
		}

		// 2) Fill the rest with the emptiest matching worlds (ties broken by id for determinism).
		all.stream()
			.filter(w -> passes(w, filter))
			.sorted(Comparator.comparingInt(WorldInfo::getPlayers).thenComparingInt(WorldInfo::getId))
			.forEach(w ->
			{
				if (result.size() < limit)
				{
					result.add(w.getId());
				}
			});

		return new ArrayList<>(result);
	}

	/** True if a world passes the filter (public-ish for direct testing). */
	static boolean passes(WorldInfo w, WorldFilter f)
	{
		if (w.getPlayers() < 0)
		{
			return false; // offline
		}
		if (w.getPlayers() >= f.getMaxPlayers())
		{
			return false; // full
		}

		boolean isMembers = w.hasType(WorldType.MEMBERS);
		if (f.isRequireMembers() != isMembers)
		{
			return false; // members account wants members worlds; f2p wants free worlds
		}

		if (f.getRegion() != null && w.getRegion() != f.getRegion())
		{
			return false; // wrong region (a null world region never matches a specific target)
		}

		if (!f.isIncludePvp() && intersects(w, PVP_TYPES))
		{
			return false;
		}
		if (!f.isIncludeLeagues() && intersects(w, SEASONAL_TYPES))
		{
			return false;
		}
		if (intersects(w, ALWAYS_EXCLUDED))
		{
			return false;
		}

		return true;
	}

	private static boolean intersects(WorldInfo w, Set<WorldType> types)
	{
		for (WorldType t : types)
		{
			if (w.hasType(t))
			{
				return true;
			}
		}
		return false;
	}

	private static WorldInfo findById(List<WorldInfo> all, int id)
	{
		if (id <= 0)
		{
			return null;
		}
		for (WorldInfo w : all)
		{
			if (w.getId() == id)
			{
				return w;
			}
		}
		return null;
	}
}
