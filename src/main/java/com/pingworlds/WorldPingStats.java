package com.pingworlds;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rolling ping statistics for a single world. Keeps the most recent N successful pings and computes
 * average latency and jitter (population standard deviation = how much the pings bounce around).
 * Timeouts (Ping.ping returns -1) are counted separately, never mixed into the latency math.
 *
 * Thread-safety: pings are recorded on the background executor thread and read later from the Swing
 * UI thread (M6), so every method is synchronized.
 */
public class WorldPingStats
{
	private final int windowSize;
	private final Deque<Integer> samples = new ArrayDeque<>(); // successful pings only, oldest first
	private int timeouts;

	public WorldPingStats(int windowSize)
	{
		this.windowSize = Math.max(1, windowSize);
	}

	/** Record one ping result. A negative value means the ping timed out / failed. */
	public synchronized void record(int pingMs)
	{
		if (pingMs < 0)
		{
			timeouts++;
			return; // don't pollute latency stats with a failed ping
		}
		samples.addLast(pingMs);
		while (samples.size() > windowSize)
		{
			samples.removeFirst(); // keep only the most recent windowSize successes
		}
	}

	public synchronized int sampleCount()
	{
		return samples.size();
	}

	public synchronized boolean hasData()
	{
		return !samples.isEmpty();
	}

	public synchronized int timeouts()
	{
		return timeouts;
	}

	/** Mean of the recent successful pings, rounded to the nearest ms. 0 if there is no data. */
	public synchronized int average()
	{
		if (samples.isEmpty())
		{
			return 0;
		}
		long sum = 0;
		for (int s : samples)
		{
			sum += s;
		}
		return Math.round((float) sum / samples.size());
	}

	/**
	 * Jitter = population standard deviation of the recent pings, rounded to ms. Needs at least two
	 * samples to mean anything, so returns 0 below that.
	 */
	public synchronized int jitter()
	{
		int n = samples.size();
		if (n < 2)
		{
			return 0;
		}
		double mean = 0;
		for (int s : samples)
		{
			mean += s;
		}
		mean /= n;

		double sumSquares = 0;
		for (int s : samples)
		{
			double diff = s - mean;
			sumSquares += diff * diff;
		}
		return (int) Math.round(Math.sqrt(sumSquares / n));
	}

	/**
	 * The green-check rule: a world is "consistent" (good to log into) when it has data and both its
	 * average latency and its jitter are at or under the user's thresholds. This is the single source
	 * of truth for green vs red — the panel (M6) will call exactly this.
	 */
	public synchronized boolean isConsistent(int avgThresholdMs, int jitterThresholdMs)
	{
		return hasData()
			&& average() <= avgThresholdMs
			&& jitter() <= jitterThresholdMs;
	}
}
