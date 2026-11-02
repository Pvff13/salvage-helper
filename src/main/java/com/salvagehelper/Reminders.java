package com.salvagehelper;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;

/**
 * Reminders that need you to act, each with a notification and a banner line:
 * <ul>
 *   <li><b>Hook empty</b>: a hook has nobody on it while a crewmate sits idle and a wreck is up in
 *   reach. Crew jobs come from the crew slot varbits ({@link SalvageIds#CREW_POSITION_VARBITS}); you
 *   count as on a hook from your animation or from standing next to one.</li>
 *   <li><b>Your hook is idle</b>: you are standing at a hook with nothing to salvage while a wreck is
 *   up in reach. Crew start on a new wreck by themselves; you have to click the hook again.</li>
 *   <li><b>Crew stopped</b>: crew are assigned to hooks and a wreck is up in reach, but none of them has
 *   said they hooked anything for a while. The game shows nothing for this.</li>
 *   <li><b>Boost / level</b>: a wreck in reach needs a higher Sailing level than your real one and
 *   only a boost is covering it: warns when one more boost drop would stop salvaging, and when it has.</li>
 *   <li><b>Idle logout</b>: shortly before the game logs you out for idling, from the client's own
 *   idle counters ({@link Client#getIdleTimeout()}, mouse and keyboard idle ticks).</li>
 *   <li><b>Danger</b>: while near wrecks on your own boat, the boat losing hull points, you taking a
 *   hit, or you getting poisoned.</li>
 * </ul>
 */
@Slf4j
@Singleton
class Reminders
{
	/** Client ticks (for the idle counters) are 20 ms. */
	private static final int CLIENT_TICK_MILLIS = 20;
	private static final long DANGER_COOLDOWN_MILLIS = 30_000L;
	private static final long DANGER_SHOW_MILLIS = 10_000L;
	/** Standing this close to a hook counts as manning it, between hook animations. */
	private static final int PLAYER_AT_HOOK_TILES = 1;
	/** "Near wrecks" for the danger alert. */
	private static final int SALVAGING_SPOT_TILES = 30;

	private final Client client;
	private final SalvageHelperConfig config;
	private final Notifier notifier;
	private final Banner banner;
	private final BoatObjects boatObjects;
	private final WreckTracker wreckTracker;
	private final SalvageSession session;
	private final CargoHoldTracker cargoHoldTracker;

	private long hookEmptySince = -1;
	private long hookEmptyLastNotified = -1;
	private long hookIdleSince = -1;
	private boolean hookIdleNotified;
	private long crewStoppedSince = -1;
	private boolean crewStoppedNotified;
	private long lastCrewLine = -1;
	private boolean boostWarned;
	private boolean levelWarned;
	private boolean idleWarned;
	private int lastBoatHp = -1;
	private boolean wasPoisoned;
	private long lastDangerAt = -1;
	private long lastDangerNotified = -1;
	private String dangerReason;

	@Inject
	Reminders(Client client, SalvageHelperConfig config, Notifier notifier, Banner banner, BoatObjects boatObjects,
		WreckTracker wreckTracker, SalvageSession session, CargoHoldTracker cargoHoldTracker)
	{
		this.client = client;
		this.config = config;
		this.notifier = notifier;
		this.banner = banner;
		this.boatObjects = boatObjects;
		this.wreckTracker = wreckTracker;
		this.session = session;
		this.cargoHoldTracker = cargoHoldTracker;
	}

	/** A crewmate said they hooked salvage. */
	void crewLine()
	{
		lastCrewLine = System.currentTimeMillis();
	}

	/** The local player took a hit. */
	void playerHit()
	{
		danger("You are being hit");
	}

