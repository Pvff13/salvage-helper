package com.salvagehelper;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** While the cargo hold is open, boxes the salvage in it so it stands out among cannonballs and kits. */
class HoldSalvageOverlay extends WidgetItemOverlay
{
	private final SalvageHelperConfig config;

	@Inject
	HoldSalvageOverlay(SalvageHelperConfig config)
	{
		this.config = config;
		showOnInterfaces(InterfaceID.SAILING_BOAT_CARGOHOLD);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.boxSalvageInHold() || !SalvageIds.SALVAGE_ITEM_IDS.contains(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(config.holdSalvageColor());
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}
}
