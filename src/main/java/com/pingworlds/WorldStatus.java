package com.pingworlds;

/**
 * An immutable display row for one world in the panel. A plain data carrier so the panel only draws
 * and never computes — the plugin builds these on the background thread, the panel renders them on
 * the Swing thread.
 */
public final class WorldStatus
{
	private final int worldId;
	private final boolean hasData;
	private final int average;
	private final int jitter;
	private final int samples;
	private final boolean consistent; // meets the green-check thresholds

	public WorldStatus(int worldId, boolean hasData, int average, int jitter, int samples, boolean consistent)
	{
		this.worldId = worldId;
		this.hasData = hasData;
		this.average = average;
		this.jitter = jitter;
		this.samples = samples;
		this.consistent = consistent;
	}

	public int getWorldId()
	{
		return worldId;
	}

	public boolean hasData()
	{
		return hasData;
	}

	public int getAverage()
	{
		return average;
	}

	public int getJitter()
	{
		return jitter;
	}

	public int getSamples()
	{
		return samples;
	}

	public boolean isConsistent()
	{
		return consistent;
	}
}
