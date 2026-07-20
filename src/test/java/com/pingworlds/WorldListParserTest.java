package com.pingworlds;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The contract for WorldListParser. Each test states one rule of "correct" more precisely than any
 * prose description could — this is what the paper means by "tests as the contract with the AI".
 */
public class WorldListParserTest
{
	@Test
	public void parsesCommaSeparated()
	{
		assertEquals(Arrays.asList(301, 302, 330), WorldListParser.parse("301,302,330"));
	}

	@Test
	public void toleratesWhitespaceAndMixedSeparators()
	{
		assertEquals(Arrays.asList(301, 302, 330), WorldListParser.parse("  301 , 302   330 "));
	}

	@Test
	public void deDupesButKeepsFirstSeenOrder()
	{
		assertEquals(Arrays.asList(330, 301), WorldListParser.parse("330, 301, 330, 301"));
	}

	@Test
	public void skipsJunkTokens()
	{
		assertEquals(Arrays.asList(301, 302), WorldListParser.parse("301, abc, 302, !!"));
	}

	@Test
	public void rejectsOutOfRangeNumbers()
	{
		assertEquals(Collections.emptyList(), WorldListParser.parse("0, -5, 99999999"));
	}

	@Test
	public void handlesNullAndBlank()
	{
		assertEquals(Collections.emptyList(), WorldListParser.parse(null));
		assertEquals(Collections.emptyList(), WorldListParser.parse("   "));
	}
}
