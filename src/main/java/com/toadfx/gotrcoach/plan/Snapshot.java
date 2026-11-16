package com.toadfx.gotrcoach.plan;

import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.Location;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the planner needs, as plain values. The plugin fills one of these every game tick;
 * tests build them by hand. No RuneLite types live here, so the planner can be unit tested.
 */
public class Snapshot
{
	public GamePhase phase = GamePhase.IDLE;
	public Location location = Location.OUTSIDE;
	/** The altar whose room the player is standing in, if any. */
	public Altar altarRoom;

	/** Seconds since the rift opened (0 before). */
	public int gameSeconds;
	/** Seconds until the rift opens, when known (-1 otherwise). */
	public int countdownSeconds = -1;
	/** During mining: seconds until the altars open. */
	public int secondsUntilAltars = -1;
	/** During crafting: ticks until the active altars rotate. */
	public int altarTicksLeft = -1;
	/** Great Guardian power, 0-100. */
	public int powerPercent;

	public Altar activeElemental;
	public Altar activeCatalytic;

	public boolean portalOpen;
	public int portalTicksLeft = -1;
	/** Seconds since the last huge-remains portal spawned (-1 if none this game). */
	public int secondsSincePortal = -1;

	public int fragments;
	public int essence;
	public int essenceInPouches;
	public int pouchCapacity;
	public boolean hasPouches;
	public boolean pouchNeedsRepair;
	public int freeSlots = 28;
	public int elementalStones;
	public int catalyticStones;
	/** Stones that give 3 energy rather than 2. */
	public int polyStones;
	public CellTier chargedCell;
	public int unchargedCells;
	public boolean hasChisel;
	/** Inventory slots holding runes we crafted (stackable, so slots not runes). */
	public int runeSlots;

	public int energyElemental;
	public int energyCatalytic;
	public int bankedElemental = -1;
	public int bankedCatalytic = -1;
	public boolean yewLantern;

	/** Altar trips made this game. */
	public int altarTrips;
	/** True once the opening craft has started this game (so mining does not resume by mistake). */
	public boolean openingCraftStarted;

	public int guardians;
	public int guardiansMax = 10;

	public int runecraftLevel = 99;
	public int agilityLevel = 99;
	public Set<Altar> questLocked = EnumSet.noneOf(Altar.class);
	/** Portal talismans carried: each opens its guardian even when it isn't active. */
	public Set<Altar> talismans = EnumSet.noneOf(Altar.class);

	public boolean repairableTile;
	public boolean emptyTile;
	/** Lowest barrier health as a percent, -1 when unknown or no barriers. */
	public int lowestBarrierPercent = -1;

	/** Walking ticks from the player to each altar portal, when known. */
	public Map<Altar, Integer> altarTravelTicks = new EnumMap<>(Altar.class);
	/** Walking ticks to the huge remains portal, -1 when unknown. */
	public int portalTravelTicks = -1;

	public int stones()
	{
		return elementalStones + catalyticStones;
	}

	public int essenceCarried()
	{
		return essence + essenceInPouches;
	}

	/** Room left for essence across inventory and pouches. */
	public int essenceRoom()
	{
		return freeSlots + Math.max(0, pouchCapacity - essenceInPouches);
	}

	public boolean inGame()
	{
		return phase == GamePhase.MINING || phase == GamePhase.CRAFTING || phase == GamePhase.CLOSING;
	}

	public boolean canEnter(Altar altar)
	{
		return altar != null && runecraftLevel >= altar.getLevel() && !questLocked.contains(altar);
	}
}
