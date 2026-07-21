package com.pingworlds;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The contract for ProfileFilter: each play-style profile must map to the expected filter rules.
 */
public class ProfileFilterTest
{
	private static final WorldRegion US = WorldRegion.UNITED_STATES_OF_AMERICA;

	@Test
	public void stableBossingIsStandardMembersNoSpecials()
	{
		WorldFilter f = ProfileFilter.forProfile(PingProfile.STABLE_BOSSING, US, AccountType.MEMBERS, 1950);
		assertTrue(f.isRequireMembers());
		assertFalse(f.isIncludePvp());
		assertFalse(f.isIncludeLeagues());
		assertNull(f.getRequiredType());
		assertEquals(US, f.getRegion());
		assertEquals(1950, f.getMaxPlayers());
	}

	@Test
	public void freeToPlayForcesFreeWorldsEvenForAMembersAccount()
	{
		WorldFilter f = ProfileFilter.forProfile(PingProfile.FREE_TO_PLAY, US, AccountType.MEMBERS, 1950);
		assertFalse(f.isRequireMembers());
		assertFalse(f.isIncludePvp());
		assertFalse(f.isIncludeLeagues());
	}

	@Test
	public void pvpProfileIncludesPvpWorlds()
	{
		WorldFilter f = ProfileFilter.forProfile(PingProfile.PVP, US, AccountType.MEMBERS, 1950);
		assertTrue(f.isIncludePvp());
		assertFalse(f.isIncludeLeagues());
		assertNull(f.getRequiredType());
	}

	@Test
	public void leaguesProfileRequiresSeasonalType()
	{
		WorldFilter f = ProfileFilter.forProfile(PingProfile.LEAGUES, US, AccountType.MEMBERS, 1950);
		assertTrue(f.isIncludeLeagues());
		assertEquals(WorldType.SEASONAL, f.getRequiredType());
	}

	@Test
	public void leaguesProfileSelectsOnlySeasonalWorlds()
	{
		WorldFilter f = ProfileFilter.forProfile(PingProfile.LEAGUES, US, AccountType.MEMBERS, 1950);
		List<WorldInfo> all = Arrays.asList(
			new WorldInfo(301, 100, US, EnumSet.of(WorldType.MEMBERS)),                       // normal
			new WorldInfo(302, 200, US, EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL)));   // leagues
		assertEquals(Collections.singletonList(302), WorldSelector.select(all, f, 0, 5));
	}
}
