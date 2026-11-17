package com.toadfx.gotrcoach.coach;

import java.util.ArrayList;
import java.util.List;

/** Best-ever rounds, saved per character. Late joins don't count. */
public class PersonalBests
{
	public int mostEnergy;
	public int mostXp;
	/** Fastest round the Great Guardian was charged, in seconds (0 = none yet). */
	public int fastestWin;
	public int bestScore = -1;
	public String bestGrade = "";

	/** Checks a finished round and returns what it beat, e.g. "most points (9.4)". */
	public List<String> update(GameRecord r)
	{
		List<String> beaten = new ArrayList<>();
		if (r == null || r.joinedLate)
		{
			return beaten;
		}
		if (r.totalEnergy() > mostEnergy)
		{
			if (mostEnergy > 0)
			{
				beaten.add(String.format("most points (%.1f)", r.points()));
			}
			mostEnergy = r.totalEnergy();
		}
		if (r.xpGained > mostXp)
		{
			if (mostXp > 0)
			{
				beaten.add(String.format("most XP (%,d)", r.xpGained));
			}
			mostXp = r.xpGained;
		}
		if (r.won && r.gameSeconds > 60 && (fastestWin == 0 || r.gameSeconds < fastestWin))
		{
			if (fastestWin > 0)
			{
				beaten.add("fastest round (" + r.gameSeconds / 60 + ":" + String.format("%02d", r.gameSeconds % 60) + ")");
			}
			fastestWin = r.gameSeconds;
		}
		if (r.score > bestScore)
		{
			if (bestScore >= 0)
			{
				beaten.add("best grade (" + r.grade + " " + r.score + ")");
			}
			bestScore = r.score;
			bestGrade = r.grade;
		}
		return beaten;
	}

	/** Seeds bests from saved history, for players who had rounds before bests existed. */
	public void seed(List<GameRecord> history)
	{
		for (GameRecord r : history)
		{
			update(r);
		}
	}

	public boolean isEmpty()
	{
		return mostEnergy == 0 && mostXp == 0 && fastestWin == 0 && bestScore < 0;
	}

	public String serialize()
	{
		return mostEnergy + "," + mostXp + "," + fastestWin + "," + bestScore + "," + bestGrade;
	}

	public static PersonalBests parse(String s)
	{
		PersonalBests b = new PersonalBests();
		if (s == null || s.isEmpty())
		{
			return b;
		}
		try
		{
			String[] p = s.split(",", -1);
			b.mostEnergy = Integer.parseInt(p[0]);
			b.mostXp = Integer.parseInt(p[1]);
			b.fastestWin = Integer.parseInt(p[2]);
			b.bestScore = Integer.parseInt(p[3]);
			b.bestGrade = p.length > 4 ? p[4] : "";
		}
		catch (RuntimeException e)
		{
			return new PersonalBests();
		}
		return b;
	}
}
