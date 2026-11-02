package com.salvagehelper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * Shows how many cargo hold slots are used while on your boat; turns orange at your alert limit and
 * red when full. Right-click it and pick "Set alert limit" to type a new limit in the chatbox.
 */
class CargoInfoBox extends InfoBox
{
	static final String SET_LIMIT_OPTION = "Set alert limit";
	private static final String TARGET = "Cargo hold";
	private static final Color LIMIT_COLOR = new Color(255, 160, 0);

	private final SalvageHelperConfig config;
	private final CargoHoldTracker cargoHoldTracker;

	CargoInfoBox(BufferedImage image, Plugin plugin, SalvageHelperConfig config, CargoHoldTracker cargoHoldTracker)
	{
		super(image, plugin);
		this.config = config;
		this.cargoHoldTracker = cargoHoldTracker;
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_INFOBOX, SET_LIMIT_OPTION, TARGET));
	}

	@Override
	public String getText()
	{
		CargoHoldTracker.Contents contents = cargoHoldTracker.current();
		if (contents == null)
		{
			return "?";
		}
		return (contents.isEstimate() ? "~" : "") + contents.getUsed();
	}

	@Override
	public Color getTextColor()
	{
		CargoHoldTracker.Contents contents = cargoHoldTracker.current();
		if (contents == null)
		{
			return Color.WHITE;
		}
		if (contents.isFull())
		{
			return Color.RED;
		}
		return contents.getUsed() >= config.cargoLimit() ? LIMIT_COLOR : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		CargoHoldTracker.Contents contents = cargoHoldTracker.current();
		String limit = "</br>Alert at " + config.cargoLimit() + " slots</br>Right-click to change the limit";
		if (contents == null)
		{
			return "Cargo hold: unknown until you open it" + limit;
		}
		String capacity = contents.getCapacity() > 0 ? "/" + contents.getCapacity() : "";
		String estimate = contents.isEstimate() ? "</br>Estimated since the hold was last opened" : "";
		return "Cargo hold: " + contents.getUsed() + capacity + " slots" + estimate + limit;
	}
}
