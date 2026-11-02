package com.salvagehelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;

/**
 * Keeps count of how many slots each boat's cargo hold is using.
 *
 * <h3>Exact counts and estimates</h3>
 * The game only sends a hold's contents (inventories 963-967, one per boat slot) while its
 * interface is open. That read is exact: every occupied slot counts once, since the hold's capacity
 * is measured in slots. Between reads the count is kept up by what can be seen:
 * <ul>
 *   <li>a crewmate's overhead line about hooking salvage or putting it in the hold: +1 slot;</li>
 *   <li>a crewmate saying the hold is full: the count jumps to the capacity;</li>
 *   <li>the "You deposit some cargo into the cargo hold" message: + the inventory slots that just
 *   emptied.</li>
 * </ul>
 * Anything else (a bank boat, a crewmate banking the hold at a dock) is missed until the hold is
 * opened again, which resyncs it. The count is marked as an estimate until then.
 *
 * <p>Each boat's count is saved to the RuneLite profile of the logged-in account, so it carries over
 * plugin reloads and client restarts as an estimate instead of starting from unknown.
 */
@Singleton
class CargoHoldTracker
{
	/** Fills older than this are ignored when working out the rate. */
	private static final long RATE_WINDOW_MILLIS = 10 * 60_000L;
	private static final long MIN_RATE_SPAN_MILLIS = 60_000L;
	private static final int MIN_RATE_EVENTS = 3;

	@Value
	static class Contents
	{
		int used;
		/** Slots in the hold, or -1 if unknown. */
		int capacity;
		int salvage;
		boolean estimate;

		boolean isFull()
		{
			return capacity > 0 && used >= capacity;
		}
	}

	private static final class Hold
	{
		int used;
		int salvage;
		boolean estimate;
	}

	private static final String SAVED_KEY = "holdCount_";

	private final Client client;
	private final BoatObjects boatObjects;
	private final ConfigManager configManager;

	private final Map<Integer, Hold> holds = new HashMap<>();
	/** (time, slots) pairs for recent fills, oldest first. */
	private final Deque<long[]> fills = new ArrayDeque<>();

	@Inject
	CargoHoldTracker(Client client, BoatObjects boatObjects, ConfigManager configManager)
	{
		this.client = client;
		this.boatObjects = boatObjects;
		this.configManager = configManager;
	}

	/** Exact read from the hold's inventory. @return true if the container was a cargo hold. */
	boolean containerChanged(int containerId, ItemContainer container)
	{
		int slot = slotFor(containerId);
		if (slot == 0)
		{
			return false;
		}

		Hold hold = holds.computeIfAbsent(slot, s -> new Hold());
		hold.used = 0;
		hold.salvage = 0;
		hold.estimate = false;
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > -1 && item.getQuantity() > 0)
				{
					hold.used++;
					if (SalvageIds.SALVAGE_ITEM_IDS.contains(item.getId()))
					{
						hold.salvage++;
					}
				}
			}
		}
		save(slot, hold);
		return true;
	}

	/** A crewmate said they put a piece of salvage in the hold. */
	void crewSalvaged()
	{
		add(1, 1);
	}

	/** The player deposited while the hold was closed; these inventory slots just emptied. */
	void playerDeposited(int slots, int salvage)
	{
		if (slots > 0)
		{
			add(slots, Math.max(0, salvage));
		}
	}

	/** A crewmate said the hold is full. */
	void holdReportedFull()
	{
		Hold hold = currentHold();
		int capacity = boatObjects.ownHoldCapacity();
		if (hold != null && capacity > 0)
		{
			hold.used = capacity;
			hold.estimate = true;
			save(currentSlot(), hold);
		}
	}

	/** The hold on the boat the player last boarded, or null if it has not been read yet. */
	Contents current()
	{
		Hold hold = currentHold();
		if (hold == null)
		{
			return null;
		}
		return new Contents(hold.used, boatObjects.ownHoldCapacity(), hold.salvage, hold.estimate);
	}

	/**
	 * Milliseconds until the hold is full at the recent fill rate, or -1 when there is not enough
	 * data (a few fills over at least a minute in the last ten).
	 */
	long millisToFull()
	{
		Contents contents = current();
		if (contents == null || contents.getCapacity() <= 0)
		{
			return -1;
		}
		if (contents.isFull())
		{
			return 0;
		}

		long now = System.currentTimeMillis();
		prune(now);
		if (fills.size() < MIN_RATE_EVENTS)
		{
			return -1;
		}
		long span = now - fills.peekFirst()[0];
		if (span < MIN_RATE_SPAN_MILLIS)
		{
			return -1;
		}
		long slots = 0;
		for (long[] fill : fills)
		{
			slots += fill[1];
		}
		double slotsPerMilli = (double) slots / span;
		return (long) ((contents.getCapacity() - contents.getUsed()) / slotsPerMilli);
	}

	void reset()
	{
		holds.clear();
		fills.clear();
	}

	private void add(int slots, int salvage)
	{
		Hold hold = currentHold();
		if (hold == null)
		{
			// Nothing to add to until the hold has been read once
			return;
		}
		int capacity = boatObjects.ownHoldCapacity();
		hold.used = capacity > 0 ? Math.min(capacity, hold.used + slots) : hold.used + slots;
		hold.salvage += salvage;
		hold.estimate = true;
		save(currentSlot(), hold);

		long now = System.currentTimeMillis();
		fills.addLast(new long[]{now, slots});
		prune(now);
	}

	private void prune(long now)
	{
		while (!fills.isEmpty() && now - fills.peekFirst()[0] > RATE_WINDOW_MILLIS)
		{
			fills.removeFirst();
		}
	}

	private int currentSlot()
	{
		int slot = client.getVarbitValue(SalvageIds.BOAT_SLOT_VARBIT);
		return slot >= 1 && slot <= SalvageIds.CARGO_HOLD_INVENTORIES.length ? slot : 0;
	}

	/** The current boat's hold: from this session, else the saved count as an estimate, else null. */
	private Hold currentHold()
	{
		int slot = currentSlot();
		if (slot == 0)
		{
			return null;
		}
		Hold hold = holds.get(slot);
		if (hold == null)
		{
			hold = load(slot);
			if (hold != null)
			{
				holds.put(slot, hold);
			}
		}
		return hold;
	}

	private void save(int slot, Hold hold)
	{
		if (slot != 0)
		{
			configManager.setRSProfileConfiguration(SalvageHelperConfig.GROUP, SAVED_KEY + slot,
				hold.used + "," + hold.salvage);
		}
	}

	private Hold load(int slot)
	{
		String saved = configManager.getRSProfileConfiguration(SalvageHelperConfig.GROUP, SAVED_KEY + slot);
		if (saved == null)
		{
			return null;
		}
		String[] parts = saved.split(",");
		try
		{
			Hold hold = new Hold();
			hold.used = Integer.parseInt(parts[0].trim());
			hold.salvage = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
			// Anything could have happened since it was saved; opening the hold makes it exact again
			hold.estimate = true;
			return hold;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Boat slot 1-5 for a cargo hold inventory id, else 0. */
	private static int slotFor(int containerId)
	{
		int id = containerId & SalvageIds.CONTAINER_ID_MASK;
		if (id < InventoryID.SAILING_BOAT_1_CARGOHOLD || id > InventoryID.SAILING_BOAT_5_CARGOHOLD)
		{
			return 0;
		}
		return id - InventoryID.SAILING_BOAT_1_CARGOHOLD + 1;
	}
}
