package com.pingworlds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the user's free-text "custom worlds" field (e.g. "301, 302, 302, abc, 330") into a clean,
 * de-duplicated list of world numbers. Free text is where bad input sneaks in, so this is a pure,
 * unit-tested function — the "hard 20%" we don't want to hand-wave.
 */
public final class WorldListParser
{
	// OSRS world numbers sit in a modest band; reject clearly-bogus values (0, negatives, huge ints).
	// Kept generous on purpose so a new world block never gets silently dropped.
	private static final int MIN_WORLD = 300;
	private static final int MAX_WORLD = 1000;

	private WorldListParser()
	{
	}

	public static List<Integer> parse(String raw)
	{
		// LinkedHashSet de-dupes while preserving the order the user typed.
		Set<Integer> worlds = new LinkedHashSet<>();
		if (raw == null)
		{
			return new ArrayList<>();
		}

		for (String token : raw.split("[,\\s]+")) // split on any mix of commas and whitespace
		{
			if (token.isEmpty())
			{
				continue;
			}
			try
			{
				int world = Integer.parseInt(token);
				if (world >= MIN_WORLD && world <= MAX_WORLD)
				{
					worlds.add(world);
				}
				// out-of-range numbers are dropped rather than trusted
			}
			catch (NumberFormatException ignored)
			{
				// junk like "abc" — skip it instead of blowing up
			}
		}

		return new ArrayList<>(worlds);
	}
}
