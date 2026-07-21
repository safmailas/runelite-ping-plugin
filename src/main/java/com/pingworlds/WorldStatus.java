package com.pingworlds;

/**
 * An immutable display row for one world in the panel. A plain data carrier so the panel only draws
 * and never computes — the plugin builds these on the background thread, the panel renders them on
 * the Swing thread.
 */
public final class WorldStatus
{
	private final int worldId;
	private final String regionLabel; // short code like "US", "UK", "" if unknown
	private final int players;        // -1 if unknown
	private final String activity;    // world activity text (may be empty)
	private final boolean hasData;
	private final int average;
	private final int jitter;
	private final int samples;
	private final boolean consistent; // meets the green thresholds

	public WorldStatus(int worldId, String regionLabel, int players, String activity,
		boolean hasData, int average, int jitter, int samples, boolean consistent)
	{
		this.worldId = worldId;
		this.regionLabel = regionLabel == null ? "" : regionLabel;
		this.players = players;
		this.activity = activity == null ? "" : activity;
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

	public String getRegionLabel()
	{
		return regionLabel;
	}

	public int getPlayers()
	{
		return players;
	}

	public String getActivity()
	{
		return activity;
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
