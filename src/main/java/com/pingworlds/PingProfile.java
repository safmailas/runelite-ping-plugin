package com.pingworlds;

/**
 * A play-style profile: a named bundle of world filters for a common goal. The filtering logic
 * that each profile maps to arrives in M5 — here it is just the choice the user makes in config.
 */
public enum PingProfile
{
	STABLE_BOSSING("Stable bossing / Inferno"),
	LEAGUES("Leagues"),
	FREE_TO_PLAY("Free-to-play"),
	PVP("PvP"),
	CUSTOM("Custom (my world list)");

	private final String displayName;

	PingProfile(String displayName)
	{
		this.displayName = displayName;
	}

	// RuneLite renders enums as a dropdown using toString(), so this controls the label the user sees.
	@Override
	public String toString()
	{
		return displayName;
	}
}