	void tick()
	{
		long now = System.currentTimeMillis();
		boolean onBoat = session.isOnBoat();
		List<WorldPoint> hooks = boatObjects.ownHookWorldPoints();
		List<WreckTracker.Site> inReach = onBoat ? wreckTracker.activeInReach(hooks) : Collections.emptyList();
		CargoHoldTracker.Contents hold = cargoHoldTracker.current();
		boolean holdFull = hold != null && hold.isFull();

		int crewPresent = 0;
		int crewIdle = 0;
		int crewOnHooks = 0;
		for (int i = 0; i < SalvageIds.CREW_SLOT_VARBITS.length; i++)
		{
			if (client.getVarbitValue(SalvageIds.CREW_SLOT_VARBITS[i]) == 0)
			{
				continue;
			}
			crewPresent++;
			int position = client.getVarbitValue(SalvageIds.CREW_POSITION_VARBITS[i]);
			if (position == SalvageIds.CREW_POSITION_IDLE)
			{
				crewIdle++;
			}
			else if (SalvageIds.CREW_POSITION_HOOKS.contains(position))
			{
				crewOnHooks++;
			}
		}

		SalvageSession.Activity activity = session.getActivity();
		// Animation alone has gaps (hook cycles with no animation, or ones not in the known set), so
		// standing next to a hook also counts as manning it
		int nearestHook = tilesToNearestHook();
		boolean playerAtHook = activity == SalvageSession.Activity.HOOKING
			|| activity == SalvageSession.Activity.AT_HOOK_IDLE
			|| (nearestHook >= 0 && nearestHook <= PLAYER_AT_HOOK_TILES);

		boolean empty = onBoat && !inReach.isEmpty() && !holdFull && crewIdle > 0
			&& hooks.size() > crewOnHooks + (playerAtHook ? 1 : 0);
		if (empty && hookEmptySince < 0)
		{
			Player local = client.getLocalPlayer();
			log.debug("Hook empty check: hooks {}, crew on hooks {}, crew idle {}, you: activity {}, animation {}, pose {}, {} tiles from the nearest hook",
				hooks.size(), crewOnHooks, crewIdle, activity, local == null ? -1 : local.getAnimation(),
				local == null ? -1 : local.getPoseAnimation(), nearestHook);
		}
		hookEmpty(now, empty);
		hookIdle(now, onBoat && !inReach.isEmpty() && !session.isInventoryFull()
			&& activity == SalvageSession.Activity.AT_HOOK_IDLE);
		crewStopped(now, onBoat && !inReach.isEmpty() && !holdFull && crewOnHooks > 0);
		boost(onBoat, inReach);
		idleLogout(onBoat);
		dangerTick(now, onBoat);
	}

	void reset()
	{
		hookEmptySince = -1;
		hookEmptyLastNotified = -1;
		hookIdleSince = -1;
		hookIdleNotified = false;
		crewStoppedSince = -1;
		crewStoppedNotified = false;
		lastCrewLine = -1;
		boostWarned = false;
		levelWarned = false;
		idleWarned = false;
		lastBoatHp = -1;
		wasPoisoned = false;
		lastDangerAt = -1;
		dangerReason = null;
	}

	/** Whole tiles from the player to the closest hook on their own boat, or -1 if unknown. */
	private int tilesToNearestHook()
	{
		Player local = client.getLocalPlayer();
		LocalPoint here = local == null ? null : local.getLocalLocation();
		if (here == null)
		{
			return -1;
		}
		int best = -1;
		for (GameObject hook : boatObjects.ownHooks())
		{
			LocalPoint at = hook.getLocalLocation();
			if (at == null)
			{
				continue;
			}
			int tiles = Math.max(Math.abs(at.getX() - here.getX()), Math.abs(at.getY() - here.getY()))
				/ Perspective.LOCAL_TILE_SIZE;
			if (best < 0 || tiles < best)
			{
				best = tiles;
			}
		}
		return best;
	}

	private void hookEmpty(long now, boolean condition)
	{
		if (!condition)
		{
			hookEmptySince = -1;
			hookEmptyLastNotified = -1;
			banner.clear("hookEmpty");
			return;
		}
		if (hookEmptySince < 0)
		{
			hookEmptySince = now;
		}
		if (now - hookEmptySince < config.reminderGraceSeconds() * 1000L)
		{
			return;
		}

		banner.set("hookEmpty", Banner.Level.REMINDER, "HOOK EMPTY - assign a crewmate");
		long repeat = config.reminderRepeatSeconds() * 1000L;
		if (hookEmptyLastNotified < 0 || (repeat > 0 && now - hookEmptyLastNotified >= repeat))
		{
			hookEmptyLastNotified = now;
			notifier.notify(config.hookEmptyNotification(), "A salvaging hook is empty. Assign a free crewmate to it.");
		}
	}

	private void hookIdle(long now, boolean condition)
	{
		if (!condition)
		{
			hookIdleSince = -1;
			hookIdleNotified = false;
			banner.clear("hookIdle");
			return;
		}
		if (hookIdleSince < 0)
		{
			hookIdleSince = now;
		}
		if (now - hookIdleSince < config.reminderGraceSeconds() * 1000L)
		{
			return;
		}
		banner.set("hookIdle", Banner.Level.REMINDER, "YOUR HOOK IS IDLE - click it");
		if (!hookIdleNotified)
		{
			hookIdleNotified = true;
			notifier.notify(config.hookIdleNotification(), "A wreck is up but your hook is idle. Click it to salvage.");
		}
	}

