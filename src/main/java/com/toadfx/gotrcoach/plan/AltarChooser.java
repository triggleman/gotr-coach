package com.toadfx.gotrcoach.plan;

import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.model.Altar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks which altar to use: the two open altars, plus any altar you hold a portal talisman for
 * (a talisman opens its guardian even when it is inactive). Pure logic so it can be tested.
 */
public final class AltarChooser
{
	/** Result of a choice: the altar to use, the backup, and a short reason for the panel. */
	public static final class Choice
	{
		public final Altar primary;
		public final Altar secondary;
		public final String reason;
		/** True when the primary altar is reached with a portal talisman rather than because it's open. */
		public final boolean viaTalisman;

		Choice(Altar primary, Altar secondary, String reason, boolean viaTalisman)
		{
			this.primary = primary;
			this.secondary = secondary;
			this.reason = reason;
			this.viaTalisman = viaTalisman;
		}
	}

	private AltarChooser()
	{
	}

	/** True when the player can reach the altar before it rotates (or when we cannot tell). */
	public static boolean reachable(Snapshot s, Altar altar)
	{
		if (isTalismanOnly(s, altar))
		{
			return true;
		}
		Integer travel = s.altarTravelTicks.get(altar);
		if (travel == null || travel < 0 || s.altarTicksLeft < 0)
		{
			return true;
		}
		return travel <= s.altarTicksLeft;
	}

	/** Reached only through a talisman (it isn't one of the open altars). */
	public static boolean isTalismanOnly(Snapshot s, Altar altar)
	{
		return altar != s.activeElemental && altar != s.activeCatalytic && s.talismans.contains(altar);
	}

	/** Every altar the player could use right now: open ones plus talisman ones. */
	public static List<Altar> usable(Snapshot s)
	{
		List<Altar> out = new ArrayList<>(4);
		List<Altar> candidates = new ArrayList<>(4);
		candidates.add(s.activeElemental);
		candidates.add(s.activeCatalytic);
		candidates.addAll(s.talismans);
		for (Altar a : candidates)
		{
			if (a == null || out.contains(a) || !s.canEnter(a))
			{
				continue;
			}
			if (EnergyMath.isCapped(a.getAlignment(), s.energyElemental, s.energyCatalytic, s.yewLantern))
			{
				continue;
			}
			out.add(a);
		}
		return out;
	}

	public static Choice choose(Snapshot s, PlannerSettings settings)
	{
		List<Altar> usable = usable(s);
		if (usable.isEmpty())
		{
			return null;
		}
		List<Altar> pool = new ArrayList<>(usable.size());
		for (Altar a : usable)
		{
			if (reachable(s, a))
			{
				pool.add(a);
			}
		}
		boolean noneReachable = pool.isEmpty();
		if (noneReachable)
		{
			// Nothing reachable before rotation: still name the best usable one so the player can wait by it.
			pool = usable;
		}

		long pointsE = s.energyElemental;
		long pointsC = s.energyCatalytic;
		if (settings.balanceWithBanked && s.bankedElemental >= 0 && s.bankedCatalytic >= 0)
		{
			pointsE += s.bankedElemental * 100L;
			pointsC += s.bankedCatalytic * 100L;
		}
		Alignment lower = pointsE <= pointsC ? Alignment.ELEMENTAL : Alignment.CATALYTIC;
		AltarMode mode = settings.altarMode;

		List<Altar> ranked = new ArrayList<>(pool);
		ranked.sort(Comparator.comparingDouble((Altar a) -> score(a, mode, lower)).reversed());
		Altar pick = ranked.get(0);
		String why = reason(pick, mode, lower, pool.size());

		// Balance override: too far apart, so take the best altar of the weaker type if there is one.
		long diff = Math.abs(pointsE - pointsC);
		boolean onlyMode = mode == AltarMode.ELEMENTAL_ONLY || mode == AltarMode.CATALYTIC_ONLY;
		if (!onlyMode && pick.getAlignment() != lower && settings.maxImbalance > 0 && diff > settings.maxImbalance)
		{
			for (Altar a : ranked)
			{
				if (a.getAlignment() == lower)
				{
					pick = a;
					why = "rebalancing, fewer " + lower.getLabel().toLowerCase() + " points";
					break;
				}
			}
		}

		Altar secondary = null;
		for (Altar a : ranked)
		{
			if (a != pick)
			{
				secondary = a;
				break;
			}
		}
		if (secondary == null && usable.size() > 1)
		{
			for (Altar a : usable)
			{
				if (a != pick)
				{
					secondary = a;
					why = "the other altar rotates before you can reach it";
					break;
				}
			}
		}
		if (pool.size() == 1 && usable.size() == 1 && !noneReachable)
		{
			why = "only usable altar";
		}

		boolean viaTalisman = isTalismanOnly(s, pick);
		if (viaTalisman)
		{
			why = why + ", use your " + pick.getLabel().toLowerCase() + " talisman";
		}
		return new Choice(pick, secondary, why, viaTalisman);
	}

	/** Higher is better. Ties go to the weaker point type, then more XP. */
	static double score(Altar a, AltarMode mode, Alignment lower)
	{
		double weaker = a.getAlignment() == lower ? 1 : 0;
		double xp = a.getXpPerEssence() / 100.0;
		switch (mode)
		{
			case ELEMENTAL_ONLY:
				return (a.getAlignment() == Alignment.ELEMENTAL ? 100 : 0) + weaker + xp;
			case CATALYTIC_ONLY:
				return (a.getAlignment() == Alignment.CATALYTIC ? 100 : 0) + weaker + xp;
			case BALANCED:
				return weaker * 10 + xp;
			case BEST_CELL:
				return a.getCellTier().getRank() * 10 + weaker + xp;
			case EXPERIENCE:
				return a.getXpPerEssence() * 10 + weaker;
			case EVEN_POINTS:
			default:
				// Wiki: Blood and Death first, then Fire and Earth, then the type you have fewer points in.
				double tier = (a == Altar.BLOOD || a == Altar.DEATH) ? 3 : (a == Altar.FIRE || a == Altar.EARTH) ? 2 : 0;
				return tier * 10 + weaker + xp;
		}
	}

	private static String reason(Altar pick, AltarMode mode, Alignment lower, int options)
	{
		String fewer = "fewer " + lower.getLabel().toLowerCase() + " points";
		switch (mode)
		{
			case ELEMENTAL_ONLY:
				return "elemental only";
			case CATALYTIC_ONLY:
				return "catalytic only";
			case BALANCED:
				return fewer;
			case BEST_CELL:
				return pick.getCellTier().getLabel().toLowerCase() + " cell";
			case EXPERIENCE:
				return "more XP per essence";
			case EVEN_POINTS:
			default:
				if (pick == Altar.BLOOD || pick == Altar.DEATH || pick == Altar.FIRE || pick == Altar.EARTH)
				{
					return pick.getLabel() + " first (wiki)";
				}
				return options > 1 ? fewer : "only usable altar";
		}
	}
}
