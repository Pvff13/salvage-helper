package com.salvagehelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

/**
 * Promotes the cargo hold's deposit option to the left-click while the inventory holds something
 * the hold accepts (or, in "Only my list" mode, something on the player's own list).
 *
 * <p>This only reorders the client's own menu entries after it has sorted them (the same thing
 * RuneLite's Menu Entry Swapper does). It never creates, consumes or sends clicks: the player still
 * clicks, the game just receives the entry they would otherwise pick from the right-click menu.
 *
 * <p>The hold's deposit option is not always named the same ("Deposit", "Deposit-all",
 * "Quick-deposit" and "Deposit-held" have all been seen), so the configured names are tried first
 * and then any other deposit option except "Deposit-held", which only moves held task cargo.
 */
@Slf4j
@Singleton
class CargoHoldSwapper
{
	private static final String HELD_CARGO_OPTION = "deposit-held";

	private final Client client;
	private final SalvageHelperConfig config;
	private final ItemManager itemManager;

	/** Lower-cased option names to promote, in priority order. */
	private List<String> depositOptions = new ArrayList<>();
	/** Items that trigger the swap by id: the built-in set plus extras, or the IDs in "My list". */
	private Set<Integer> depositableIds = new HashSet<>();
	/** Lower-cased names (with * wildcards) from "My list"; only used in that mode. */
	private List<String> whitelistNames = new ArrayList<>();
	private boolean hasDepositable;
	/** The last set of hold options written to the log, so each distinct menu is logged once. */
	private String loggedOptions;

	@Inject
	CargoHoldSwapper(Client client, SalvageHelperConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
	}

	/** Re-reads the option names, trigger and extra item IDs from config, then rechecks the inventory. */
	void reloadConfig()
	{
		List<String> options = new ArrayList<>();
		for (String option : Text.fromCSV(config.depositOptions()))
		{
			options.add(option.toLowerCase(Locale.ROOT));
		}
		depositOptions = options;

		Set<Integer> ids = new HashSet<>();
		List<String> names = new ArrayList<>();
		switch (config.depositTrigger())
		{
			case WHITELIST:
				// "My list" alone decides: each entry is an item ID or a name, * matching anything
				for (String entry : Text.fromCSV(config.depositWhitelist()))
				{
					Integer id = parseId(entry);
					if (id != null)
					{
						ids.add(id);
					}
					else if (!entry.isEmpty())
					{
						names.add(entry.toLowerCase(Locale.ROOT));
					}
				}
				break;
			case SALVAGE_ONLY:
				ids.addAll(SalvageIds.SALVAGE_ITEM_IDS);
				addExtraIds(ids);
				break;
			default:
				ids.addAll(SalvageIds.HOLD_ACCEPTED_ITEM_IDS);
				addExtraIds(ids);
				break;
		}
		depositableIds = ids;
		whitelistNames = names;

		updateInventory(client.getItemContainer(InventoryID.INV));
	}

