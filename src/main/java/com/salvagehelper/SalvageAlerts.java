package com.salvagehelper;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/**
 * Cargo hold and hook-stopped alerts: their infoboxes, banner lines and notifications.
 *
 * <ul>
 *   <li><b>Your limit</b>: one notification when the hold reaches the number you picked; re-arms when
 *   it drops back below or the limit changes.</li>
 *   <li><b>Full</b>: a notification when the hold fills, repeated every few minutes if you asked for
 *   it, and a red banner while it stays full.</li>
 *   <li><b>Hook stopped</b>: your own message, once per stop; re-arms as soon as salvaging resumes.</li>
 * </ul>
 */
@Singleton
class SalvageAlerts
{
	private static final int MAX_LIMIT = 240;

	private final SalvageHelperConfig config;
	private final ConfigManager configManager;
	private final InfoBoxManager infoBoxManager;
	private final ItemManager itemManager;
	private final Notifier notifier;
	private final ChatboxPanelManager chatboxPanelManager;
	private final ClientThread clientThread;
	private final CargoHoldTracker cargoHoldTracker;
	private final HookMonitor hookMonitor;
	private final Banner banner;

	private CargoInfoBox cargoInfoBox;
	private HookStoppedInfoBox hookStoppedInfoBox;
	private HoldEtaInfoBox etaInfoBox;
	private boolean limitAlerted;
	private boolean wasFull;
	private long fullNotifiedAt = -1;

	@Inject
	SalvageAlerts(SalvageHelperConfig config, ConfigManager configManager, InfoBoxManager infoBoxManager,
		ItemManager itemManager, Notifier notifier, ChatboxPanelManager chatboxPanelManager,
		ClientThread clientThread, CargoHoldTracker cargoHoldTracker, HookMonitor hookMonitor, Banner banner)
	{
		this.config = config;
		this.configManager = configManager;
		this.infoBoxManager = infoBoxManager;
		this.itemManager = itemManager;
		this.notifier = notifier;
		this.chatboxPanelManager = chatboxPanelManager;
		this.clientThread = clientThread;
		this.cargoHoldTracker = cargoHoldTracker;
		this.hookMonitor = hookMonitor;
		this.banner = banner;
	}

	void startUp(Plugin plugin)
	{
		cargoInfoBox = new CargoInfoBox(
			itemManager.getImage(ItemID.SAILING_LARGE_SHIPWRECK_SALVAGE), plugin, config, cargoHoldTracker);
		// The skill guide's hook icon; it is a display-only item, so confirm it renders (see README)
		hookStoppedInfoBox = new HookStoppedInfoBox(
			itemManager.getImage(ItemID.SAILING_SKILLGUIDE_SALVAGING_HOOK_BRONZE), plugin, config, hookMonitor);
		etaInfoBox = new HoldEtaInfoBox(
			itemManager.getImage(ItemID.SAILING_MERCHANT_SHIPWRECK_SALVAGE), plugin, cargoHoldTracker);
	}

	void shutDown()
	{
		infoBoxManager.removeInfoBox(cargoInfoBox);
		infoBoxManager.removeInfoBox(hookStoppedInfoBox);
		infoBoxManager.removeInfoBox(etaInfoBox);
		limitAlerted = false;
		wasFull = false;
		fullNotifiedAt = -1;
	}

	/** Called once per tick after the session, cargo tracker and hook monitor have updated. */
	void tick(boolean onBoat, boolean hookJustStopped)
	{
		CargoHoldTracker.Contents contents = cargoHoldTracker.current();
		long now = System.currentTimeMillis();

		boolean full = onBoat && contents != null && contents.isFull();
		boolean atLimit = onBoat && contents != null && !full && contents.getUsed() >= config.cargoLimit();
		String count = contents == null ? "" : contents.getUsed()
			+ (contents.getCapacity() > 0 ? "/" + contents.getCapacity() : "");

		if (atLimit && !limitAlerted)
		{
			notifier.notify(config.cargoLimitNotification(),
				"Your cargo hold has reached " + count + " slots (your limit is " + config.cargoLimit() + ").");
		}
		if (contents != null)
		{
			limitAlerted = contents.getUsed() >= config.cargoLimit();
		}
		banner.toggle("limit", atLimit && config.limitBanner(), Banner.Level.LIMIT, "CARGO HOLD AT " + count);

		long repeat = config.fullRepeatMinutes() * 60_000L;
		if (full && (!wasFull || (repeat > 0 && now - fullNotifiedAt >= repeat)))
		{
			fullNotifiedAt = now;
			notifier.notify(config.fullHoldNotification(), "Your cargo hold is full (" + count + ").");
		}
		wasFull = full;
		banner.toggle("full", full, Banner.Level.FULL, "CARGO HOLD FULL");

		if (hookJustStopped)
		{
			notifier.notify(config.hookStoppedNotification(), config.hookStoppedMessage());
		}

		setVisible(cargoInfoBox, onBoat && config.cargoInfobox());
		setVisible(hookStoppedInfoBox, onBoat && config.hookStoppedInfobox() && hookMonitor.isStopped());
		setVisible(etaInfoBox, onBoat && config.etaInfobox() && contents != null);
	}

	/** Lets the limit alert fire again for the new limit if the hold is already over it. */
	void limitChanged()
	{
		limitAlerted = false;
	}

	boolean isCargoInfoBox(InfoBox infoBox)
	{
		return infoBox == cargoInfoBox;
	}

	/** Opens a chatbox number prompt and saves the answer as the cargo alert limit. */
	void promptForLimit()
	{
		clientThread.invoke(() -> chatboxPanelManager.openTextInput("Alert when the cargo hold uses this many slots:")
			.value(Integer.toString(config.cargoLimit()))
			.charValidator(c -> c >= '0' && c <= '9')
			.onDone((Consumer<String>) value ->
			{
				if (value.isEmpty())
				{
					return;
				}
				try
				{
					int limit = Math.max(1, Math.min(MAX_LIMIT, Integer.parseInt(value)));
					configManager.setConfiguration(SalvageHelperConfig.GROUP, "cargoLimit", limit);
				}
				catch (NumberFormatException ignored)
				{
					// Only digits can be typed, so this is just an over-long number; keep the old limit
				}
			})
			.build());
	}

	private void setVisible(InfoBox infoBox, boolean visible)
	{
		boolean shown = infoBoxManager.getInfoBoxes().contains(infoBox);
		if (visible && !shown)
		{
			infoBoxManager.addInfoBox(infoBox);
		}
		else if (!visible && shown)
		{
			infoBoxManager.removeInfoBox(infoBox);
		}
	}
}
