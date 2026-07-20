package com.pingworlds;

/**
 * The only account distinction that affects which worlds you can log into: members vs free-to-play.
 * (Ironman/HCIM don't restrict world choice, so they aren't modelled here.) We cannot detect this
 * before login, so the user tells us once via config.
 */
public enum AccountType
{
	MEMBERS("Members"),
	FREE_TO_PLAY("Free-to-play");

	private final String displayName;

	AccountType(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