	/**
	 * Caches whether the inventory holds a depositable item, so the per-frame menu hook does not have
	 * to scan the inventory. Called on every inventory change.
	 */
	void updateInventory(ItemContainer inventory)
	{
		hasDepositable = false;
		if (inventory == null)
		{
			return;
		}

		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0 && isDepositable(item.getId()))
			{
				hasDepositable = true;
				return;
			}
		}
	}

	private boolean isDepositable(int itemId)
	{
		if (depositableIds.contains(itemId))
		{
			return true;
		}
		if (whitelistNames.isEmpty())
		{
			return false;
		}
		String name = itemManager.getItemComposition(itemId).getName();
		if (name == null)
		{
			return false;
		}
		name = name.toLowerCase(Locale.ROOT);
		for (String pattern : whitelistNames)
		{
			if (WildcardMatcher.matches(pattern, name))
			{
				return true;
			}
		}
		return false;
	}

	private void addExtraIds(Set<Integer> ids)
	{
		for (String entry : Text.fromCSV(config.extraDepositableIds()))
		{
			Integer id = parseId(entry);
			// Typos in the config box are skipped rather than breaking the whole list
			if (id != null)
			{
				ids.add(id);
			}
		}
	}

	private static Integer parseId(String text)
	{
		try
		{
			return Integer.parseInt(text.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	void reset()
	{
		hasDepositable = false;
		loggedOptions = null;
	}

	/** Called from PostMenuSort, i.e. once the client has finished ordering this frame's menu. */
	void swap()
	{
		// An open menu is not rebuilt each frame, so swapping it here would keep flipping entries
		if (!client.isMenuOpen())
		{
			reorder();
		}
	}

	/**
	 * Called when the right-click menu opens. Other plugins that rewrite the menu as it opens would
	 * otherwise put the original order back, so the swap is applied once more on the opened menu.
	 */
	void menuOpened()
	{
		reorder();
	}

	private void reorder()
	{
		if (!config.leftClickDepositAll() || !hasDepositable)
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		if (entries.length < 2)
		{
			return;
		}

		// The last entry is the left-click. Only touch the menu when the cursor's top target is the
		// cargo hold itself, so something drawn in front of it (a crewmate, the player) keeps its
		// normal left-click.
		MenuEntry top = entries[entries.length - 1];
		if (!isCargoHold(top))
		{
			return;
		}

		int depositIdx = findDepositEntry(entries, top);
		logOptionsOnce(entries, top, depositIdx);
		if (depositIdx < 0 || depositIdx == entries.length - 1)
		{
			return;
		}

		MenuEntry deposit = entries[depositIdx];
		System.arraycopy(entries, depositIdx + 1, entries, depositIdx, entries.length - 1 - depositIdx);
		entries[entries.length - 1] = deposit;
		menu.setMenuEntries(entries);
	}

	/**
	 * Matches by object ID first (from {@link SalvageIds#CARGO_HOLD_OBJECT_IDS}); falls back to a
	 * target name containing "cargo hold" in case a hold variant's ID is not listed.
	 */
	private static boolean isCargoHold(MenuEntry entry)
	{
		if (!isGameObjectOption(entry.getType()))
		{
			return false;
		}
		return SalvageIds.CARGO_HOLD_OBJECT_IDS.contains(entry.getIdentifier())
			|| Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT).contains("cargo hold");
	}

	/**
	 * Finds the deposit option on the same object the top entry belongs to (same ID, same scene tile
	 * and same world view, so a second hold on screen is never picked): the configured names in order,
	 * then any other deposit option except "Deposit-held".
	 */
	private int findDepositEntry(MenuEntry[] entries, MenuEntry top)
	{
		for (String wanted : depositOptions)
		{
			for (int i = entries.length - 1; i >= 0; i--)
			{
				if (sameObject(entries[i], top) && wanted.equals(option(entries[i])))
				{
					return i;
				}
			}
		}
		for (int i = entries.length - 1; i >= 0; i--)
		{
			String option = option(entries[i]);
			if (sameObject(entries[i], top) && option.contains("deposit") && !option.equals(HELD_CARGO_OPTION))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Writes the hold's options to the debug log the first time each distinct menu is seen, so the
	 * real option names can be read from the debug log (client started with --debug).
	 */
	private void logOptionsOnce(MenuEntry[] entries, MenuEntry top, int depositIdx)
	{
		// Sorted, so the same menu before and after the swap logs only once
		TreeSet<String> options = new TreeSet<>();
		for (MenuEntry entry : entries)
		{
			if (sameObject(entry, top))
			{
				options.add(Text.removeTags(entry.getOption()));
			}
		}
		String summary = String.join(", ", options) + " (object " + top.getIdentifier() + ")";
		if (!summary.equals(loggedOptions))
		{
			loggedOptions = summary;
			log.debug("Cargo hold menu: {} -> {}", summary,
				depositIdx < 0 ? "no deposit option found" : "promoting \"" + Text.removeTags(entries[depositIdx].getOption()) + "\"");
		}
	}

	private static boolean sameObject(MenuEntry e, MenuEntry top)
	{
		return isGameObjectOption(e.getType())
			&& e.getIdentifier() == top.getIdentifier()
			&& e.getParam0() == top.getParam0()
			&& e.getParam1() == top.getParam1()
			&& e.getWorldViewId() == top.getWorldViewId();
	}

	private static String option(MenuEntry entry)
	{
		return Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT);
	}

	private static boolean isGameObjectOption(MenuAction type)
	{
		switch (type)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}
}
