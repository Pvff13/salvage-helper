package com.salvagehelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Salvaging hooks, cargo holds and salvaging stations on boats, plus helpers for the player's own
 * boat.
 *
 * <p>Every boat is a WorldEntity with its own world view, and its objects use that world view's
 * local coordinates. The player's own boat is the world entity whose world view the player stands
 * in and whose owner type is {@link WorldEntity#OWNER_TYPE_SELF_PLAYER}, which also rules out
 * visiting someone else's boat. {@link WorldEntity#transformToMainWorld} turns a point on the boat
 * into a point on the sea, which is how hooks are compared with wrecks.
 */
@Singleton
class BoatObjects
{
	private final Client client;

	private final Set<GameObject> hooks = new HashSet<>();
	private final Set<GameObject> cargoHolds = new HashSet<>();
	private final Set<GameObject> stations = new HashSet<>();

	@Inject
	BoatObjects(Client client)
	{
		this.client = client;
	}

	void spawned(GameObject object)
	{
		int id = object.getId();
		if (SalvageIds.SALVAGING_HOOK_OBJECT_IDS.contains(id))
		{
			hooks.add(object);
		}
		else if (SalvageIds.CARGO_HOLD_OBJECT_IDS.contains(id))
		{
			cargoHolds.add(object);
		}
		else if (SalvageIds.SALVAGING_STATION_OBJECT_IDS.contains(id))
		{
			stations.add(object);
		}
	}

	void despawned(GameObject object)
	{
		hooks.remove(object);
		cargoHolds.remove(object);
		stations.remove(object);
	}

	void reset()
	{
		hooks.clear();
		cargoHolds.clear();
		stations.clear();
	}

	/** The boat the player stands on, if it is their own; null ashore or on another player's boat. */
	WorldEntity ownBoat()
	{
		Player player = client.getLocalPlayer();
		WorldView top = client.getTopLevelWorldView();
		if (player == null || top == null)
		{
			return null;
		}
		WorldView view = player.getWorldView();
		if (view == null || view.isTopLevel())
		{
			return null;
		}
		WorldEntity boat = top.worldEntities().byIndex(view.getId());
		return boat != null && boat.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER ? boat : null;
	}

	boolean isOnOwnBoat()
	{
		return ownBoat() != null;
	}

	/** The world view of the player's own boat, or null. */
	WorldView ownBoatView()
	{
		WorldEntity boat = ownBoat();
		return boat == null ? null : boat.getWorldView();
	}

	List<GameObject> ownHooks()
	{
		return onOwnBoat(hooks);
	}

	GameObject ownCargoHold()
	{
		List<GameObject> holds = onOwnBoat(cargoHolds);
		return holds.isEmpty() ? null : holds.get(0);
	}

	GameObject ownStation()
	{
		List<GameObject> found = onOwnBoat(stations);
		return found.isEmpty() ? null : found.get(0);
	}

	/** Slots in the own boat's hold, or -1 if the hold or its tier is unknown. */
	int ownHoldCapacity()
	{
		GameObject hold = ownCargoHold();
		if (hold == null)
		{
			return -1;
		}
		return SalvageIds.HOLD_CAPACITY.getOrDefault(hold.getId(), -1);
	}

	/** Where each of the own boat's hooks is on the sea right now. */
	List<WorldPoint> ownHookWorldPoints()
	{
		List<WorldPoint> points = new ArrayList<>();
		WorldEntity boat = ownBoat();
		if (boat == null)
		{
			return points;
		}
		for (GameObject hook : onOwnBoat(hooks))
		{
			WorldPoint point = toSea(boat, hook.getLocalLocation());
			if (point != null)
			{
				points.add(point);
			}
		}
		return points;
	}

	/** Where the own boat is on the sea, or null. */
	WorldPoint ownBoatWorldPoint()
	{
		WorldEntity boat = ownBoat();
		if (boat == null || boat.getLocalLocation() == null)
		{
			return null;
		}
		return WorldPoint.fromLocal(client, boat.getLocalLocation());
	}

	private WorldPoint toSea(WorldEntity boat, LocalPoint onBoat)
	{
		if (onBoat == null)
		{
			return null;
		}
		LocalPoint sea = boat.transformToMainWorld(onBoat);
		return sea == null ? null : WorldPoint.fromLocal(client, sea);
	}

	private List<GameObject> onOwnBoat(Set<GameObject> objects)
	{
		List<GameObject> result = new ArrayList<>();
		WorldView view = ownBoatView();
		if (view == null)
		{
			return result;
		}
		for (GameObject object : objects)
		{
			if (object.getWorldView() == view)
			{
				result.add(object);
			}
		}
		return result;
	}
}
