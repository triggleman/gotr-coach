package com.toadfx.gotrcoach.coach;

import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Strategy;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.Location;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Watches a round tick by tick and produces a graded {@link GameRecord} at the end. */
public class GameRecorder
{
	public static final int MAX_HISTORY = 50;

	private GameRecord current;
	private GameRecord last;
	private final Deque<GameRecord> history = new ArrayDeque<>();
	private Location lastLocation = Location.OUTSIDE;
	private int lastEssence;
	private int lastFragments;
	private int idleTicks;

	/** Ticks after a round ends during which XP still counts toward it (the end-of-round reward). */
	private static final int LATE_XP_TICKS = 10;
	private int startXp = -1;
	private int lateXpTicks;

	public void start(Strategy strategy, boolean joinedLate, int runecraftXp)
	{
		startXp = runecraftXp;
		lateXpTicks = 0;
		current = new GameRecord();
		current.strategy = strategy;
		current.joinedLate = joinedLate;
		idleTicks = 0;
		lastEssence = 0;
		lastFragments = 0;
		lastLocation = Location.TEMPLE;
	}

	/**
	 * @param idle true when the player is standing still with nothing animating
	 * @param shouldBeBusy true when the planner has a real job for the player (not waiting)
	 */
	public void tick(Snapshot s, boolean idle, boolean shouldBeBusy, int portalsSpawned)
	{
		if (current == null || !(s.phase == GamePhase.MINING || s.phase == GamePhase.CRAFTING))
		{
			lastLocation = s.location;
			return;
		}
		int carried = s.essenceCarried();
		if (current.craftStartSecond < 0 && carried > lastEssence && s.location != Location.HUGE_REMAINS && s.fragments < lastFragments)
		{
			current.craftStartSecond = s.gameSeconds;
			current.fragmentsAtCraftStart = lastFragments;
		}
		if (s.location == Location.ALTAR_ROOM && lastLocation != Location.ALTAR_ROOM)
		{
			current.altarTrips++;
			if (current.firstAltarSecond < 0)
			{
				current.firstAltarSecond = s.gameSeconds;
			}
		}
		if (s.location == Location.HUGE_REMAINS && lastLocation != Location.HUGE_REMAINS && s.phase == GamePhase.CRAFTING)
		{
			current.portalsEntered++;
		}
		if (idle && shouldBeBusy && s.location != Location.ALTAR_ROOM)
		{
			idleTicks++;
		}
		current.portalsSpawned = portalsSpawned;
		current.energyElemental = s.energyElemental;
		current.energyCatalytic = s.energyCatalytic;
		if (EnergyMath.isFullyCapped(s.energyElemental, s.energyCatalytic, s.yewLantern))
		{
			current.hitCap = true;
		}
		lastEssence = carried;
		lastFragments = s.fragments;
		lastLocation = s.location;
	}

	/** Call every tick with the player's Runecraft XP. */
	public void trackXp(int runecraftXp)
	{
		if (startXp < 0 || runecraftXp <= 0)
		{
			return;
		}
		if (current != null)
		{
			current.xpGained = Math.max(0, runecraftXp - startXp);
		}
		else if (last != null && lateXpTicks > 0)
		{
			last.xpGained = Math.max(last.xpGained, runecraftXp - startXp);
			lateXpTicks--;
		}
	}

	/** XP gained so far in the round being recorded, or -1. */
	public int currentXp()
	{
		return current == null ? -1 : current.xpGained;
	}

	/** True while the end-of-round XP can still land on the last record. */
	public boolean awaitingLateXp()
	{
		return lateXpTicks > 0;
	}

	/** Ends the round. Returns the graded record, or null if no round was being recorded. */
	public GameRecord finish(boolean won, int gameSeconds, int energyElemental, int energyCatalytic, long nowMillis)
	{
		if (current == null)
		{
			return null;
		}
		GameRecord r = current;
		current = null;
		r.won = won;
		r.gameSeconds = gameSeconds;
		if (energyElemental >= 0)
		{
			r.energyElemental = Math.max(r.energyElemental, energyElemental);
		}
		if (energyCatalytic >= 0)
		{
			r.energyCatalytic = Math.max(r.energyCatalytic, energyCatalytic);
		}
		r.idleSeconds = (int) Math.round(idleTicks * 0.6);
		r.endedAtMillis = nowMillis;
		lateXpTicks = LATE_XP_TICKS;
		Grader.grade(r);
		last = r;
		if (!r.joinedLate)
		{
			history.addFirst(r);
			while (history.size() > MAX_HISTORY)
			{
				history.removeLast();
			}
		}
		return r;
	}

	public boolean isRecording()
	{
		return current != null;
	}

	public GameRecord getLast()
	{
		return last;
	}

	public List<GameRecord> getHistory()
	{
		return new ArrayList<>(history);
	}

	/** Average XP per round over the most recent n rounds that recorded XP, or -1. */
	public double averageXp(int n)
	{
		int count = 0;
		double total = 0;
		for (GameRecord r : history)
		{
			if (count >= n)
			{
				break;
			}
			if (r.xpGained > 0)
			{
				total += r.xpGained;
				count++;
			}
		}
		return count == 0 ? -1 : total / count;
	}

	/** Averages over the most recent n graded rounds: {score, points, seconds}. */
	public double[] averages(int n)
	{
		int count = 0;
		double score = 0;
		double points = 0;
		double seconds = 0;
		for (GameRecord r : history)
		{
			if (count >= n)
			{
				break;
			}
			if (r.score < 0)
			{
				continue;
			}
			score += r.score;
			points += r.points();
			seconds += r.gameSeconds;
			count++;
		}
		if (count == 0)
		{
			return null;
		}
		return new double[]{score / count, points / count, seconds / count, count};
	}

	public String serializeHistory()
	{
		StringBuilder sb = new StringBuilder();
		for (GameRecord r : history)
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(r.serialize());
		}
		return sb.toString();
	}

	public void loadHistory(String saved)
	{
		history.clear();
		if (saved == null || saved.isEmpty())
		{
			return;
		}
		for (String line : saved.split(";"))
		{
			GameRecord r = GameRecord.parse(line);
			if (r != null)
			{
				history.addLast(r);
			}
		}
		last = history.peekFirst();
	}

	public void clearHistory()
	{
		history.clear();
		last = null;
	}
}
