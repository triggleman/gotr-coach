package com.toadfx.gotrcoach.coach;

import com.toadfx.gotrcoach.plan.Strategy;
import java.util.ArrayList;
import java.util.List;

/** What happened in one round, plus its grade once the round is over. */
public class GameRecord
{
	public long endedAtMillis;
	public Strategy strategy = Strategy.TEAM;
	public boolean won;
	public boolean joinedLate;
	public int gameSeconds;
	/** Game second the first essence was crafted, -1 if never. */
	public int craftStartSecond = -1;
	/** Fragments held when the opening craft began. */
	public int fragmentsAtCraftStart;
	/** Game second of the first altar entry, -1 if never. */
	public int firstAltarSecond = -1;
	public int altarTrips;
	public int portalsSpawned;
	public int portalsEntered;
	public int idleSeconds;
	public int energyElemental;
	public int energyCatalytic;
	public int barrierRepairs;
	public boolean hitCap;
	/** Runecraft XP gained this round, including the end-of-round reward. */
	public int xpGained;

	// Filled in by the grader.
	public int score;
	public String grade = "";
	public final List<String> tips = new ArrayList<>();

	public int totalEnergy()
	{
		return energyElemental + energyCatalytic;
	}

	public double points()
	{
		return totalEnergy() / 100.0;
	}

	/** One line for saving. Tips are recalculated on load, so they are not saved. */
	public String serialize()
	{
		return endedAtMillis + "," + strategy.name() + "," + (won ? 1 : 0) + "," + gameSeconds + ","
			+ craftStartSecond + "," + fragmentsAtCraftStart + "," + firstAltarSecond + "," + altarTrips + ","
			+ portalsSpawned + "," + portalsEntered + "," + idleSeconds + "," + energyElemental + ","
			+ energyCatalytic + "," + score + "," + grade + "," + xpGained;
	}

	public static GameRecord parse(String line)
	{
		try
		{
			String[] p = line.split(",");
			if (p.length < 15)
			{
				return null;
			}
			GameRecord r = new GameRecord();
			r.endedAtMillis = Long.parseLong(p[0]);
			r.strategy = Strategy.valueOf(p[1]);
			r.won = "1".equals(p[2]);
			r.gameSeconds = Integer.parseInt(p[3]);
			r.craftStartSecond = Integer.parseInt(p[4]);
			r.fragmentsAtCraftStart = Integer.parseInt(p[5]);
			r.firstAltarSecond = Integer.parseInt(p[6]);
			r.altarTrips = Integer.parseInt(p[7]);
			r.portalsSpawned = Integer.parseInt(p[8]);
			r.portalsEntered = Integer.parseInt(p[9]);
			r.idleSeconds = Integer.parseInt(p[10]);
			r.energyElemental = Integer.parseInt(p[11]);
			r.energyCatalytic = Integer.parseInt(p[12]);
			r.score = Integer.parseInt(p[13]);
			r.grade = p[14];
			if (p.length > 15)
			{
				r.xpGained = Integer.parseInt(p[15]);
			}
			return r;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
