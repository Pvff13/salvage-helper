package com.salvagehelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ObjectID;

/**
 * The eight shipwreck kinds. Object IDs are gameval constants; the Sailing level and lifetime are
 * game facts (OSRS wiki, as also tabulated by AFK Shipwrecks, BSD-2). A wreck that is up is the
 * plain object; once it sinks it is replaced by the _STUMP object at the same spot.
 */
@Getter
@RequiredArgsConstructor
enum ShipwreckType
{
	SMALL("Small", 15, 60, ObjectID.SAILING_SMALL_SHIPWRECK, ObjectID.SAILING_SMALL_SHIPWRECK_STUMP),
	FISHERMAN("Fisherman's", 26, 180, ObjectID.SAILING_FISHERMAN_SHIPWRECK, ObjectID.SAILING_FISHERMAN_SHIPWRECK_STUMP),
	BARRACUDA("Barracuda", 35, 180, ObjectID.SAILING_BARRACUDA_SHIPWRECK, ObjectID.SAILING_BARRACUDA_SHIPWRECK_STUMP),
	LARGE("Large", 53, 180, ObjectID.SAILING_LARGE_SHIPWRECK, ObjectID.SAILING_LARGE_SHIPWRECK_STUMP),
	PIRATE("Pirate", 64, 180, ObjectID.SAILING_PIRATE_SHIPWRECK, ObjectID.SAILING_PIRATE_SHIPWRECK_STUMP),
	MERCENARY("Mercenary", 73, 195, ObjectID.SAILING_MERCENARY_SHIPWRECK, ObjectID.SAILING_MERCENARY_SHIPWRECK_STUMP),
	FREMENNIK("Fremennik", 80, 223, ObjectID.SAILING_FREMENNIK_SHIPWRECK, ObjectID.SAILING_FREMENNIK_SHIPWRECK_STUMP),
	MERCHANT("Merchant", 87, 240, ObjectID.SAILING_MERCHANT_SHIPWRECK, ObjectID.SAILING_MERCHANT_SHIPWRECK_STUMP);

	private final String displayName;
	private final int sailingLevel;
	/** Longest a wreck stays up once first salvaged. */
	private final int lifetimeSeconds;
	private final int activeObjectId;
	private final int sunkObjectId;

	static ShipwreckType fromObjectId(int objectId)
	{
		for (ShipwreckType type : values())
		{
			if (type.activeObjectId == objectId || type.sunkObjectId == objectId)
			{
				return type;
			}
		}
		return null;
	}
}
