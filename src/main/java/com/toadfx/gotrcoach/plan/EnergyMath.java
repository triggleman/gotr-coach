package com.toadfx.gotrcoach.plan;

import com.toadfx.gotrcoach.model.Alignment;

/**
 * Energy and point rules from the OSRS Wiki:
 * 1 point per 100 energy (the remainder is a % chance of one more),
 * at most 1,000 energy of each type and 1,200 in total per game (1,320 with yew logs in the lantern),
 * and at least 300 total energy for the end-of-game XP.
 */
public final class EnergyMath
{
	public static final int TYPE_CAP = 1000;
	public static final int TOTAL_CAP = 1200;
	public static final int TOTAL_CAP_YEW = 1320;
	public static final int XP_THRESHOLD = 300;
	public static final int ENERGY_PER_STONE = 2;
	public static final int ENERGY_PER_POLY_STONE = 3;

	private EnergyMath()
	{
	}

	public static int totalCap(boolean yewLantern)
	{
		return yewLantern ? TOTAL_CAP_YEW : TOTAL_CAP;
	}

	/** How much more energy of this type can still count this game. */
	public static int room(Alignment type, int elemental, int catalytic, boolean yewLantern)
	{
		int mine = type == Alignment.ELEMENTAL ? elemental : catalytic;
		int byType = TYPE_CAP - mine;
		int byTotal = totalCap(yewLantern) - (elemental + catalytic);
		return Math.max(0, Math.min(byType, byTotal));
	}

	public static boolean isCapped(Alignment type, int elemental, int catalytic, boolean yewLantern)
	{
		return room(type, elemental, catalytic, yewLantern) <= 0;
	}

	public static boolean isFullyCapped(int elemental, int catalytic, boolean yewLantern)
	{
		return isCapped(Alignment.ELEMENTAL, elemental, catalytic, yewLantern)
			&& isCapped(Alignment.CATALYTIC, elemental, catalytic, yewLantern);
	}

	/** Expected points from an amount of energy (whole points plus the chance of one more). */
	public static double expectedPoints(int energy)
	{
		return Math.max(0, energy) / 100.0;
	}

	/** Energy each type would have once the given stones are handed in, respecting the caps. */
	public static int[] afterHandIn(int elemental, int catalytic, int elementalStones, int catalyticStones, int polyStones, boolean yewLantern)
	{
		int e = elemental;
		int c = catalytic;
		// Treat poly stones as part of whichever type they were counted in; give them the extra energy first.
		int polyE = Math.min(polyStones, elementalStones);
		int polyC = Math.min(polyStones - polyE, catalyticStones);
		int addE = polyE * ENERGY_PER_POLY_STONE + (elementalStones - polyE) * ENERGY_PER_STONE;
		int addC = polyC * ENERGY_PER_POLY_STONE + (catalyticStones - polyC) * ENERGY_PER_STONE;
		int roomE = room(Alignment.ELEMENTAL, e, c, yewLantern);
		e += Math.min(addE, roomE);
		int roomC = room(Alignment.CATALYTIC, e, c, yewLantern);
		c += Math.min(addC, roomC);
		return new int[]{e, c};
	}

	/** Energy the stones would waste because a cap is already reached. */
	public static int wasted(int elemental, int catalytic, int elementalStones, int catalyticStones, int polyStones, boolean yewLantern)
	{
		int[] after = afterHandIn(elemental, catalytic, elementalStones, catalyticStones, polyStones, yewLantern);
		int polyE = Math.min(polyStones, elementalStones);
		int polyC = Math.min(polyStones - polyE, catalyticStones);
		int add = polyE * ENERGY_PER_POLY_STONE + (elementalStones - polyE) * ENERGY_PER_STONE
			+ polyC * ENERGY_PER_POLY_STONE + (catalyticStones - polyC) * ENERGY_PER_STONE;
		return add - ((after[0] - elemental) + (after[1] - catalytic));
	}
}
