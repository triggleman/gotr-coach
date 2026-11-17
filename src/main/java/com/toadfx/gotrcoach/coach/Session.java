package com.toadfx.gotrcoach.coach;

/** Stats since the client started (or since the player reset them). */
public class Session
{
	private long startMillis;
	private boolean started;
	private int rounds;
	private int xp;
	private int energy;
	private int scoreTotal;
	private int graded;
	private int wins;

	public void reset()
	{
		startMillis = 0;
		started = false;
		rounds = 0;
		xp = 0;
		energy = 0;
		scoreTotal = 0;
		graded = 0;
		wins = 0;
	}

	/** Called when a round starts, so the session clock begins with the first round. */
	public void roundStarted(long nowMillis)
	{
		if (!started)
		{
			startMillis = nowMillis;
			started = true;
		}
	}

	public void add(GameRecord r)
	{
		if (r == null)
		{
			return;
		}
		rounds++;
		xp += Math.max(0, r.xpGained);
		energy += r.totalEnergy();
		if (r.won)
		{
			wins++;
		}
		if (r.score >= 0)
		{
			scoreTotal += r.score;
			graded++;
		}
	}

	public boolean isEmpty()
	{
		return rounds == 0;
	}

	public int getRounds()
	{
		return rounds;
	}

	public int getXp()
	{
		return xp;
	}

	public double getPoints()
	{
		return energy / 100.0;
	}

	public int getWins()
	{
		return wins;
	}

	public long elapsedMillis(long nowMillis)
	{
		return !started ? 0 : Math.max(0, nowMillis - startMillis);
	}

	/** Per-hour rate; 0 until at least a minute has passed. */
	private double perHour(double amount, long nowMillis)
	{
		long ms = elapsedMillis(nowMillis);
		return ms < 60_000 ? 0 : amount * 3_600_000.0 / ms;
	}

	public double xpPerHour(long nowMillis)
	{
		return perHour(xp, nowMillis);
	}

	public double pointsPerHour(long nowMillis)
	{
		return perHour(getPoints(), nowMillis);
	}

	public double roundsPerHour(long nowMillis)
	{
		return perHour(rounds, nowMillis);
	}

	public double averageScore()
	{
		return graded == 0 ? -1 : scoreTotal / (double) graded;
	}
}
