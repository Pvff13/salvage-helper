package com.salvagehelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Renderable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.util.Text;

/**
 * Decides which of the player's crewmates are idle (not on a salvaging hook) and should be hidden.
 *
 * <h3>How "on a hook" is detected</h3>
 * <ol>
 *   <li><b>Job assignment (primary).</b> The game's crew varbits say which job each crewmate is on
 *   (see {@link CrewRoster}); a crewmate assigned to a hook counts as on it, anyone else (idle, sails,
 *   repairs...) does not. This is exact and does not care where they are walking.</li>
 *   <li><b>Animation (fallback)</b> for a crewmate that cannot be matched to a crew slot: the
 *   {@link SalvageIds#HOOK_OPERATOR_ANIMATIONS} set.</li>
 *   <li><b>Proximity (optional, fallback only).</b> Within {@link #HOOK_PROXIMITY_TILES} tiles of a
 *   hook. Off by default: much of a deck is that close to a hook, so it kept walking crew visible.</li>
 * </ol>
 * A crewmate is only hidden once they have been off a hook for longer than the grace period, so
 * they do not flicker in and out.
 *
 * <h3>Scope</h3>
 * Hiding only applies while the player stands on a boat whose WorldEntity owner type says it is
 * theirs, and only to crewmate NPCs in that boat's world view. Other players, other NPCs, other
 * people's crews and the local player are never touched.
 */
@Slf4j
@Singleton
class CrewTracker
{
	/** Distance, in tiles, at which a crewmate counts as standing at a hook for the fallback. */
	private static final int HOOK_PROXIMITY_TILES = 2;

	private final Client client;
	private final SalvageHelperConfig config;
	private final BoatObjects boatObjects;
	private final CrewRoster crewRoster;

	/** Last game tick each crewmate was seen on a hook (or first seen, so new crew get a grace period). */
	private final Map<NPC, Integer> lastOnHookTick = new HashMap<>();
	/** Crewmates to skip when drawing. Read by the draw callback, written on game ticks (both client thread). */
	private final Set<NPC> hidden = new HashSet<>();
	/** Last crew report written to the log. */
	private String lastReport;

	@Inject
	CrewTracker(Client client, SalvageHelperConfig config, BoatObjects boatObjects, CrewRoster crewRoster)
	{
		this.client = client;
		this.config = config;
		this.boatObjects = boatObjects;
		this.crewRoster = crewRoster;
	}

	void npcDespawned(NPC npc)
	{
		lastOnHookTick.remove(npc);
		hidden.remove(npc);
	}

	/** Forgets all crew state and shows everyone again. */
	void reset()
	{
		lastOnHookTick.clear();
		hidden.clear();
		lastReport = null;
	}

	/** Recomputes the hidden set. Called once per game tick. */
	void update()
	{
		hidden.clear();
		if (!config.hideIdleCrewmates())
		{
			return;
		}

		// Crew live in the boat's own world view; only the player's own boat counts
		WorldView boat = boatObjects.ownBoatView();
		if (boat == null)
		{
			lastOnHookTick.clear();
			return;
		}

		int tick = client.getTickCount();
		int grace = config.graceTicks();
		Set<NPC> seen = new HashSet<>();
		List<CrewRoster.Member> members = crewRoster.members();
		TreeSet<String> report = new TreeSet<>();

		for (NPC npc : boat.npcs())
		{
			if (npc == null)
			{
				continue;
			}
			CrewRoster.Member member = CrewRoster.find(members, npc);
			if (member == null && !isCrewmate(npc))
			{
				report.add(describe(npc, null) + " not crew");
				continue;
			}
			seen.add(npc);

			// The game's own job assignment decides when the crewmate can be matched to a slot; the
			// animation (and optional proximity) guess is only for crew that cannot be matched
			boolean onHook = member != null ? member.onHook() : isOnHook(npc, boat);
			if (onHook)
			{
				lastOnHookTick.put(npc, tick);
			}
			else
			{
				int last = lastOnHookTick.computeIfAbsent(npc, n -> tick);
				if (tick - last > grace)
				{
					hidden.add(npc);
				}
			}
			report.add(describe(npc, member) + (onHook ? " on hook" : " idle") + (hidden.contains(npc) ? " HIDDEN" : ""));
		}

		lastOnHookTick.keySet().retainAll(seen);
		logOnChange(members, report);
	}

	private static String describe(NPC npc, CrewRoster.Member member)
	{
		NPCComposition transformed = npc.getTransformedComposition();
		return Text.removeTags(String.valueOf(npc.getName())) + " (npc " + npc.getId()
			+ (transformed != null && transformed.getId() != npc.getId() ? "/" + transformed.getId() : "")
			+ (member != null ? ", job " + member.position : ", no crew slot match") + ")";
	}

	/**
	 * Writes what the hider sees to the debug log whenever it changes, so a crewmate that is not
	 * hidden can be diagnosed from the debug log (client started with --debug).
	 */
	private void logOnChange(List<CrewRoster.Member> members, TreeSet<String> report)
	{
		StringBuilder slots = new StringBuilder();
		for (CrewRoster.Member member : members)
		{
			slots.append(slots.length() == 0 ? "" : ", ")
				.append(member.uniqueId).append(" job ").append(member.position).append(" npcs ").append(member.npcIds);
		}
		String summary = "crew slots [" + slots + "], boat NPCs " + (report.isEmpty() ? "[none]" : report);
		if (!summary.equals(lastReport))
		{
			lastReport = summary;
			log.debug("Crew hider: {}", summary);
		}
	}

	/** RenderCallback.addEntity body: false skips drawing the model and its overhead text/hitsplats. */
	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		return !(renderable instanceof NPC) || !hidden.contains(renderable);
	}

	static boolean isCrewmate(NPC npc)
	{
		if (SalvageIds.CREWMATE_NPC_IDS.contains(npc.getId()))
		{
			return true;
		}
		// Crewmates may be multi-NPCs that transform with varbits; check the resolved ID as well.
		NPCComposition transformed = npc.getTransformedComposition();
		return transformed != null && SalvageIds.CREWMATE_NPC_IDS.contains(transformed.getId());
	}

	private boolean isOnHook(NPC npc, WorldView boat)
	{
		if (SalvageIds.HOOK_OPERATOR_ANIMATIONS.contains(npc.getAnimation()))
		{
			return true;
		}

		if (!config.hookProximityFallback())
		{
			return false;
		}

		LocalPoint npcPos = npc.getLocalLocation();
		if (npcPos == null)
		{
			return false;
		}

		int maxDistance = HOOK_PROXIMITY_TILES * Perspective.LOCAL_TILE_SIZE;
		for (GameObject hook : boatObjects.ownHooks())
		{
			LocalPoint hookPos = hook.getLocalLocation();
			if (hookPos != null && hookPos.distanceTo(npcPos) <= maxDistance)
			{
				return true;
			}
		}
		return false;
	}
}
