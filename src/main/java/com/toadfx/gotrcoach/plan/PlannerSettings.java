package com.toadfx.gotrcoach.plan;

/** Planner tuning, copied from the config each tick so the planner stays pure. */
public class PlannerSettings
{
	public Strategy strategy = Strategy.TEAM;
	public AltarMode altarMode = AltarMode.EVEN_POINTS;
	/** 0 means use the strategy default. */
	public int openingFragments;
	/** -1 means use the strategy default. */
	public int craftLeadSeconds = -1;
	/** On mass worlds, chisel only this much essence for the first altar trip (wiki: 30). */
	public int massFirstTripEssence = 30;
	/** Smallest load worth an altar trip. */
	public int minEssenceForAltar = 10;
	/** Guardians you want up before you put cells into barriers instead. */
	public int guardiansWanted = 4;
	/** Energy difference before balance overrides the altar mode. */
	public int maxImbalance = 200;
	public boolean balanceWithBanked = true;
	/** Essence room needed to make the huge-remains portal worth taking. */
	public int portalMinRoom = 20;
	/** Portal first: drop everything else (stones, cells, crafting) when a portal is open and reachable. */
	public boolean portalAlways = true;
	/** Don't start an altar trip when a portal is due within portalSoonSeconds (you'd be stuck in the altar). */
	public boolean holdForPortal = true;
	public int portalSoonSeconds = 15;
	public boolean depositRunes = false;
	/** Deposit runes once free slots fall below this. */
	public int depositBelowFreeSlots = 24;
	public int unchargedCellsWanted = 10;
	/** Wiki grace-portal trick: hold the last stones until 95s after the last portal. */
	public boolean graceHold = true;

	public int openingFragments()
	{
		return openingFragments > 0 ? openingFragments : strategy.defaultOpeningFragments();
	}

	public int craftLeadSeconds()
	{
		return craftLeadSeconds >= 0 ? craftLeadSeconds : strategy.defaultCraftLeadSeconds();
	}
}
