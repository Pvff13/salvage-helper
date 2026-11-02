package com.salvagehelper;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;

/**
 * Works out when salvaging has stopped.
 *
 * <p>Salvaging counts as active on any tick where salvage was gained (into the inventory or the
 * hold), or where you or one of your crewmates plays a working hook animation
 * ({@link SalvageIds#HOOK_WORKING_ANIMATIONS}). Salvage rolls can fail several times in a row, so
 * the animations are what keep the timer from firing during normal play. The monitor only arms once
 * activity has been seen on this boat trip, so sailing around does not trigger it.
 */
@Singleton
class HookMonitor
{
	private static final int TICK_MILLIS = 600;

	private final Client client;
	private final SalvageHelperConfig config;

	private int lastActiveTick;
	private boolean armed;
	@Getter
	private boolean stopped;

	@Inject
	HookMonitor(Client client, SalvageHelperConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** @return true only on the tick salvaging is first detected as stopped */
	boolean tick(boolean onBoat, boolean gainedSalvage)
	{
		if (!onBoat)
		{
			reset();
			return false;
		}

		int tick = client.getTickCount();
		if (gainedSalvage || anyoneWorking())
		{
			lastActiveTick = tick;
			armed = true;
			stopped = false;
			return false;
		}

		if (!armed || stopped)
		{
			return false;
		}

		if ((long) (tick - lastActiveTick) * TICK_MILLIS >= config.hookStoppedSeconds() * 1000L)
		{
			stopped = true;
			return true;
		}
		return false;
	}

	int secondsStopped()
	{
		return (client.getTickCount() - lastActiveTick) * TICK_MILLIS / 1000;
	}

	void reset()
	{
		armed = false;
		stopped = false;
	}

	private boolean anyoneWorking()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return false;
		}
		if (SalvageIds.HOOK_WORKING_ANIMATIONS.contains(local.getAnimation()))
		{
			return true;
		}

		// Only your own crew: they share your boat's world view
		for (NPC npc : local.getWorldView().npcs())
		{
			if (npc != null && CrewTracker.isCrewmate(npc)
				&& SalvageIds.HOOK_WORKING_ANIMATIONS.contains(npc.getAnimation()))
			{
				return true;
			}
		}
		return false;
	}
}
