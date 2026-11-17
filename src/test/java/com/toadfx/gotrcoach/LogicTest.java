package com.toadfx.gotrcoach;

import com.toadfx.gotrcoach.coach.GameRecord;
import com.toadfx.gotrcoach.coach.GameRecorder;
import com.toadfx.gotrcoach.coach.Grader;
import com.toadfx.gotrcoach.coach.PersonalBests;
import com.toadfx.gotrcoach.coach.Session;
import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.model.PouchType;
import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Strategy;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.HudState;
import com.toadfx.gotrcoach.state.Location;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LogicTest
{
	// ---------------------------------------------------------------- energy

	@Test
	public void capsPerTypeAndTotal()
	{
		assertEquals(1000, EnergyMath.room(Alignment.ELEMENTAL, 0, 0, false));
		assertEquals(200, EnergyMath.room(Alignment.CATALYTIC, 1000, 0, false));
		assertEquals(320, EnergyMath.room(Alignment.CATALYTIC, 1000, 0, true));
		assertTrue(EnergyMath.isCapped(Alignment.ELEMENTAL, 1000, 0, false));
		assertTrue(EnergyMath.isFullyCapped(700, 500, false));
		assertFalse(EnergyMath.isFullyCapped(700, 500, true));
	}

	@Test
	public void handInRespectsCaps()
	{
		int[] after = EnergyMath.afterHandIn(990, 0, 10, 0, 0, false);
		assertEquals(1000, after[0]);
		assertEquals(10, EnergyMath.wasted(990, 0, 10, 0, 0, false));
		after = EnergyMath.afterHandIn(0, 0, 4, 2, 2, false);
		assertEquals(10, after[0]); // 2 poly x3 + 2 normal x2
		assertEquals(4, after[1]);
	}

	// ---------------------------------------------------------------- HUD

	@Test
	public void hudPhases()
	{
		// args: [script, elemental, catalytic, power, maxPower, portalLoc, elemIdx, catIdx, guardians, maxGuardians, guardianTicks, portalTicks]
		HudState mining = HudState.parse(new Object[]{5980, 0, 0, 100, 1000, -1, 0, 0, 0, 10, 150, -1}, 0);
		assertNotNull(mining);
		assertEquals(GamePhase.MINING, mining.phase());
		HudState crafting = HudState.parse(new Object[]{5980, 120, 80, 400, 1000, 3, 2, 5, 4, 10, 50, 30}, 0);
		assertEquals(GamePhase.CRAFTING, crafting.phase());
		assertTrue(crafting.portalOpen());
		assertEquals(40, crafting.powerPercent());
		assertEquals(GamePhase.CLOSING, HudState.parse(new Object[]{5980, 0, 0, 1000, 1000, -1, 2, 5, 4, 10, 50, -1}, 0).phase());
		assertEquals(GamePhase.ENDED, HudState.parse(new Object[]{5980, 0, 0, 0, 1000, 0, 0, 0, 0, 10, -5, -1}, 0).phase());
		assertEquals(GamePhase.WAITING, HudState.parse(new Object[]{5980, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0).phase());
		assertNull(HudState.parse(new Object[]{5980, 1, 2}, 0));
		assertNull(HudState.parse(new Object[]{5980, "x", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0));
	}

	// ---------------------------------------------------------------- pouches

	@Test
	public void colossalGrowsWithLevel()
	{
		assertEquals(8, PouchType.COLOSSAL.capacity(30));
		assertEquals(16, PouchType.COLOSSAL.capacity(60));
		assertEquals(27, PouchType.COLOSSAL.capacity(80));
		assertEquals(40, PouchType.COLOSSAL.capacity(90));
		assertEquals(12, PouchType.GIANT.capacity(1));
	}

	// ---------------------------------------------------------------- grading

	private static GameRecord goodRound()
	{
		GameRecord r = new GameRecord();
		r.strategy = Strategy.TEAM;
		r.won = true;
		r.gameSeconds = 540;
		r.craftStartSecond = 86;
		r.fragmentsAtCraftStart = 125;
		r.firstAltarSecond = 128;
		r.portalsSpawned = 3;
		r.portalsEntered = 3;
		r.idleSeconds = 5;
		r.energyElemental = 450;
		r.energyCatalytic = 430;
		r.xpGained = 38_000;
		return r;
	}

	@Test
	public void cleanRoundGradesHigh()
	{
		GameRecord r = goodRound();
		Grader.grade(r);
		assertTrue("score " + r.score, r.score >= 95);
		assertEquals("S", r.grade);
	}

	@Test
	public void lateCraftAndMissedPortalsCost()
	{
		GameRecord r = goodRound();
		r.craftStartSecond = 115;
		r.portalsEntered = 1;
		r.energyElemental = 250;
		r.energyCatalytic = 250;
		Grader.grade(r);
		assertTrue("score " + r.score, r.score < 70);
		assertTrue(r.tips.stream().anyMatch(t -> t.contains("late")));
		assertTrue(r.tips.stream().anyMatch(t -> t.contains("Missed 2 of 3")));
	}

	@Test
	public void lateJoinNotGraded()
	{
		GameRecord r = goodRound();
		r.joinedLate = true;
		Grader.grade(r);
		assertEquals(-1, r.score);
	}

	@Test
	public void recordRoundTrip()
	{
		GameRecord r = goodRound();
		Grader.grade(r);
		GameRecord back = GameRecord.parse(r.serialize());
		assertNotNull(back);
		assertEquals(r.score, back.score);
		assertEquals(r.grade, back.grade);
		assertEquals(r.energyCatalytic, back.energyCatalytic);
		assertEquals(r.xpGained, back.xpGained);
		// Rounds saved before XP tracking still load.
		String old = r.serialize();
		assertNotNull(GameRecord.parse(old.substring(0, old.lastIndexOf(','))));
		assertNull(GameRecord.parse("garbage"));
	}

	@Test
	public void recorderFollowsARound()
	{
		GameRecorder rec = new GameRecorder();
		rec.start(Strategy.TEAM, false, 1_000_000);
		Snapshot s = new Snapshot();
		s.phase = GamePhase.MINING;
		s.location = Location.TEMPLE;
		for (int sec = 0; sec <= 85; sec++)
		{
			s.gameSeconds = sec;
			s.fragments = sec * 3 / 2;
			rec.tick(s, false, true, 0);
		}
		// Crafting starts: fragments go down, essence goes up.
		s.gameSeconds = 86;
		s.fragments = 120;
		s.essence = 5;
		rec.tick(s, false, true, 0);
		s.phase = GamePhase.CRAFTING;
		s.gameSeconds = 125;
		s.location = Location.ALTAR_ROOM;
		rec.tick(s, false, true, 0);
		s.location = Location.TEMPLE;
		s.gameSeconds = 165;
		rec.tick(s, false, true, 1);
		s.location = Location.HUGE_REMAINS;
		s.gameSeconds = 170;
		s.energyElemental = 420;
		s.energyCatalytic = 400;
		rec.tick(s, false, true, 1);
		rec.trackXp(1_030_000);
		GameRecord r = rec.finish(true, 520, 420, 400, 0L);
		// End-of-round XP lands a tick or two after the round ends.
		rec.trackXp(1_042_500);
		assertEquals(42_500, r.xpGained);
		assertNotNull(r);
		assertEquals(86, r.craftStartSecond);
		assertEquals(125, r.firstAltarSecond);
		assertEquals(1, r.portalsEntered);
		assertEquals(1, r.portalsSpawned);
		assertEquals(1, rec.getHistory().size());
		GameRecorder copy = new GameRecorder();
		copy.loadHistory(rec.serializeHistory());
		assertEquals(1, copy.getHistory().size());
	}

	@Test
	public void personalBestsTrackAndPersist()
	{
		PersonalBests b = new PersonalBests();
		GameRecord first = goodRound();
		Grader.grade(first);
		assertTrue("first round sets bests silently", b.update(first).isEmpty());
		assertEquals(880, b.mostEnergy);

		GameRecord better = goodRound();
		better.energyElemental = 600;
		better.xpGained = 50_000;
		better.gameSeconds = 480;
		Grader.grade(better);
		java.util.List<String> beaten = b.update(better);
		assertTrue(beaten.toString(), beaten.stream().anyMatch(t -> t.startsWith("most points")));
		assertTrue(beaten.toString(), beaten.stream().anyMatch(t -> t.startsWith("most XP")));
		assertTrue(beaten.toString(), beaten.stream().anyMatch(t -> t.startsWith("fastest round (8:00)")));

		GameRecord late = goodRound();
		late.joinedLate = true;
		late.energyElemental = 999;
		assertTrue(b.update(late).isEmpty());

		PersonalBests back = PersonalBests.parse(b.serialize());
		assertEquals(b.mostEnergy, back.mostEnergy);
		assertEquals(b.mostXp, back.mostXp);
		assertEquals(480, back.fastestWin);
		assertTrue(PersonalBests.parse("junk").isEmpty());
	}

	@Test
	public void sessionRates()
	{
		Session s = new Session();
		assertTrue(s.isEmpty());
		s.roundStarted(0L);
		GameRecord r = goodRound();
		r.xpGained = 40_000;
		s.add(r);
		s.add(r);
		assertEquals(2, s.getRounds());
		assertEquals(80_000, s.getXp());
		// Two rounds in 30 minutes: 160k XP/hr and 4 rounds/hr.
		assertEquals(160_000, Math.round(s.xpPerHour(30 * 60_000L)));
		assertEquals(4, Math.round(s.roundsPerHour(30 * 60_000L)));
		assertEquals(0, Math.round(s.xpPerHour(30_000L)));
		s.reset();
		assertTrue(s.isEmpty());
	}
}
