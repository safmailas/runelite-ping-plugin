package com.pingworlds;

/**
 * Which server region to consider. AUTO means "match the world RuneLite currently has selected"
 * (read via Client.getWorld() in M4) — the honest stand-in for auto-detecting your location, since
 * we cannot know it before you log in.
 */
public enum RegionOption
{
	AUTO("Auto (match my current world)"),
	US("United States"),
	UK("United Kingdom"),
	AUSTRALIA("Australia"),
	GERMANY("Germany");

	private final String displayName;

	RegionOption(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
