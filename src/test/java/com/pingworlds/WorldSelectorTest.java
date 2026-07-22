package com.pingworlds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The contract for WorldSelector: filtering rules, emptiest-N ordering, seed handling, and the edge
 * cases (empty input, all full, offline, null region, count cap).
 */
public class WorldSelectorTest
{
	private static final WorldRegion US = WorldRegion.UNITED_STATES_OF_AMERICA;
	private static final WorldRegion UK = WorldRegion.UNITED_KINGDOM;

	// Standard "stable login" filter: US, members, no PvP, no Leagues, 1950 = full.
	private static WorldFilter membersUs()
	{
		return new WorldFilter(US, true, false, false, 1950);
	}

	private static WorldInfo w(int id, int players, WorldRegion region, WorldType... types)
	{
		return world(id, players, region, "", types); // generic (no activity) by default
	}

	private static WorldInfo world(int id, int players, WorldRegion region, String activity, WorldType... types)
	{
		Set<WorldType> set = types.length == 0
			? EnumSet.noneOf(WorldType.class)
			: EnumSet.copyOf(Arrays.asList(types));
		return new WorldInfo(id, players, region, activity, set);
	}

	@Test
	public void picksEmptiestFirst()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, 500, US, WorldType.MEMBERS),
			w(302, 100, US, WorldType.MEMBERS),
			w(303, 300, US, WorldType.MEMBERS));
		assertEquals(Arrays.asList(302, 303), WorldSelector.select(all, membersUs(), 0, 2));
	}

	@Test
	public void excludesFullWorlds()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, 1950, US, WorldType.MEMBERS),   // full
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void keepsFullWorldsThatHaveAnActivity()
	{
		List<WorldInfo> all = Arrays.asList(
			world(301, 1980, US, "Trade - Members", WorldType.MEMBERS), // full but non-generic -> kept
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Arrays.asList(302, 301), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void excludesGenericFullWorlds()
	{
		List<WorldInfo> all = Arrays.asList(
			world(301, 1980, US, "", WorldType.MEMBERS), // full AND generic -> excluded
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void excludesOfflineWorlds()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, -1, US, WorldType.MEMBERS),     // offline
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void excludesWrongRegion()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, 100, UK, WorldType.MEMBERS),
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void nullRegionExcludedWhenTargetRegionSet()
	{
		List<WorldInfo> all = Collections.singletonList(w(301, 100, null, WorldType.MEMBERS));
		assertEquals(Collections.emptyList(), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void nullRegionAllowedWhenFilterRegionIsAny()
	{
		WorldFilter anyRegion = new WorldFilter(null, true, false, false, 1950);
		List<WorldInfo> all = Collections.singletonList(w(301, 100, null, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(301), WorldSelector.select(all, anyRegion, 0, 5));
	}

	@Test
	public void membersFilterExcludesFreeToPlay()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, 100, US),                       // no MEMBERS type => f2p
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void freeToPlayFilterExcludesMembers()
	{
		WorldFilter f2pUs = new WorldFilter(US, false, false, false, 1950);
		List<WorldInfo> all = Arrays.asList(
			w(301, 100, US, WorldType.MEMBERS),
			w(302, 100, US));                      // f2p
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, f2pUs, 0, 5));
	}

	@Test
	public void excludesPvpByDefaultButIncludesWhenFlagged()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, 100, US, WorldType.MEMBERS, WorldType.PVP),
			w(302, 200, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 0, 5));

		WorldFilter withPvp = new WorldFilter(US, true, true, false, 1950);
		assertEquals(Arrays.asList(301, 302), WorldSelector.select(all, withPvp, 0, 5));
	}

	@Test
	public void excludesSeasonalByDefaultButIncludesWhenLeaguesFlagged()
	{
		List<WorldInfo> all = Collections.singletonList(
			w(301, 100, US, WorldType.MEMBERS, WorldType.SEASONAL));
		assertEquals(Collections.emptyList(), WorldSelector.select(all, membersUs(), 0, 5));

		WorldFilter withLeagues = new WorldFilter(US, true, false, true, 1950);
		assertEquals(Collections.singletonList(301), WorldSelector.select(all, withLeagues, 0, 5));
	}

	@Test
	public void alwaysExcludesSkillTotalWorlds()
	{
		List<WorldInfo> all = Collections.singletonList(
			w(301, 100, US, WorldType.MEMBERS, WorldType.SKILL_TOTAL));
		assertEquals(Collections.emptyList(), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void seedIsAlwaysIncludedFirstEvenIfItFailsTheFilter()
	{
		List<WorldInfo> all = Arrays.asList(
			w(305, 1950, UK, WorldType.MEMBERS),   // seed: full AND wrong region
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Arrays.asList(305, 302), WorldSelector.select(all, membersUs(), 305, 2));
	}

	@Test
	public void seedIsNotIncludedWhenOffline()
	{
		List<WorldInfo> all = Arrays.asList(
			w(305, -1, US, WorldType.MEMBERS),     // seed offline
			w(302, 100, US, WorldType.MEMBERS));
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, membersUs(), 305, 5));
	}

	@Test
	public void seedIsNotDuplicatedWhenAlsoAMatch()
	{
		List<WorldInfo> all = Arrays.asList(
			w(302, 100, US, WorldType.MEMBERS),
			w(303, 200, US, WorldType.MEMBERS));
		// seed 302 also passes the filter; it should appear once, first.
		assertEquals(Arrays.asList(302, 303), WorldSelector.select(all, membersUs(), 302, 5));
	}

	@Test
	public void emptyInputReturnsEmpty()
	{
		assertEquals(Collections.emptyList(),
			WorldSelector.select(new ArrayList<>(), membersUs(), 0, 5));
	}

	@Test
	public void allFullWithNoSeedReturnsEmpty()
	{
		List<WorldInfo> all = Arrays.asList(
			w(301, 1950, US, WorldType.MEMBERS),
			w(302, 2000, US, WorldType.MEMBERS));
		assertEquals(Collections.emptyList(), WorldSelector.select(all, membersUs(), 0, 5));
	}

	@Test
	public void countIsCappedAtEight()
	{
		List<WorldInfo> all = new ArrayList<>();
		for (int i = 0; i < 12; i++)
		{
			all.add(w(300 + i, 100 + i, US, WorldType.MEMBERS));
		}
		assertEquals(8, WorldSelector.select(all, membersUs(), 0, 20).size());
	}

	@Test
	public void passesRuleDirectly()
	{
		assertTrue(WorldSelector.passes(w(302, 100, US, WorldType.MEMBERS), membersUs()));
		assertFalse(WorldSelector.passes(w(302, 100, UK, WorldType.MEMBERS), membersUs()));
	}
}
