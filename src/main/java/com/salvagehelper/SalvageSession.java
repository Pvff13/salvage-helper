package com.salvagehelper;

import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InventoryID;

/**
 * Per-tick salvaging state shared by the alerts and overlays: whether you're on your own boat, what
 * is in your inventory, what you are doing, and the salvage counter session.
 *
 * <h3>How salvage gains are counted</h3>
 * Two sources, so nothing is counted twice:
 * <ul>
 *   <li>your own hooking: a rise in inventory salvage while the cargo hold is closed (with it open,
 *   a rise is a withdrawal, not new salvage);</li>
 *   <li>your crew: each crewmate overhead line about hooking salvage, passed in by the plugin.</li>
 * </ul>
 */
@Singleton
class SalvageSession
{
	enum Activity
	{
		NONE,
		/** At a hook rolling for salvage. */
		HOOKING,
		/** At a hook with nothing to salvage (the wreck sank or none in reach). */
		AT_HOOK_IDLE,
		SORTING
	}

	private static final int INVENTORY_SIZE = 28;
	/** Salvaging animations loop with short gaps; keep the last activity this many ticks. */
	private static final int ACTIVITY_HOLD_TICKS = 3;

	private final Client client;
	private final SalvageHelperConfig config;
	private final BoatObjects boatObjects;

	@Getter
	private boolean onBoat;
	@Getter
	private int inventorySalvage;
	@Getter
	private int inventoryUsed;
	@Getter
	private boolean inventoryFull;
	@Getter
	private Activity activity = Activity.NONE;

	@Getter
	private int salvageGained;
	@Getter
	private int xpGained;
	@Getter
	private Instant start;
	private Instant lastGain;
	/** When the player logged out with a session running, or null. */
	private Instant pausedAt;

	private int previousSalvage = -1;
	private int lastSailingXp = -1;
	private int activitySeenTick;

	@Inject
	SalvageSession(Client client, SalvageHelperConfig config, BoatObjects boatObjects)
	{
		this.client = client;
		this.config = config;
		this.boatObjects = boatObjects;
	}

	/**
	 * Refreshes boat/inventory/activity state and the counter.
	 *
	 * @param crewSalvage crew salvage lines seen this tick
	 * @param holdOpen whether the cargo hold interface is open
	 * @return salvage gained this tick, yours and your crew's
	 */
	int tick(int crewSalvage, boolean holdOpen)
	{
		boolean wasOnBoat = onBoat;
		onBoat = boatObjects.isOnOwnBoat();
		scanInventory(client.getItemContainer(InventoryID.INV));
		updateActivity();

		int gained = 0;
		if (onBoat)
		{
			gained += crewSalvage;
			if (wasOnBoat && !holdOpen && previousSalvage >= 0)
			{
				gained += Math.max(0, inventorySalvage - previousSalvage);
			}
		}
		previousSalvage = inventorySalvage;

		Instant now = Instant.now();
		if (pausedAt != null)
		{
			// Back in game: move the session's clock past the time spent logged out
			Duration away = Duration.between(pausedAt, now);
			start = start == null ? null : start.plus(away);
			lastGain = lastGain == null ? null : lastGain.plus(away);
			pausedAt = null;
		}
		if (gained > 0)
		{
			if (start == null)
			{
				start = now;
			}
			salvageGained += gained;
			lastGain = now;
		}
		else if (lastGain != null
			&& Duration.between(lastGain, now).toMinutes() >= config.counterResetMinutes())
		{
			resetCounter();
		}

		return gained;
	}

	/** Called on every Sailing StatChanged. XP only counts while a counter session is running. */
	void sailingXpChanged(int xp)
	{
		if (lastSailingXp >= 0 && xp > lastSailingXp && start != null)
		{
			xpGained += xp - lastSailingXp;
		}
		lastSailingXp = xp;
	}

	boolean isCounting()
	{
		return start != null;
	}

	int salvagePerHour()
	{
		return perHour(salvageGained);
	}

	int xpPerHour()
	{
		return perHour(xpGained);
	}

	void resetCounter()
	{
		salvageGained = 0;
		xpGained = 0;
		start = null;
		lastGain = null;
	}

	/** Full reset, e.g. on logout or world hop. */
	void reset()
	{
		resetCounter();
		loggedOut();
		pausedAt = null;
	}

	/**
	 * Logout or world hop: forget what was in view, but keep the counter. Its clock is paused so the
	 * time spent logged out does not drag the per-hour rates down; {@link #tick} resumes it.
	 */
	void loggedOut()
	{
		if (start != null && pausedAt == null)
		{
			pausedAt = Instant.now();
		}
		onBoat = false;
		inventorySalvage = 0;
		inventoryUsed = 0;
		inventoryFull = false;
		activity = Activity.NONE;
		previousSalvage = -1;
		lastSailingXp = -1;
	}

	private void updateActivity()
	{
		Player local = client.getLocalPlayer();
		int animation = local == null ? -1 : local.getAnimation();
		Activity seen = Activity.NONE;
		if (SalvageIds.HOOK_WORKING_ANIMATIONS.contains(animation))
		{
			seen = Activity.HOOKING;
		}
		else if (SalvageIds.HOOK_IDLE_ANIMATIONS.contains(animation))
		{
			seen = Activity.AT_HOOK_IDLE;
		}
		else if (SalvageIds.SORTING_ANIMATIONS.contains(animation))
		{
			seen = Activity.SORTING;
		}

		int tick = client.getTickCount();
		if (seen != Activity.NONE)
		{
			activity = seen;
			activitySeenTick = tick;
		}
		else if (activity == Activity.AT_HOOK_IDLE && local != null && local.getPoseAnimation() == local.getIdlePoseAnimation())
		{
			// After a reset the bigger hooks play no animation at all, so stay "at the hook" until the
			// player starts moving
			activitySeenTick = tick;
		}
		else if (tick - activitySeenTick > ACTIVITY_HOLD_TICKS)
		{
			activity = Activity.NONE;
		}
	}

	private int perHour(int amount)
	{
		if (start == null)
		{
			return 0;
		}
		long seconds = Math.max(1, Duration.between(start, Instant.now()).getSeconds());
		return (int) (amount * 3600L / seconds);
	}

	private void scanInventory(ItemContainer inventory)
	{
		inventorySalvage = 0;
		inventoryUsed = 0;
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() <= 0)
				{
					continue;
				}
				inventoryUsed++;
				if (SalvageIds.SALVAGE_ITEM_IDS.contains(item.getId()))
				{
					inventorySalvage += item.getQuantity();
				}
			}
		}
		inventoryFull = inventoryUsed >= INVENTORY_SIZE;
	}
}
