package com.salvagehelper;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Salvage in inventory and hold, salvage gained and per hour, and Sailing XP gained and per hour. */
class SalvageCounterOverlay extends OverlayPanel
{
	private final SalvageHelperConfig config;
	private final SalvageSession session;
	private final CargoHoldTracker cargoHoldTracker;
	private final NumberFormat numberFormat = NumberFormat.getIntegerInstance();

	@Inject
	SalvageCounterOverlay(SalvageHelperConfig config, SalvageSession session, CargoHoldTracker cargoHoldTracker)
	{
		this.config = config;
		this.session = session;
		this.cargoHoldTracker = cargoHoldTracker;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Salvage counter", e -> session.resetCounter());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showSalvageCounter() || (!session.isOnBoat() && !session.isCounting()))
		{
			return null;
		}

		if (config.counterInventory())
		{
			line("Inventory", numberFormat.format(session.getInventorySalvage()));
		}

		CargoHoldTracker.Contents cargo = cargoHoldTracker.current();
		if (config.counterCargoHold())
		{
			line("Cargo hold", cargo == null ? "?" : (cargo.isEstimate() ? "~" : "") + numberFormat.format(cargo.getSalvage()));
		}

		if (config.holdFullLine() && cargo != null)
		{
			long millis = cargoHoldTracker.millisToFull();
			line("Hold full in", millis < 0 ? "..." : millis == 0 ? "Full" : Durations.approx(millis));
		}

		if (session.isCounting())
		{
			if (config.counterGained())
			{
				line("Gained", numberFormat.format(session.getSalvageGained()));
			}
			if (config.counterSalvagePerHour())
			{
				line("Salvage/hr", numberFormat.format(session.salvagePerHour()));
			}
			if (config.counterXpGained())
			{
				line("XP gained", numberFormat.format(session.getXpGained()));
			}
			if (config.counterXpPerHour())
			{
				line("XP/hr", numberFormat.format(session.xpPerHour()));
			}
		}

		// With every line switched off, draw nothing rather than an empty title box
		if (panelComponent.getChildren().isEmpty())
		{
			return null;
		}
		if (config.counterTitle())
		{
			panelComponent.getChildren().add(0, TitleComponent.builder().text("Salvage").build());
		}

		return super.render(graphics);
	}

	private void line(String label, String value)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(label)
			.right(value)
			.build());
	}
}
