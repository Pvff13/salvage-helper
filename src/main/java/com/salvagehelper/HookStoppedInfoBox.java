package com.salvagehelper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/** Shown while salvaging has stopped. Displays the player's own message; the tooltip has it in full. */
class HookStoppedInfoBox extends InfoBox
{
	private final SalvageHelperConfig config;
	private final HookMonitor hookMonitor;

	HookStoppedInfoBox(BufferedImage image, Plugin plugin, SalvageHelperConfig config, HookMonitor hookMonitor)
	{
		super(image, plugin);
		this.config = config;
		this.hookMonitor = hookMonitor;
	}

	@Override
	public String getText()
	{
		return config.hookStoppedMessage();
	}

	@Override
	public Color getTextColor()
	{
		return Color.RED;
	}

	@Override
	public String getTooltip()
	{
		return config.hookStoppedMessage() + "</br>No salvaging for " + hookMonitor.secondsStopped() + "s";
	}
}
