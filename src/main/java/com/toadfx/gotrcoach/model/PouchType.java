package com.toadfx.gotrcoach.model;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Essence pouches. The varbit holds how much essence the pouch has in it. */
public enum PouchType
{
	SMALL("Small", ItemID.RCU_POUCH_SMALL, -1, VarbitID.SMALL_ESSENCE_POUCH, 3),
	MEDIUM("Medium", ItemID.RCU_POUCH_MEDIUM, ItemID.RCU_POUCH_MEDIUM_DEGRADE, VarbitID.MEDIUM_ESSENCE_POUCH, 6),
	LARGE("Large", ItemID.RCU_POUCH_LARGE, ItemID.RCU_POUCH_LARGE_DEGRADE, VarbitID.LARGE_ESSENCE_POUCH, 9),
	GIANT("Giant", ItemID.RCU_POUCH_GIANT, ItemID.RCU_POUCH_GIANT_DEGRADE, VarbitID.GIANT_ESSENCE_POUCH, 12),
	COLOSSAL("Colossal", ItemID.RCU_POUCH_COLOSSAL, ItemID.RCU_POUCH_COLOSSAL_DEGRADE, VarbitID.COLOSSAL_ESSENCE_POUCH, 40);

	private final String label;
	private final int itemId;
	private final int degradedItemId;
	private final int varbit;
	private final int baseCapacity;

	PouchType(String label, int itemId, int degradedItemId, int varbit, int baseCapacity)
	{
		this.label = label;
		this.itemId = itemId;
		this.degradedItemId = degradedItemId;
		this.varbit = varbit;
		this.baseCapacity = baseCapacity;
	}

	public String getLabel()
	{
		return label;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getDegradedItemId()
	{
		return degradedItemId;
	}

	public int getVarbit()
	{
		return varbit;
	}

	/**
	 * Capacity of a pouch in good repair. The colossal pouch grows with Runecraft level
	 * (8 / 16 / 27 / 40 at 25 / 50 / 75 / 85).
	 */
	public int capacity(int runecraftLevel)
	{
		if (this != COLOSSAL)
		{
			return baseCapacity;
		}
		if (runecraftLevel >= 85)
		{
			return 40;
		}
		if (runecraftLevel >= 75)
		{
			return 27;
		}
		if (runecraftLevel >= 50)
		{
			return 16;
		}
		return 8;
	}

	public static PouchType fromItem(int itemId)
	{
		for (PouchType type : values())
		{
			if (type.itemId == itemId || (type.degradedItemId != -1 && type.degradedItemId == itemId))
			{
				return type;
			}
		}
		return null;
	}

	public boolean isDegraded(int itemId)
	{
		return degradedItemId != -1 && degradedItemId == itemId;
	}
}
