package com.salvagehelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.client.util.Text;

/**
 * The player's crew and the job each is assigned to, straight from the game.
 *
 * <p>Varbits {@link SalvageIds#CREW_SLOT_VARBITS} hold which crewmate sits in each crew slot (their
 * unique id, 0 for an empty slot) and {@link SalvageIds#CREW_POSITION_VARBITS} the job they are on.
 * The game's crew table ({@link DBTableID.SailingCrew}) turns a unique id into the crewmate's NPCs
 * on deck: the boat NPC and the one carrying cargo. An NPC on the boat is matched to its slot by
 * NPC id (as spawned or transformed) and, if that fails, by name.
 */
@Slf4j
@Singleton
class CrewRoster
{
	static final class Member
	{
		final int uniqueId;
		final int position;
		final List<Integer> npcIds;
		final String name;

		Member(int uniqueId, int position, List<Integer> npcIds, String name)
		{
			this.uniqueId = uniqueId;
			this.position = position;
			this.npcIds = npcIds;
			this.name = name;
		}

		boolean onHook()
		{
			return SalvageIds.CREW_POSITION_HOOKS.contains(position);
		}
	}

	private static final class Lookup
	{
		final List<Integer> npcIds;
		final String name;

		Lookup(List<Integer> npcIds, String name)
		{
			this.npcIds = npcIds;
			this.name = name;
		}
	}

	private final Client client;
	/** Crew table lookups never change, so each unique id is read once. */
	private final Map<Integer, Lookup> lookups = new HashMap<>();

	@Inject
	CrewRoster(Client client)
	{
		this.client = client;
	}

	/** The current crew, one entry per filled slot. Client thread only. */
	List<Member> members()
	{
		List<Member> members = new ArrayList<>();
		for (int i = 0; i < SalvageIds.CREW_SLOT_VARBITS.length; i++)
		{
			int uniqueId = client.getVarbitValue(SalvageIds.CREW_SLOT_VARBITS[i]);
			if (uniqueId == 0)
			{
				continue;
			}
			int position = client.getVarbitValue(SalvageIds.CREW_POSITION_VARBITS[i]);
			Lookup lookup = lookups.computeIfAbsent(uniqueId, this::lookup);
			members.add(new Member(uniqueId, position, lookup.npcIds, lookup.name));
		}
		return members;
	}

	/** The crew slot this NPC belongs to, or null if it cannot be matched. */
	static Member find(List<Member> members, NPC npc)
	{
		int id = npc.getId();
		NPCComposition transformed = npc.getTransformedComposition();
		int transformedId = transformed == null ? -1 : transformed.getId();
		for (Member member : members)
		{
			if (member.npcIds.contains(id) || member.npcIds.contains(transformedId))
			{
				return member;
			}
		}

		String name = realName(npc.getName());
		if (name != null)
		{
			for (Member member : members)
			{
				if (name.equals(member.name))
				{
					return member;
				}
			}
		}
		return null;
	}

	void reset()
	{
		lookups.clear();
	}

	private Lookup lookup(int uniqueId)
	{
		List<Integer> npcIds = new ArrayList<>();
		String name = "";
		try
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SailingCrew.ID, DBTableID.SailingCrew.COL_UNIQUE_ID, 0, uniqueId);
			if (rows != null && !rows.isEmpty())
			{
				int row = rows.get(0);
				for (int column : new int[]{DBTableID.SailingCrew.COL_BOAT_NPC, DBTableID.SailingCrew.COL_CARGO_NPC})
				{
					Object[] field = client.getDBTableField(row, column, 0);
					if (field != null && field.length > 0 && field[0] instanceof Integer)
					{
						addVariants(npcIds, (Integer) field[0]);
					}
				}
				for (int npcId : npcIds)
				{
					NPCComposition definition = client.getNpcDefinition(npcId);
					String found = definition == null ? null : realName(definition.getName());
					if (found != null)
					{
						name = found;
						break;
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read crewmate {} from the crew table", uniqueId, e);
		}
		log.debug("Crew table: crewmate {} -> NPC ids {}, name \"{}\"", uniqueId, npcIds, name);
		return new Lookup(npcIds, name);
	}

	/**
	 * The crew table names the crewmate's base NPC (e.g. SAILING_CREW_GENERIC_4_SHIP, 15284), but on
	 * deck they show as one of its variants (_SHIP_OP 15285 or _SHIP_NO_OP 15286; for the cargo NPC
	 * _CARGO_2 / _CARGO_3), seen in game 2026-11-02. Every NPC the base can transform into is added,
	 * plus the next two ids in case the base lists no transforms.
	 */
	private void addVariants(List<Integer> npcIds, int baseId)
	{
		npcIds.add(baseId);
		NPCComposition base = client.getNpcDefinition(baseId);
		int[] configs = base == null ? null : base.getConfigs();
		if (configs != null)
		{
			for (int id : configs)
			{
				if (id > 0 && !npcIds.contains(id))
				{
					npcIds.add(id);
				}
			}
		}
		for (int offset = 1; offset <= 2; offset++)
		{
			if (!npcIds.contains(baseId + offset))
			{
				npcIds.add(baseId + offset);
			}
		}
	}

	/** A display name, or null for none: unnamed NPCs are called "null" in the game's data. */
	static String realName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String plain = Text.removeTags(name).trim().toLowerCase(Locale.ROOT);
		return plain.isEmpty() || plain.equals("null") ? null : plain;
	}
}
