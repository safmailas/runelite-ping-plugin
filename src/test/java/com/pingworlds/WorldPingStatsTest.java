package com.pingworlds;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The contract for WorldPingStats — the average/jitter math and the green-check rule. Each test
 * pins down one rule of "correct" so the AI-written arithmetic can't quietly drift.
 */
public class WorldPingStatsTest
{
	private static WorldPingStats stats(int window, int... pings)
	{
		WorldPingStats s = new WorldPingStats(window);
		for (int p : pings)
		{
			s.record(p);
		}
		return s;
	}

	@Test
	public void averageIsTheMeanOfSuccesses()
	{
		assertEquals(50, stats(10, 40, 50, 60).average());
	}

	@Test
	public void jitterIsPopulationStdDev()
	{
		// 40,50,60 -> mean 50, deviations -10/0/+10 -> sqrt(200/3) ~= 8.16 -> 8
		assertEquals(8, stats(10, 40, 50, 60).jitter());
	}

	@Test
	public void steadyWorldHasZeroJitter()
	{
		WorldPingStats s = stats(10, 50, 50, 50);
		assertEquals(50, s.average());
		assertEquals(0, s.jitter());
	}

	@Test
	public void rollingWindowKeepsOnlyMostRecent()
	{
		// window 3, feed 4 values -> oldest (10) drops, keeps 20,30,40 -> avg 30
		assertEquals(30, stats(3, 10, 20, 30, 40).average());
	}

	@Test
	public void timeoutsAreExcludedFromLatencyButCounted()
	{
		WorldPingStats s = stats(10, 40, -1, 60);
		assertEquals(50, s.average());   // -1 ignored in the mean
		assertEquals(10, s.jitter());    // stddev of {40,60}
		assertEquals(2, s.sampleCount());
		assertEquals(1, s.timeouts());
	}

	@Test
	public void noDataIsSafeDefaults()
	{
		WorldPingStats s = new WorldPingStats(10);
		assertFalse(s.hasData());
		assertEquals(0, s.average());
		assertEquals(0, s.jitter());
	}

	@Test
	public void consistentWhenFastAndSteady()
	{
		// avg 50, jitter 8 -> both under 80/15 -> green
		assertTrue(stats(10, 40, 50, 60).isConsistent(80, 15));
	}

	@Test
	public void notConsistentWhenAverageTooHigh()
	{
		// avg ~205 -> fails the average threshold even though it is steady
		assertFalse(stats(10, 200, 210).isConsistent(80, 15));
	}

	@Test
	public void notConsistentWhenJitterTooHigh()
	{
		// 55,95 -> avg 75 (under 80) but jitter 20 (over 15) -> red
		assertFalse(stats(10, 55, 95).isConsistent(80, 15));
	}

	@Test
	public void onlyTimeoutsIsNotConsistent()
	{
		WorldPingStats s = stats(10, -1, -1);
		assertFalse(s.isConsistent(80, 15));
		assertFalse(s.hasData());
		assertEquals(2, s.timeouts());
	}
}
