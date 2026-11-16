package com.toadfx.gotrcoach;

import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.plan.AltarChooser;
import com.toadfx.gotrcoach.plan.AltarMode;
import com.toadfx.gotrcoach.plan.Instruction;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.PlannerSettings;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Step;
import com.toadfx.gotrcoach.plan.Strategy;
import com.toadfx.gotrcoach.plan.Target;
import com.toadfx.gotrcoach.plan.Urgency;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.Location;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PlannerTest
{
	private static Snapshot crafting()
	{
		Snapshot s = new Snapshot();
		s.phase = GamePhase.CRAFTING;
		s.location = Location.TEMPLE;
		s.gameSeconds = 200;
		s.activeElemental = Altar.WATER;
		s.activeCatalytic = Altar.NATURE;
		s.altarTicksLeft = 100;
		s.unchargedCells = 10;
		s.hasChisel = true;
		s.hasPouches = true;
		s.pouchCapacity = 40;
		return s;
	}

	private static PlannerSettings team()
	{
		PlannerSettings p = new PlannerSettings();
		p.strategy = Strategy.TEAM;
		return p;
	}

	@Test
	public void betweenRoundsTakesCellsThenWeakCellThenWaits()
	{
		Snapshot s = new Snapshot();
		s.phase = GamePhase.WAITING;
		s.location = Location.TEMPLE;
		s.unchargedCells = 3;
		assertEquals(Step.TAKE_UNCHARGED_CELLS, Planner.plan(s, team()).getStep());
		s.unchargedCells = 10;
		assertEquals(Step.TAKE_WEAK_CELL, Planner.plan(s, team()).getStep());
		s.chargedCell = CellTier.WEAK;
		assertEquals(Step.WAIT_FOR_START, Planner.plan(s, team()).getStep());
	}

	@Test
	public void lobbyMeansEnterBarrier()
	{
		Snapshot s = new Snapshot();
		s.phase = GamePhase.WAITING;
		s.location = Location.LOBBY;
		assertEquals(Step.ENTER_BARRIER, Planner.plan(s, team()).getStep());
	}

	@Test
	public void openingPlacesTheWeakCellFirst()
	{
		Snapshot s = new Snapshot();
		s.phase = GamePhase.MINING;
		s.location = Location.TEMPLE;
		s.gameSeconds = 2;
		s.secondsUntilAltars = 118;
		s.chargedCell = CellTier.WEAK;
		s.emptyTile = true;
		s.guardians = 6;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.PLACE_CELL, i.getStep());
		assertEquals(Target.CELL_TILE, i.getTarget());
	}

	@Test
	public void miningUntilTargetThenCraft()
	{
		Snapshot s = new Snapshot();
		s.phase = GamePhase.MINING;
		s.location = Location.TEMPLE;
		s.gameSeconds = 40;
		s.secondsUntilAltars = 80;
		s.fragments = 50;
		s.agilityLevel = 60;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.MINE_FRAGMENTS, i.getStep());
		assertEquals(Target.LARGE_REMAINS, i.getTarget());

		s.agilityLevel = 40;
		assertEquals(Target.GUARDIAN_REMAINS, Planner.plan(s, team()).getTarget());

		// Team: stop 35s before the altars open (85s into the round).
		s.secondsUntilAltars = 35;
		s.gameSeconds = 85;
		assertEquals(Step.CRAFT_ESSENCE, Planner.plan(s, team()).getStep());
	}

	@Test
	public void massMinesLongerAndFirstTripIsThirtyEssence()
	{
		PlannerSettings mass = team();
		mass.strategy = Strategy.MASS;
		Snapshot s = new Snapshot();
		s.phase = GamePhase.MINING;
		s.location = Location.TEMPLE;
		s.secondsUntilAltars = 35;
		s.fragments = 130;
		s.pouchCapacity = 40;
		assertEquals("mass keeps mining at 85s", Step.MINE_FRAGMENTS, Planner.plan(s, mass).getStep());

		s.fragments = 168;
		s.essence = 0;
		Instruction i = Planner.plan(s, mass);
		assertEquals(Step.CRAFT_ESSENCE, i.getStep());
		assertTrue(i.getDetail(), i.getDetail().startsWith("0 / 30"));

		s.essence = 30;
		s.freeSlots = 0;
		s.fragments = 138;
		s.openingCraftStarted = true;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, mass).getStep());
	}

	@Test
	public void stonesGoToTheGreatGuardian()
	{
		Snapshot s = crafting();
		s.elementalStones = 20;
		assertEquals(Step.POWER_UP, Planner.plan(s, team()).getStep());
	}

	@Test
	public void graceHoldNearTheEnd()
	{
		Snapshot s = crafting();
		s.catalyticStones = 20;
		s.powerPercent = 93;
		s.secondsSincePortal = 60;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.HOLD_STONES, i.getStep());
		s.secondsSincePortal = 96;
		assertEquals(Step.POWER_UP, Planner.plan(s, team()).getStep());
	}

	@Test
	public void portalBeatsCraftingWhenThereIsRoom()
	{
		Snapshot s = crafting();
		s.fragments = 50;
		s.portalOpen = true;
		s.portalTicksLeft = 30;
		s.portalTravelTicks = 10;
		assertEquals(Step.ENTER_REMAINS_PORTAL, Planner.plan(s, team()).getStep());

		// Can't make it before it closes.
		s.portalTravelTicks = 35;
		assertEquals(Step.CRAFT_ESSENCE, Planner.plan(s, team()).getStep());
	}

	@Test
	public void portalUrgentWhenClosing()
	{
		Snapshot s = crafting();
		s.portalOpen = true;
		s.portalTicksLeft = 8;
		s.portalTravelTicks = 4;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.ENTER_REMAINS_PORTAL, i.getStep());
		assertEquals(Urgency.NOW, i.getUrgency());
	}

	@Test
	public void insideAltarCraftThenEmptyThenLeave()
	{
		Snapshot s = crafting();
		s.location = Location.ALTAR_ROOM;
		s.altarRoom = Altar.NATURE;
		s.essence = 26;
		assertEquals(Step.CRAFT_RUNES, Planner.plan(s, team()).getStep());
		s.essence = 0;
		s.essenceInPouches = 40;
		assertEquals(Step.EMPTY_POUCHES, Planner.plan(s, team()).getStep());
		s.essenceInPouches = 0;
		assertEquals(Step.LEAVE_ALTAR, Planner.plan(s, team()).getStep());
	}

	@Test
	public void strongCellBuildsGuardianWeakCellHealsBarrier()
	{
		Snapshot s = crafting();
		s.chargedCell = CellTier.STRONG;
		s.guardians = 2;
		assertEquals(Step.BUILD_GUARDIAN, Planner.plan(s, team()).getStep());
		s.chargedCell = CellTier.WEAK;
		assertEquals(Step.PLACE_CELL, Planner.plan(s, team()).getStep());
		s.chargedCell = CellTier.OVERCHARGED;
		s.hasChisel = false;
		assertEquals(Step.PLACE_CELL, Planner.plan(s, team()).getStep());
	}

	@Test
	public void repairBrokenBarrierWithFragments()
	{
		Snapshot s = crafting();
		s.repairableTile = true;
		s.fragments = 30;
		s.powerPercent = 70;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.REPAIR_BARRIER, i.getStep());
		assertEquals(Urgency.NOW, i.getUrgency());
	}

	@Test
	public void energyCapStopsCrafting()
	{
		Snapshot s = crafting();
		s.energyElemental = 600;
		s.energyCatalytic = 600;
		s.essence = 20;
		assertEquals(Step.ENERGY_CAPPED, Planner.plan(s, team()).getStep());
	}

	@Test
	public void depositRunesOnlyWhenTurnedOn()
	{
		Snapshot s = crafting();
		s.fragments = 60;
		s.runeSlots = 6;
		s.freeSlots = 20;
		assertEquals("off by default", Step.CRAFT_ESSENCE, Planner.plan(s, team()).getStep());
		PlannerSettings on = team();
		on.depositRunes = true;
		assertEquals(Step.DEPOSIT_RUNES, Planner.plan(s, on).getStep());
	}

	@Test
	public void portalAlwaysBeatsStonesAndCells()
	{
		Snapshot s = crafting();
		s.elementalStones = 12;
		s.chargedCell = CellTier.STRONG;
		s.freeSlots = 5;
		s.portalOpen = true;
		s.portalTicksLeft = 30;
		s.portalTravelTicks = 8;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.ENTER_REMAINS_PORTAL, i.getStep());
		assertEquals(Urgency.NOW, i.getUrgency());

		// Old behaviour: stones first, and only with the minimum room.
		PlannerSettings room = team();
		room.portalAlways = false;
		assertEquals(Step.POWER_UP, Planner.plan(s, room).getStep());
	}

	@Test
	public void portalSkippedWhenFullOrUnreachable()
	{
		Snapshot s = crafting();
		s.portalOpen = true;
		s.portalTicksLeft = 30;
		s.portalTravelTicks = 8;
		s.freeSlots = 0;
		s.essence = 28;
		s.essenceInPouches = 40;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, team()).getStep());
		s.freeSlots = 10;
		s.essence = 18;
		s.portalTravelTicks = 40;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, team()).getStep());
	}

	@Test
	public void closingHandsInNow()
	{
		Snapshot s = crafting();
		s.phase = GamePhase.CLOSING;
		s.elementalStones = 5;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.HAND_IN_NOW, i.getStep());
		assertEquals(Urgency.NOW, i.getUrgency());
	}

	// ---------------------------------------------------------------- altar choice

	@Test
	public void evenPointsPrefersBloodAndDeath()
	{
		Snapshot s = crafting();
		s.activeElemental = Altar.FIRE;
		s.activeCatalytic = Altar.BLOOD;
		s.essence = 20;
		AltarChooser.Choice c = AltarChooser.choose(s, team());
		assertEquals(Altar.BLOOD, c.primary);
		assertEquals(Altar.FIRE, c.secondary);
	}

	@Test
	public void evenPointsThenFireEarthThenLowerType()
	{
		Snapshot s = crafting();
		s.activeElemental = Altar.EARTH;
		s.activeCatalytic = Altar.NATURE;
		assertEquals(Altar.EARTH, AltarChooser.choose(s, team()).primary);

		s.activeElemental = Altar.WATER;
		s.energyElemental = 300;
		s.energyCatalytic = 100;
		assertEquals(Altar.NATURE, AltarChooser.choose(s, team()).primary);
	}

	@Test
	public void imbalanceOverridesThePreference()
	{
		Snapshot s = crafting();
		s.activeElemental = Altar.FIRE;
		s.activeCatalytic = Altar.LAW;
		s.energyElemental = 500;
		s.energyCatalytic = 100;
		assertEquals(Altar.LAW, AltarChooser.choose(s, team()).primary);
	}

	@Test
	public void bankedPointsCountWhenBalancing()
	{
		PlannerSettings p = team();
		p.altarMode = AltarMode.BALANCED;
		Snapshot s = crafting();
		s.bankedElemental = 2;
		s.bankedCatalytic = 50;
		assertEquals(Altar.WATER, AltarChooser.choose(s, p).primary);
	}

	@Test
	public void lockedAndCappedAltarsAreSkipped()
	{
		Snapshot s = crafting();
		s.activeCatalytic = Altar.BLOOD;
		s.runecraftLevel = 60;
		assertEquals(Altar.WATER, AltarChooser.choose(s, team()).primary);

		s.runecraftLevel = 99;
		s.questLocked.add(Altar.BLOOD);
		assertEquals(Altar.WATER, AltarChooser.choose(s, team()).primary);

		s.questLocked.clear();
		s.energyCatalytic = 1000;
		assertEquals(Altar.WATER, AltarChooser.choose(s, team()).primary);
	}

	@Test
	public void unreachableAltarLosesToReachableOne()
	{
		Snapshot s = crafting();
		s.activeElemental = Altar.FIRE;
		s.activeCatalytic = Altar.DEATH;
		s.altarTicksLeft = 10;
		s.altarTravelTicks.put(Altar.DEATH, 20);
		s.altarTravelTicks.put(Altar.FIRE, 6);
		AltarChooser.Choice c = AltarChooser.choose(s, team());
		assertEquals(Altar.FIRE, c.primary);
	}

	@Test
	public void noUsableAltar()
	{
		Snapshot s = crafting();
		s.activeElemental = null;
		s.activeCatalytic = Altar.BLOOD;
		s.runecraftLevel = 50;
		assertNull(AltarChooser.choose(s, team()));
		s.essence = 10;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.ENTER_ALTAR, i.getStep());
		assertEquals(Target.NONE, i.getTarget());
	}

	@Test
	public void talismanAltarCountsAsAnOption()
	{
		Snapshot s = crafting();
		s.activeElemental = Altar.WATER;
		s.activeCatalytic = Altar.NATURE;
		s.talismans.add(Altar.BLOOD);
		s.essence = 20;
		AltarChooser.Choice c = AltarChooser.choose(s, team());
		assertEquals(Altar.BLOOD, c.primary);
		assertTrue(c.viaTalisman);
		assertTrue(c.reason, c.reason.contains("blood talisman"));

		// Talisman altars never rotate away, so a short rotation timer doesn't rule them out.
		s.altarTicksLeft = 2;
		s.altarTravelTicks.put(Altar.BLOOD, 20);
		assertEquals(Altar.BLOOD, AltarChooser.choose(s, team()).primary);
	}

	@Test
	public void talismanForLockedAltarIsIgnored()
	{
		Snapshot s = crafting();
		s.talismans.add(Altar.DEATH);
		s.questLocked.add(Altar.DEATH);
		AltarChooser.Choice c = AltarChooser.choose(s, team());
		assertTrue(c.primary != Altar.DEATH);
	}

	@Test
	public void closingUsesAnAltarStillOpen()
	{
		Snapshot s = crafting();
		s.phase = GamePhase.CLOSING;
		s.activeElemental = Altar.FIRE;
		s.activeCatalytic = null;
		s.essence = 24;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.ENTER_ALTAR, i.getStep());
		assertEquals(Altar.FIRE, i.getAltar());
		assertEquals(Urgency.NOW, i.getUrgency());

		s.elementalStones = 10;
		assertEquals("stones first", Step.HAND_IN_NOW, Planner.plan(s, team()).getStep());
	}

	@Test
	public void portalDueSoonHoldsAltarTrips()
	{
		Snapshot s = crafting();
		s.essence = 14;
		s.freeSlots = 14;
		s.secondsToPortal = 12;
		Instruction i = Planner.plan(s, team());
		assertEquals(Step.WAIT_FOR_PORTAL, i.getStep());
		assertTrue(i.getDetail(), i.getDetail().contains("12s"));

		// Further out than the countdown: go craft.
		s.secondsToPortal = 40;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, team()).getStep());

		// Full inventory and pouches: the portal is no use, so go anyway.
		s.secondsToPortal = 12;
		s.freeSlots = 0;
		s.essence = 28;
		s.essenceInPouches = 40;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, team()).getStep());

		// Turned off.
		PlannerSettings off = team();
		off.holdForPortal = false;
		s.freeSlots = 14;
		s.essence = 14;
		s.essenceInPouches = 0;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, off).getStep());
	}

	@Test
	public void smallTopUpGoesStraightToTheAltar()
	{
		// The screenshot case: 22 essence, 2 free slots, 121 fragments after the portal.
		Snapshot s = crafting();
		s.hasPouches = false;
		s.pouchCapacity = 0;
		s.essence = 22;
		s.freeSlots = 2;
		s.fragments = 121;
		assertEquals(Step.ENTER_ALTAR, Planner.plan(s, team()).getStep());

		// Lots of room left: still worth filling up.
		s.essence = 10;
		s.freeSlots = 14;
		assertEquals(Step.CRAFT_ESSENCE, Planner.plan(s, team()).getStep());
	}

	@Test
	public void goNowIfCraftingWouldMissTheRotation()
	{
		Snapshot s = crafting();
		s.hasPouches = false;
		s.pouchCapacity = 0;
		s.essence = 14;
		s.freeSlots = 14;
		s.fragments = 80;
		s.altarTicksLeft = 17;
		s.altarTravelTicks.put(Altar.WATER, 8);
		s.altarTravelTicks.put(Altar.NATURE, 9);
		assertEquals("14 more essence (~28 ticks) would miss it", Step.ENTER_ALTAR, Planner.plan(s, team()).getStep());

		// Plenty of time before the rotation: fill up first.
		s.altarTicksLeft = 90;
		assertEquals(Step.CRAFT_ESSENCE, Planner.plan(s, team()).getStep());

		// Too late either way: keep crafting for the next pair rather than running for nothing.
		s.altarTicksLeft = 5;
		assertEquals(Step.CRAFT_ESSENCE, Planner.plan(s, team()).getStep());
	}
}