	private void crewStopped(long now, boolean condition)
	{
		if (!condition)
		{
			crewStoppedSince = -1;
			crewStoppedNotified = false;
			banner.clear("crewStopped");
			return;
		}
		if (crewStoppedSince < 0)
		{
			crewStoppedSince = now;
		}
		long quietSince = Math.max(crewStoppedSince, lastCrewLine);
		if (now - quietSince < config.crewStoppedSeconds() * 1000L)
		{
			crewStoppedNotified = false;
			banner.clear("crewStopped");
			return;
		}
		banner.set("crewStopped", Banner.Level.REMINDER, "CREW STOPPED - reassign or nudge the boat");
		if (!crewStoppedNotified)
		{
			crewStoppedNotified = true;
			notifier.notify(config.crewStoppedNotification(),
				"Your crew have stopped bringing in salvage although a wreck is in reach.");
		}
	}

	private void boost(boolean onBoat, List<WreckTracker.Site> inReach)
	{
		int required = 0;
		for (WreckTracker.Site site : inReach)
		{
			required = Math.max(required, site.getType().getSailingLevel());
		}
		int real = client.getRealSkillLevel(Skill.SAILING);
		int boosted = client.getBoostedSkillLevel(Skill.SAILING);
		SalvageHelperConfig.BoostWarning mode = config.boostWarning();

		boolean aboutToDrop = onBoat && required > real && boosted == required;
		boolean tooLow = onBoat && required > 0 && boosted < required;

		boolean warnBefore = aboutToDrop && (mode == SalvageHelperConfig.BoostWarning.BEFORE || mode == SalvageHelperConfig.BoostWarning.BOTH);
		banner.toggle("boost", warnBefore, Banner.Level.REMINDER, "BOOST WEARING OFF - re-boost Sailing");
		if (warnBefore && !boostWarned)
		{
			notifier.notify(config.boostNotification(),
				"Your Sailing boost is at " + boosted + ", exactly what this wreck needs. Its next drop stops salvaging.");
		}
		boostWarned = aboutToDrop;

		boolean warnAfter = tooLow && (mode == SalvageHelperConfig.BoostWarning.AFTER || mode == SalvageHelperConfig.BoostWarning.BOTH);
		banner.toggle("level", warnAfter, Banner.Level.REMINDER, "SAILING LEVEL TOO LOW (" + boosted + "/" + required + ")");
		if (warnAfter && !levelWarned)
		{
			notifier.notify(config.boostNotification(),
				"Your Sailing level (" + boosted + ") is below the " + required + " this wreck needs. Salvaging has stopped.");
		}
		levelWarned = tooLow;
	}

	private void idleLogout(boolean onBoat)
	{
		int timeout = client.getIdleTimeout();
		long warnMillis = config.idleLogoutWarnSeconds() * 1000L;
		if (!onBoat || timeout <= 0 || warnMillis <= 0)
		{
			idleWarned = false;
			banner.clear("idle");
			return;
		}
		int idle = Math.min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks());
		long remaining = Math.max(0, (long) (timeout - idle) * CLIENT_TICK_MILLIS);
		boolean soon = remaining <= warnMillis;
		banner.toggle("idle", soon, Banner.Level.REMINDER, "IDLE LOGOUT IN " + Durations.countdown(remaining) + " - move the mouse");
		if (soon && !idleWarned)
		{
			notifier.notify(config.idleLogoutNotification(),
				"You will be logged out for idling in about " + Durations.countdown(remaining) + ". Move the mouse or press a key.");
		}
		idleWarned = soon;
	}

	private void dangerTick(long now, boolean onBoat)
	{
		boolean atSpot = onBoat && wreckTracker.anySiteNear(boatObjects.ownBoatWorldPoint(), SALVAGING_SPOT_TILES);

		int hp = onBoat ? client.getVarbitValue(SalvageIds.BOAT_HP_VARBIT) : -1;
		if (atSpot && lastBoatHp >= 0 && hp >= 0 && hp < lastBoatHp)
		{
			danger("Your boat is taking damage");
		}
		lastBoatHp = hp;

		boolean poisoned = client.getVarpValue(SalvageIds.POISON_VARP) > 0;
		if (atSpot && poisoned && !wasPoisoned)
		{
			danger("You have been poisoned");
		}
		wasPoisoned = poisoned;

		boolean show = config.dangerBanner() && lastDangerAt >= 0
			&& (now - lastDangerAt < DANGER_SHOW_MILLIS || (poisoned && atSpot));
		banner.toggle("danger", show, Banner.Level.DANGER, "DANGER - " + dangerReason);
	}

	private void danger(String reason)
	{
		if (!session.isOnBoat()
			|| !wreckTracker.anySiteNear(boatObjects.ownBoatWorldPoint(), SALVAGING_SPOT_TILES))
		{
			return;
		}
		long now = System.currentTimeMillis();
		lastDangerAt = now;
		dangerReason = reason;
		if (lastDangerNotified < 0 || now - lastDangerNotified >= DANGER_COOLDOWN_MILLIS)
		{
			lastDangerNotified = now;
			notifier.notify(config.dangerNotification(), reason + " while salvaging.");
		}
	}
}
