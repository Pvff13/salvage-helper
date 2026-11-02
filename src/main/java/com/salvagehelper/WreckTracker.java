package com.salvagehelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;

/**
 * Shipwreck sites in view and how long each has left.
 *
 * <h3>Timers are upper limits</h3>
 * A wreck stays up for at most its {@link ShipwreckType#getLifetimeSeconds() lifetime} from the
 * moment anyone first salvages it, which the client cannot see. So a wreck's clock starts the first
 * time salvage comes in while it is in reach of one of your hooks: if someone else got there first
 * it really has less time, so the shown time is always "at most". A sunk wreck shows when the next
 * one can rise at the latest: in an area the wrecks share one pool, so one rises as another sinks,
 * meaning the earliest known sink among the wrecks still up bounds the next rise.
 */
@Singleton
class WreckTracker
{
	static final class Site
	{
		@Getter
		private final WorldPoint point;
		@Getter
		private final ShipwreckType type;
		@Getter
		private GameObject object;
		@Getter
		private boolean active;
		/** Latest time it can sink, or -1 if its clock has not started. */
		@Getter
		private long sinksBy = -1;

		Site(WorldPoint point, ShipwreckType type)
		{
			this.point = point;
			this.type = type;
		}

		boolean inReachOf(WorldPoint hook)
		{
			return hook != null && hook.getPlane() == point.getPlane()
				&& Math.abs(hook.getX() - point.getX()) <= SalvageIds.HOOK_REACH_TILES
				&& Math.abs(hook.getY() - point.getY()) <= SalvageIds.HOOK_REACH_TILES;
		}
	}

	private final Map<WorldPoint, Site> sites = new HashMap<>();

	void spawned(GameObject object)
	{
		ShipwreckType type = ShipwreckType.fromObjectId(object.getId());
		if (type == null || object.getWorldView() == null || !object.getWorldView().isTopLevel())
		{
			return;
		}

		WorldPoint point = object.getWorldLocation();
		Site site = sites.computeIfAbsent(point, p -> new Site(p, type));
		boolean active = object.getId() == type.getActiveObjectId();
		if (site.active != active)
		{
			// Rose or sank: either way the old clock no longer applies
			site.sinksBy = -1;
		}
		site.object = object;
		site.active = active;
	}

	void despawned(GameObject object)
	{
		Site site = sites.get(object.getWorldLocation());
		if (site != null && site.object == object)
		{
			site.object = null;
		}
	}

	/** Drops sites whose object went away and was not replaced (scene unloaded). Once per tick. */
	void tick()
	{
		Iterator<Site> it = sites.values().iterator();
		while (it.hasNext())
		{
			if (it.next().object == null)
			{
				it.remove();
			}
		}
	}

	/** Salvage came in this tick: start the clock of every wreck that is up and in reach of a hook. */
	void salvageGained(List<WorldPoint> hooks)
	{
		long now = System.currentTimeMillis();
		for (Site site : sites.values())
		{
			if (site.active && site.sinksBy < 0 && inReachOfAny(site, hooks))
			{
				site.sinksBy = now + site.type.getLifetimeSeconds() * 1000L;
			}
		}
	}

	Collection<Site> sites()
	{
		return sites.values();
	}

	/** Wrecks that are up and in reach of at least one of the given hooks. */
	List<Site> activeInReach(List<WorldPoint> hooks)
	{
		List<Site> result = new ArrayList<>();
		for (Site site : sites.values())
		{
			if (site.active && inReachOfAny(site, hooks))
			{
				result.add(site);
			}
		}
		return result;
	}

	/** Whether any wreck site (up or sunk) is within this many tiles of the point. */
	boolean anySiteNear(WorldPoint point, int tiles)
	{
		if (point == null)
		{
			return false;
		}
		for (Site site : sites.values())
		{
			if (site.point.distanceTo2D(point) <= tiles)
			{
				return true;
			}
		}
		return false;
	}

	/** Latest time the next wreck can rise: the earliest known sink among wrecks still up, or -1. */
	long nextRiseBy()
	{
		long earliest = -1;
		for (Site site : sites.values())
		{
			if (site.active && site.sinksBy >= 0 && (earliest < 0 || site.sinksBy < earliest))
			{
				earliest = site.sinksBy;
			}
		}
		return earliest;
	}

	void reset()
	{
		sites.clear();
	}

	private static boolean inReachOfAny(Site site, List<WorldPoint> hooks)
	{
		for (WorldPoint hook : hooks)
		{
			if (site.inReachOf(hook))
			{
				return true;
			}
		}
		return false;
	}
}
