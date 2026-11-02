package com.salvagehelper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/** Time until the cargo hold is full at the recent fill rate. */
class HoldEtaInfoBox extends InfoBox
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	private final CargoHoldTracker cargoHoldTracker;

	HoldEtaInfoBox(BufferedImage image, Plugin plugin, CargoHoldTracker cargoHoldTracker)
	{
		super(image, plugin);
		this.cargoHoldTracker = cargoHoldTracker;
	}

	@Override
	public String getText()
	{
		long millis = cargoHoldTracker.millisToFull();
		if (millis < 0)
		{
			return "...";
		}
		long minutes = millis / 60_000;
		return minutes >= 60 ? (minutes / 60) + "h" + (minutes % 60) : minutes + "m";
	}

	@Override
	public Color getTextColor()
	{
		return cargoHoldTracker.millisToFull() == 0 ? Color.RED : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		long millis = cargoHoldTracker.millisToFull();
		if (millis < 0)
		{
			return "Cargo hold full in: working out the rate</br>Needs a few minutes of salvage coming in";
		}
		if (millis == 0)
		{
			return "Cargo hold is full";
		}
		return "Cargo hold full in about " + Durations.approx(millis)
			+ "</br>Around " + LocalTime.now().plusSeconds(millis / 1000).format(CLOCK);
	}
}
