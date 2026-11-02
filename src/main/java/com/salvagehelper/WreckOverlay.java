package com.salvagehelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Draws on the sea around wrecks while you are on your own boat:
 * <ul>
 *   <li>an outline on each wreck that is up, and a timer on every wreck (see {@link WreckTracker});</li>
 *   <li>reach boxes: the square of sea where a hook can reach each wreck (8 tiles each way);</li>
 *   <li>double spot boxes: where two wrecks' reach squares overlap, so one hook reaches both. Once
 *   every hook on your boat is inside one, the box changes colour and says "Parked".</li>
 * </ul>
 */
class WreckOverlay extends Overlay
{
	private static final Color SUNK_COLOR = new Color(150, 150, 150);
	private static final Color TIMER_COLOR = new Color(80, 220, 255);
	private static final int FILL_ALPHA = 28;

	private final Client client;
	private final SalvageHelperConfig config;
	private final WreckTracker wreckTracker;
	private final BoatObjects boatObjects;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	WreckOverlay(Client client, SalvageHelperConfig config, WreckTracker wreckTracker, BoatObjects boatObjects,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.config = config;
		this.wreckTracker = wreckTracker;
		this.boatObjects = boatObjects;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<WorldPoint> hooks = boatObjects.ownHookWorldPoints();
		if (hooks.isEmpty())
		{
			// Not on your own boat, or it has no salvaging hook
			return null;
		}

		List<WreckTracker.Site> sites = new ArrayList<>(wreckTracker.sites());
		long now = System.currentTimeMillis();

		boolean parked = false;
		if (config.doubleSpotBoxes())
		{
			parked = drawDoubleSpots(graphics, sites, hooks);
		}

		if (config.reachBoxes() && !(parked && config.hideReachWhenParked()))
		{
			for (WreckTracker.Site site : sites)
			{
				Color color = site.isActive() ? config.reachColor() : SUNK_COLOR;
				drawBox(graphics, reachBox(site.getPoint()), color, null);
			}
		}

		for (WreckTracker.Site site : sites)
		{
			GameObject object = site.getObject();
			if (object == null)
			{
				continue;
			}
			if (config.outlineWrecks() && site.isActive())
			{
				modelOutlineRenderer.drawOutline(object, 2, config.reachColor(), 4);
			}
			if (config.wreckTimers())
			{
				String text = timerText(site, now);
				Point at = object.getCanvasTextLocation(graphics, text, 0);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(graphics, at, text, site.isActive() ? TIMER_COLOR : SUNK_COLOR);
				}
			}
		}
		return null;
	}

	/** @return true if every hook is inside one of the boxes */
	private boolean drawDoubleSpots(Graphics2D graphics, List<WreckTracker.Site> sites, List<WorldPoint> hooks)
	{
		boolean anyParked = false;
		for (int i = 0; i < sites.size(); i++)
		{
			for (int j = i + 1; j < sites.size(); j++)
			{
				int[] box = overlap(reachBox(sites.get(i).getPoint()), reachBox(sites.get(j).getPoint()));
				if (box == null)
				{
					continue;
				}

				int inside = 0;
				for (WorldPoint hook : hooks)
				{
					if (contains(box, hook))
					{
						inside++;
					}
				}
				boolean parked = inside == hooks.size();
				anyParked |= parked;

				String label = null;
				if (config.labelDoubleSpots())
				{
					label = parked ? "Parked" : "Park here (" + inside + "/" + hooks.size() + ")";
				}
				drawBox(graphics, box, parked ? config.parkedColor() : config.doubleSpotColor(), label);
			}
		}
		return anyParked;
	}

	private String timerText(WreckTracker.Site site, long now)
	{
		if (site.isActive())
		{
			return site.getSinksBy() < 0 ? site.getType().getDisplayName() + " wreck"
				: "Sinks in ≤ " + Durations.countdown(site.getSinksBy() - now);
		}
		long next = wreckTracker.nextRiseBy();
		return next < 0 ? "Sunk" : "Sunk - next ≤ " + Durations.countdown(next - now);
	}

	/** {minX, minY, maxX, maxY, plane} of the sea a hook can reach the wreck from. */
	private static int[] reachBox(WorldPoint wreck)
	{
		int r = SalvageIds.HOOK_REACH_TILES;
		return new int[]{wreck.getX() - r, wreck.getY() - r, wreck.getX() + r, wreck.getY() + r, wreck.getPlane()};
	}

	private static int[] overlap(int[] a, int[] b)
	{
		if (a[4] != b[4])
		{
			return null;
		}
		int minX = Math.max(a[0], b[0]);
		int minY = Math.max(a[1], b[1]);
		int maxX = Math.min(a[2], b[2]);
		int maxY = Math.min(a[3], b[3]);
		return minX > maxX || minY > maxY ? null : new int[]{minX, minY, maxX, maxY, a[4]};
	}

	private static boolean contains(int[] box, WorldPoint point)
	{
		return point.getPlane() == box[4]
			&& point.getX() >= box[0] && point.getX() <= box[2]
			&& point.getY() >= box[1] && point.getY() <= box[3];
	}

	private void drawBox(Graphics2D graphics, int[] box, Color color, String label)
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return;
		}
		int width = box[2] - box[0] + 1;
		int height = box[3] - box[1] + 1;
		LocalPoint southWest = LocalPoint.fromWorld(top, box[0], box[1]);
		if (southWest == null)
		{
			return;
		}
		// The polygon helper wants the middle of the area
		LocalPoint middle = southWest.plus((width - 1) * Perspective.LOCAL_TILE_SIZE / 2,
			(height - 1) * Perspective.LOCAL_TILE_SIZE / 2);
		Polygon poly = Perspective.getCanvasTileAreaPoly(client, middle, width, height, top.getPlane(), 0);
		if (poly == null)
		{
			return;
		}

		graphics.setStroke(new BasicStroke(2));
		graphics.setColor(color);
		graphics.draw(poly);
		if (config.fillBoxes())
		{
			graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), FILL_ALPHA));
			graphics.fill(poly);
		}

		if (label != null)
		{
			Point at = Perspective.getCanvasTextLocation(client, graphics, middle, label, 0);
			if (at != null)
			{
				OverlayUtil.renderTextLocation(graphics, at, label, color);
			}
		}
	}
}
