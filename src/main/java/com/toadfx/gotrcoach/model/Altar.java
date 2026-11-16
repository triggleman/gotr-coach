package com.toadfx.gotrcoach.model;

import net.runelite.api.Quest;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

/**
 * The twelve runic altars reachable from the Temple of the Eye.
 * hudIndex is the position the GOTR HUD script uses for the active altar of each alignment.
 */
public enum Altar
{
	AIR("Air", Alignment.ELEMENTAL, 1, 1, CellTier.WEAK, 5.0,
		ObjectID.GOTR_PORTAL_AIR, ObjectID.AIR_ALTAR, ObjectID.AIRTEMPLE_EXIT_PORTAL, ItemID.AIRRUNE, ItemID.GOTR_PORTAL_TALISMAN_AIR, null),
	WATER("Water", Alignment.ELEMENTAL, 2, 5, CellTier.MEDIUM, 6.0,
		ObjectID.GOTR_PORTAL_WATER, ObjectID.WATER_ALTAR, ObjectID.WATERTEMPLE_EXIT_PORTAL, ItemID.WATERRUNE, ItemID.GOTR_PORTAL_TALISMAN_WATER, null),
	EARTH("Earth", Alignment.ELEMENTAL, 3, 9, CellTier.STRONG, 6.5,
		ObjectID.GOTR_PORTAL_EARTH, ObjectID.EARTH_ALTAR, ObjectID.EARTHTEMPLE_EXIT_PORTAL, ItemID.EARTHRUNE, ItemID.GOTR_PORTAL_TALISMAN_EARTH, null),
	FIRE("Fire", Alignment.ELEMENTAL, 4, 14, CellTier.OVERCHARGED, 7.0,
		ObjectID.GOTR_PORTAL_FIRE, ObjectID.FIRE_ALTAR, ObjectID.FIRETEMPLE_EXIT_PORTAL, ItemID.FIRERUNE, ItemID.GOTR_PORTAL_TALISMAN_FIRE, null),
	MIND("Mind", Alignment.CATALYTIC, 1, 2, CellTier.WEAK, 5.5,
		ObjectID.GOTR_PORTAL_MIND, ObjectID.MIND_ALTAR, ObjectID.MINDTEMPLE_EXIT_PORTAL, ItemID.MINDRUNE, ItemID.GOTR_PORTAL_TALISMAN_MIND, null),
	BODY("Body", Alignment.CATALYTIC, 2, 20, CellTier.WEAK, 7.5,
		ObjectID.GOTR_PORTAL_BODY, ObjectID.BODY_ALTAR, ObjectID.BODYTEMPLE_EXIT_PORTAL, ItemID.BODYRUNE, ItemID.GOTR_PORTAL_TALISMAN_BODY, null),
	COSMIC("Cosmic", Alignment.CATALYTIC, 3, 27, CellTier.MEDIUM, 8.0,
		ObjectID.GOTR_PORTAL_COSMIC, ObjectID.COSMIC_ALTAR, ObjectID.COSMICTEMPLE_EXIT_PORTAL, ItemID.COSMICRUNE, ItemID.GOTR_PORTAL_TALISMAN_COSMIC, Quest.LOST_CITY),
	CHAOS("Chaos", Alignment.CATALYTIC, 4, 35, CellTier.MEDIUM, 8.5,
		ObjectID.GOTR_PORTAL_CHAOS, ObjectID.CHAOS_ALTAR, ObjectID.CHAOSTEMPLE_EXIT_PORTAL, ItemID.CHAOSRUNE, ItemID.GOTR_PORTAL_TALISMAN_CHAOS, null),
	NATURE("Nature", Alignment.CATALYTIC, 5, 44, CellTier.STRONG, 9.0,
		ObjectID.GOTR_PORTAL_NATURE, ObjectID.NATURE_ALTAR, ObjectID.NATURETEMPLE_EXIT_PORTAL, ItemID.NATURERUNE, ItemID.GOTR_PORTAL_TALISMAN_NATURE, null),
	LAW("Law", Alignment.CATALYTIC, 6, 54, CellTier.STRONG, 9.5,
		ObjectID.GOTR_PORTAL_LAW, ObjectID.LAW_ALTAR, ObjectID.LAWTEMPLE_EXIT_PORTAL, ItemID.LAWRUNE, ItemID.GOTR_PORTAL_TALISMAN_LAW, Quest.TROLL_STRONGHOLD),
	DEATH("Death", Alignment.CATALYTIC, 7, 65, CellTier.OVERCHARGED, 10.0,
		ObjectID.GOTR_PORTAL_DEATH, ObjectID.DEATH_ALTAR, ObjectID.DEATHTEMPLE_EXIT_PORTAL, ItemID.DEATHRUNE, ItemID.GOTR_PORTAL_TALISMAN_DEATH, Quest.MOURNINGS_END_PART_II),
	BLOOD("Blood", Alignment.CATALYTIC, 8, 77, CellTier.OVERCHARGED, 10.5,
		ObjectID.GOTR_PORTAL_BLOOD, ObjectID.BLOOD_ALTAR, ObjectID.BLOODTEMPLE_EXIT_PORTAL, ItemID.BLOODRUNE, ItemID.GOTR_PORTAL_TALISMAN_BLOOD, Quest.SINS_OF_THE_FATHER);

