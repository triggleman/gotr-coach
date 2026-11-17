package com.toadfx.gotrcoach.state;

import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.model.PouchType;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;

/** What the player is carrying, counted from the inventory container. */
public final class Inventory
{
	public int fragments;
	public int essence;
	public int elementalStones;
	public int catalyticStones;
	public int polyStones;
	public CellTier chargedCell;
	public int unchargedCells;
	public boolean chisel;
	/** A pickaxe in the inventory or worn. */
	public boolean pickaxe;
	public int runeSlots;
	public int freeSlots = 28;
	/** Pouches carried, and whether each is degraded. */
	public final Map<PouchType, Boolean> pouches = new EnumMap<>(PouchType.class);
	/** Portal talismans carried. */
	public final java.util.Set<Altar> talismans = java.util.EnumSet.noneOf(Altar.class);

	/** Every pickaxe that can mine guardian remains (worn or carried). */
	static final int[] PICKAXES = {
		ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_PICKAXE, ItemID.BLACK_PICKAXE, ItemID.MITHRIL_PICKAXE,
		ItemID.ADAMANT_PICKAXE, ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE, ItemID.DRAGON_PICKAXE_PRETTY,
		ItemID.ZALCANO_PICKAXE, ItemID.INFERNAL_PICKAXE, ItemID.INFERNAL_PICKAXE_EMPTY, ItemID._3A_PICKAXE,
		ItemID.TRAIL_GILDED_PICKAXE, ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE,
		ItemID.TRAILBLAZER_PICKAXE, ItemID.TRAILBLAZER_PICKAXE_EMPTY, ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL,
		ItemID.TRAILBLAZER_RELOADED_PICKAXE, ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY,
		ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL
	};

	public static boolean isPickaxe(int id)
	{
		for (int p : PICKAXES)
		{
			if (p == id)
			{
				return true;
			}
		}
		return false;
	}

	private static final int[] COMBINATION_RUNES = {
		ItemID.MISTRUNE, ItemID.DUSTRUNE, ItemID.MUDRUNE, ItemID.SMOKERUNE, ItemID.STEAMRUNE, ItemID.LAVARUNE
	};

	public static Inventory read(Item[] items)
	{
		Inventory inv = new Inventory();
		if (items == null)
		{
			return inv;
		}
		int used = 0;
		for (Item item : items)
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			used++;
			int id = item.getId();
			int qty = item.getQuantity();
			if (id == ItemID.GOTR_GUARDIAN_FRAGMENT)
			{
				inv.fragments += qty;
			}
			else if (id == ItemID.GOTR_GUARDIAN_ESSENCE)
			{
				inv.essence += qty;
			}
			else if (id == ItemID.GOTR_GUARDIAN_STONE_ELEMENTAL)
			{
				inv.elementalStones += qty;
			}
			else if (id == ItemID.GOTR_GUARDIAN_STONE_CATALYTIC)
			{
				inv.catalyticStones += qty;
			}
			else if (id == ItemID.GOTR_GUARDIAN_STONE_POLYELEMENTAL)
			{
				inv.elementalStones += qty;
				inv.polyStones += qty;
			}
			else if (id == ItemID.GOTR_GUARDIAN_STONE_POLYCATALYTIC)
			{
				inv.catalyticStones += qty;
				inv.polyStones += qty;
			}
			else if (id == ItemID.GOTR_CELL_UNCHARGED)
			{
				inv.unchargedCells += qty;
			}
			else if (id == ItemID.CHISEL)
			{
				inv.chisel = true;
			}
			else if (isPickaxe(id))
			{
				inv.pickaxe = true;
			}
			else if (CellTier.fromItem(id) != null)
			{
				CellTier tier = CellTier.fromItem(id);
				if (inv.chargedCell == null || tier.getRank() > inv.chargedCell.getRank())
				{
					inv.chargedCell = tier;
				}
			}
			else if (PouchType.fromItem(id) != null)
			{
				PouchType type = PouchType.fromItem(id);
				inv.pouches.put(type, type.isDegraded(id));
			}
			else if (Altar.fromTalisman(id) != null)
			{
				inv.talismans.add(Altar.fromTalisman(id));
			}
			else if (isCraftedRune(id))
			{
				inv.runeSlots++;
			}
		}
		inv.freeSlots = Math.max(0, 28 - used);
		return inv;
	}

	static boolean isCraftedRune(int id)
	{
		if (Altar.isRune(id))
		{
			return true;
		}
		for (int r : COMBINATION_RUNES)
		{
			if (r == id)
			{
				return true;
			}
		}
		return false;
	}

	public boolean anyPouchDegraded()
	{
		return pouches.containsValue(Boolean.TRUE);
	}
}
