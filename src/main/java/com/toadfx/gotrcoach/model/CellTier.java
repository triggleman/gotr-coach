package com.toadfx.gotrcoach.model;

import java.awt.Color;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

/**
 * Cell tiers. Numbers are from the OSRS Wiki Guardians of the Rift page:
 * energy for strengthening a barrier, recharging it, and creating a guardian.
 */
public enum CellTier
{
	WEAK("Weak", 1, ItemID.GOTR_CELL_TIER1, ObjectID.GOTR_CELL_TILE_TIER1, new Color(230, 230, 230), 0, 2, 6),
	MEDIUM("Medium", 2, ItemID.GOTR_CELL_TIER2, ObjectID.GOTR_CELL_TILE_TIER2, new Color(70, 140, 255), 7, 5, 15),
	STRONG("Strong", 3, ItemID.GOTR_CELL_TIER3, ObjectID.GOTR_CELL_TILE_TIER3, new Color(60, 220, 90), 13, 9, 27),
	OVERCHARGED("Overcharged", 4, ItemID.GOTR_CELL_TIER4, ObjectID.GOTR_CELL_TILE_TIER4, new Color(255, 70, 70), 22, 15, 45);

	private final String label;
	private final int rank;
	private final int itemId;
	private final int tileObjectId;
	private final Color color;
	private final int strengthenEnergy;
	private final int rechargeEnergy;
	private final int guardianEnergy;

	CellTier(String label, int rank, int itemId, int tileObjectId, Color color,
		int strengthenEnergy, int rechargeEnergy, int guardianEnergy)
	{
		this.label = label;
		this.rank = rank;
		this.itemId = itemId;
		this.tileObjectId = tileObjectId;
		this.color = color;
		this.strengthenEnergy = strengthenEnergy;
		this.rechargeEnergy = rechargeEnergy;
		this.guardianEnergy = guardianEnergy;
	}

	public String getLabel()
	{
		return label;
	}

	public int getRank()
	{
		return rank;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getTileObjectId()
	{
		return tileObjectId;
	}

	public Color getColor()
	{
		return color;
	}

	public int getStrengthenEnergy()
	{
		return strengthenEnergy;
	}

	public int getRechargeEnergy()
	{
		return rechargeEnergy;
	}

	public int getGuardianEnergy()
	{
		return guardianEnergy;
	}

	/** Strong and overcharged cells make guardians worth a guardian slot. */
	public boolean isGuardianWorthy()
	{
		return rank >= STRONG.rank;
	}

	public static CellTier fromItem(int itemId)
	{
		for (CellTier tier : values())
		{
			if (tier.itemId == itemId)
			{
				return tier;
			}
		}
		return null;
	}

	public static CellTier fromTileObject(int objectId)
	{
		for (CellTier tier : values())
		{
			if (tier.tileObjectId == objectId)
			{
				return tier;
			}
		}
		return null;
	}
}