	private final String label;
	private final Alignment alignment;
	private final int hudIndex;
	private final int level;
	private final CellTier cellTier;
	private final double xpPerEssence;
	private final int portalObjectId;
	private final int altarObjectId;
	private final int exitPortalObjectId;
	private final int runeItemId;
	private final int talismanItemId;
	private final Quest quest;

	Altar(String label, Alignment alignment, int hudIndex, int level, CellTier cellTier, double xpPerEssence,
		int portalObjectId, int altarObjectId, int exitPortalObjectId, int runeItemId, int talismanItemId, Quest quest)
	{
		this.label = label;
		this.alignment = alignment;
		this.hudIndex = hudIndex;
		this.level = level;
		this.cellTier = cellTier;
		this.xpPerEssence = xpPerEssence;
		this.portalObjectId = portalObjectId;
		this.altarObjectId = altarObjectId;
		this.exitPortalObjectId = exitPortalObjectId;
		this.runeItemId = runeItemId;
		this.talismanItemId = talismanItemId;
		this.quest = quest;
	}

	public String getLabel()
	{
		return label;
	}

	public Alignment getAlignment()
	{
		return alignment;
	}

	public int getHudIndex()
	{
		return hudIndex;
	}

	public int getLevel()
	{
		return level;
	}

	public CellTier getCellTier()
	{
		return cellTier;
	}

	public double getXpPerEssence()
	{
		return xpPerEssence;
	}

	public int getPortalObjectId()
	{
		return portalObjectId;
	}

	public int getAltarObjectId()
	{
		return altarObjectId;
	}

	public int getExitPortalObjectId()
	{
		return exitPortalObjectId;
	}

	public int getRuneItemId()
	{
		return runeItemId;
	}

	/** The portal talisman that opens this guardian even while it is inactive. */
	public int getTalismanItemId()
	{
		return talismanItemId;
	}

	public static Altar fromTalisman(int itemId)
	{
		for (Altar altar : values())
		{
			if (altar.talismanItemId == itemId)
			{
				return altar;
			}
		}
		return null;
	}

	/** The quest that must be finished to enter, or null. */
	public Quest getQuest()
	{
		return quest;
	}

	public static Altar fromHud(Alignment alignment, int index)
	{
		for (Altar altar : values())
		{
			if (altar.alignment == alignment && altar.hudIndex == index)
			{
				return altar;
			}
		}
		return null;
	}

	public static Altar fromPortal(int objectId)
	{
		for (Altar altar : values())
		{
			if (altar.portalObjectId == objectId)
			{
				return altar;
			}
		}
		return null;
	}

	public static Altar fromAltarObject(int objectId)
	{
		for (Altar altar : values())
		{
			if (altar.altarObjectId == objectId)
			{
				return altar;
			}
		}
		return null;
	}

	public static Altar fromExitPortal(int objectId)
	{
		for (Altar altar : values())
		{
			if (altar.exitPortalObjectId == objectId)
			{
				return altar;
			}
		}
		return null;
	}

	public static boolean isRune(int itemId)
	{
		for (Altar altar : values())
		{
			if (altar.runeItemId == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
