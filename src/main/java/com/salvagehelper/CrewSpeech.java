package com.salvagehelper;

import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Recognises the game's salvaging lines. The phrases are what crewmates and the chat box say in
 * game, as collected by AFK Shipwrecks (BSD-2); matching is on lower-cased fragments so small
 * wording differences between crewmates still match.
 */
final class CrewSpeech
{
	private static final String HOLD_FULL = "cargo hold is full";
	private static final String[] CREW_SALVAGE = {"hook some salvage", "put it in the cargo hold"};
	private static final String PLAYER_DEPOSIT = "you deposit some cargo into the cargo hold";
	/** An order to a crewmate, e.g. "Ex-Captain Siad, take control of the ... hook!" or "..., take a rest!". */
	private static final Pattern CREW_ORDER = Pattern.compile("^[^,]+, (?:take control of the .+|take a rest)!?$");
	/** The ghost crewmate only ever says variations of "Wooo wooo." */
	private static final Pattern GHOST = Pattern.compile("^(?:w+o+[ ,]*)+[.!]*$");

	private CrewSpeech()
	{
	}

	static boolean isHoldFull(String text)
	{
		return plain(text).contains(HOLD_FULL);
	}

	/** A crewmate saying they hooked a piece of salvage (the ghost's "wooo" counts too). */
	static boolean isCrewSalvage(String text)
	{
		String line = plain(text);
		if (line.contains(HOLD_FULL))
		{
			return false;
		}
		for (String fragment : CREW_SALVAGE)
		{
			if (line.contains(fragment))
			{
				return true;
			}
		}
		return GHOST.matcher(line.trim()).matches();
	}

	static boolean isPlayerDeposit(String text)
	{
		return plain(text).contains(PLAYER_DEPOSIT);
	}

	static boolean isCrewOrder(String text)
	{
		return CREW_ORDER.matcher(Text.removeTags(text).trim()).matches();
	}

	private static String plain(String text)
	{
		return text == null ? "" : Text.removeTags(text).toLowerCase(Locale.ROOT);
	}
}
