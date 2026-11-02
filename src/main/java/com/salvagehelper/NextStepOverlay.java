package com.salvagehelper;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines what to click next on your boat.
 *
 * <ul>
 *   <li><b>Sort</b> (Automatic, on a boat with a salvaging station): the station while you carry
 *   salvage; the cargo hold when your inventory has no salvage but the hold does, to take some out.</li>
 *   <li><b>Deposit</b> (or Automatic without a station): the cargo hold once your inventory is full
 *   and holds salvage.</li>
 *   <li>Either way, with no salvage step left: a free hook, when a wreck is up in reach and you are
 *   not on a hook yourself. A hook counts as free when none of your crewmates stands next to it.</li>
 * </ul>
 */
class NextStepOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;
	private static final int HOOK_OCCUPIED_TILES = 2;

	private final SalvageHelperConfig config;
	private final SalvageSession session;
	private final BoatObjects boatObjects;
	private final CargoHoldTracker cargoHoldTracker;
	private final WreckTracker wreckTracker;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	NextStepOverlay(SalvageHelperConfig config, SalvageSession session, BoatObjects boatObjects,
		CargoHoldTracker cargoHoldTracker, WreckTracker wreckTracker, ModelOutlineRenderer modelOutlineRenderer)
	{
		this.config = config;
		this.session = session;
		this.boatObjects = boatObjects;
		this.cargoHoldTracker = cargoHoldTracker;
		this.wreckTracker = wreckTracker;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.nextStep() == SalvageHelperConfig.NextStepMode.OFF || !session.isOnBoat())
		{
			return null;
		}

		for (GameObject target : targets())
		{
			modelOutlineRenderer.drawOutline(target, OUTLINE_WIDTH, config.nextStepColor(), OUTLINE_FEATHER);
		}
		return null;
	}

	private List<GameObject> targets()
	{
		List<GameObject> targets = new ArrayList<>();
		GameObject hold = boatObjects.ownCargoHold();
		GameObject station = boatObjects.ownStation();
		CargoHoldTracker.Contents contents = cargoHoldTracker.current();
		boolean carrying = session.getInventorySalvage() > 0;
		boolean sortMode = config.nextStep() == SalvageHelperConfig.NextStepMode.AUTOMATIC && station != null;

		if (sortMode)
		{
			if (carrying)
			{
				targets.add(station);
				return targets;
			}
			if (hold != null && contents != null && contents.getSalvage() > 0 && !session.isInventoryFull())
			{
				targets.add(hold);
				return targets;
			}
		}
		else if (carrying && session.isInventoryFull() && hold != null)
		{
			targets.add(hold);
			return targets;
		}

		SalvageSession.Activity activity = session.getActivity();
		boolean atHook = activity == SalvageSession.Activity.HOOKING || activity == SalvageSession.Activity.AT_HOOK_IDLE;
		if (!atHook && !session.isInventoryFull()
			&& !wreckTracker.activeInReach(boatObjects.ownHookWorldPoints()).isEmpty())
		{
			targets.addAll(freeHooks());
		}
		return targets;
	}

	private List<GameObject> freeHooks()
	{
		List<GameObject> free = new ArrayList<>();
		WorldView boat = boatObjects.ownBoatView();
		if (boat == null)
		{
			return free;
		}
		int occupied = HOOK_OCCUPIED_TILES * Perspective.LOCAL_TILE_SIZE;
		for (GameObject hook : boatObjects.ownHooks())
		{
			LocalPoint hookPos = hook.getLocalLocation();
			boolean taken = false;
			for (NPC npc : boat.npcs())
			{
				LocalPoint npcPos = npc == null ? null : npc.getLocalLocation();
				if (npcPos != null && CrewTracker.isCrewmate(npc) && npcPos.distanceTo(hookPos) <= occupied)
				{
					taken = true;
					break;
				}
			}
			if (!taken)
			{
				free.add(hook);
			}
		}
		return free;
	}
}
