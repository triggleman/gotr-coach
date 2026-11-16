package com.toadfx.gotrcoach.coach;

import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Strategy;

/**
 * Grades a finished round against the OSRS Wiki timelines and gives the most useful tips.
 * Benchmarks: the team timeline starts crafting at 85s and uses the altar at the 135s rotation;
 * mass worlds mine longer (165-170 fragments); solo starts crafting at 80s. The wiki says the
 * team and mass strategies give 8+ points a round and solo gives 10+.
 */
public final class Grader
{
	private static final int ALTARS_OPEN = 120;

	private Grader()
	{
	}

	/** Game second the opening craft should start. */
	public static int craftStartTarget(Strategy s)
	{
		switch (s)
		{
			case MASS:
				return 105;
			case SOLO:
				return 80;
			default:
				return 85;
		}
	}

	/** Combined energy a good round reaches (100 energy = 1 point). */
	public static int energyTarget(Strategy s)
	{
		return s == Strategy.SOLO ? 1000 : 800;
	}

	public static void grade(GameRecord r)
	{
		r.tips.clear();
		int score = 100;

		if (r.joinedLate)
		{
			r.score = -1;
			r.grade = "-";
			r.tips.add("Joined part-way through, so this round isn't graded.");
			return;
		}

		// 1. Energy, the thing that actually becomes points.
		int target = energyTarget(r.strategy);
		int cap = EnergyMath.TOTAL_CAP;
		int energy = r.totalEnergy();
		if (r.hitCap || energy >= cap)
		{
			r.tips.add("Hit the energy cap. Nothing left on the table.");
		}
		else if (energy < target)
		{
			int short_ = target - energy;
			score -= Math.min(35, short_ / 20);
			r.tips.add(String.format("%.1f points, about %.1f short of a good %s round.",
				energy / 100.0, short_ / 100.0, r.strategy.toString().toLowerCase()));
		}
		if (energy < EnergyMath.XP_THRESHOLD)
		{
			score -= 10;
			r.tips.add("Under 300 energy: no end-of-round Runecraft XP this time.");
		}

		// 2. Opening: when crafting began.
		if (r.craftStartSecond >= 0)
		{
			int delta = r.craftStartSecond - craftStartTarget(r.strategy);
			if (delta > 10)
			{
				score -= Math.min(15, delta / 2);
				r.tips.add("Started crafting at " + r.craftStartSecond + "s, " + delta + "s late (aim for "
					+ craftStartTarget(r.strategy) + "s). Stop mining sooner.");
			}
			else if (delta < -20 && r.fragmentsAtCraftStart < 100)
			{
				score -= 5;
				r.tips.add("Stopped mining at " + r.craftStartSecond + "s with only " + r.fragmentsAtCraftStart
					+ " fragments. Mine a bit longer.");
			}
		}
		else
		{
			score -= 20;
			r.tips.add("No essence crafted this round.");
		}

		// 3. First altar: the wiki uses the altar at the 135s rotation at the latest.
		if (r.firstAltarSecond >= 0)
		{
			int late = r.firstAltarSecond - (ALTARS_OPEN + 15);
			if (late > 10)
			{
				score -= Math.min(10, late / 3);
				r.tips.add("First altar at " + r.firstAltarSecond + "s. Be at a guardian when they open at 120s.");
			}
		}

		// 4. Portals: each huge-remains portal is a free full load of essence.
		int missed = Math.max(0, r.portalsSpawned - r.portalsEntered);
		if (missed > 0)
		{
			score -= Math.min(20, missed * 7);
			r.tips.add("Missed " + missed + " of " + r.portalsSpawned + " portals. Each one is a free full load.");
		}

		// 5. Idle time.
		if (r.idleSeconds > 20)
		{
			score -= Math.min(15, (r.idleSeconds - 20) / 4);
			r.tips.add("About " + r.idleSeconds + "s standing still in the round.");
		}

		// 6. Balance between the two point types (reward searches use one of each).
		int diff = Math.abs(r.energyElemental - r.energyCatalytic);
		if (diff > 300 && energy > 400)
		{
			score -= 5;
			String low = r.energyElemental < r.energyCatalytic ? "elemental" : "catalytic";
			r.tips.add("Points were lopsided. Lean " + low + " next round, since each search costs one of each.");
		}

		if (!r.won)
		{
			r.tips.add(0, "The Great Guardian fell. Barriers and guardians matter more on this world.");
		}

		r.score = Math.max(0, Math.min(100, score));
		r.grade = letter(r.score);
		if (r.tips.isEmpty())
		{
			r.tips.add("Clean round. Right on the wiki timeline.");
		}
	}

	public static String letter(int score)
	{
		if (score >= 95)
		{
			return "S";
		}
		if (score >= 85)
		{
			return "A";
		}
		if (score >= 70)
		{
			return "B";
		}
		if (score >= 55)
		{
			return "C";
		}
		return "D";
	}
}
