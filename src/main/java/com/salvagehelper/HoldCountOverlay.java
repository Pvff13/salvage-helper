package com.salvagehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ColorUtil;

/**
 * Writes the slot count on your cargo hold ("39/160", "~39/160" while estimated), fading from green
 * through yellow at half full to red when full.
 */
class HoldCountOverlay extends Overlay
{
	private static final Color EMPTY = new Color(80, 220, 80);
	private static final Color HALF = new Color(255, 255, 0);
	private static final Color FULL = new Color(255, 0, 0);
	/** Draw the text a little above the hold's base. */
	private static final int Z_OFFSET = 60;

	private final SalvageHelperConfig config;
	private final BoatObjects boatObjects;
	private final CargoHoldTracker cargoHoldTracker;

	@Inject
	HoldCountOverlay(SalvageHelperConfig config, BoatObjects boatObjects, CargoHoldTracker cargoHoldTracker)
	{
		this.config = config;
		this.boatObjects = boatObjects;
		this.cargoHoldTracker = cargoHoldTracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.countOnHold())
		{
			return null;
		}
		GameObject hold = boatObjects.ownCargoHold();
		CargoHoldTracker.Contents contents = cargoHoldTracker.current();
		if (hold == null || contents == null)
		{
			return null;
		}

		String text = (contents.isEstimate() ? "~" : "") + contents.getUsed()
			+ (contents.getCapacity() > 0 ? "/" + contents.getCapacity() : "");
		Point at = hold.getCanvasTextLocation(graphics, text, Z_OFFSET);
		if (at != null)
		{
			OverlayUtil.renderTextLocation(graphics, at, text, color(contents));
		}
		return null;
	}

	private static Color color(CargoHoldTracker.Contents contents)
	{
		if (contents.getCapacity() <= 0)
		{
			return Color.WHITE;
		}
		double fill = Math.min(1, (double) contents.getUsed() / contents.getCapacity());
		return fill < 0.5
			? ColorUtil.colorLerp(EMPTY, HALF, fill * 2)
			: ColorUtil.colorLerp(HALF, FULL, (fill - 0.5) * 2);
	}
}
