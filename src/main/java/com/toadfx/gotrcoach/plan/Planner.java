package com.toadfx.gotrcoach.plan;

import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.Location;

/**
 * Turns a {@link Snapshot} into the single next thing to do, following the OSRS Wiki
 * Guardians of the Rift strategies. Pure logic: no RuneLite calls, so it is unit tested.
 */
public final class Planner
{
	/** Fragments a broken cell tile costs to repair. */
	public static final int REPAIR_COST = 12;
	/** Wiki grace-portal trick: an extra portal spawns if the grace period starts 95s+ after the last one. */
	public static final int GRACE_PORTAL_SECONDS = 95;
	/** Huge-remains portals stay open about 25 seconds; under this many ticks left it is urgent. */
	private static final int PORTAL_URGENT_TICKS = 15;

	private Planner()
	{
	}

	public static Instruction plan(Snapshot s, PlannerSettings st)
	{
		switch (s.phase)
		{
			case MINING:
				return mining(s, st);
			case CRAFTING:
				return crafting(s, st);
			case CLOSING:
				return closing(s, st);
			case WAITING:
			case ENDED:
				return between(s, st);
			case IDLE:
			default:
				return Instruction.of(Step.OUTSIDE, Target.NONE,
					"Minigame teleport, or the portal in the Wizards' Tower basement");
		}
	}

	// ---------------------------------------------------------------- between rounds

	private static Instruction between(Snapshot s, PlannerSettings st)
	{
		if (s.phase == GamePhase.ENDED && s.countdownSeconds < 0)
		{
			return Instruction.of(Step.GAME_OVER, Target.NONE, "Points are banked. Safe to leave.");
		}
		String countdown = s.countdownSeconds >= 0 ? "Starts in " + s.countdownSeconds + "s. " : "";
		if (s.pouchNeedsRepair)
		{
			return Instruction.of(Step.REPAIR_POUCHES, Target.CORDELIA,
				countdown + "NPC Contact the Dark Mage, or talk to Apprentice Cordelia");
		}
		if (!s.hasPickaxe)
		{
			return new Instruction(Step.GET_PICKAXE, Target.NONE, null,
				countdown + "You can't mine fragments without one (inventory or worn)", Urgency.SOON);
		}
		if (!s.hasChisel)
		{
			return new Instruction(Step.GET_CHISEL, Target.NONE, null,
				countdown + "Needed to craft essence and build guardians", Urgency.SOON);
		}
		if (s.location == Location.LOBBY)
		{
			return Instruction.of(Step.ENTER_BARRIER, Target.ENTRANCE_BARRIER, countdown + "Get inside before the rift opens");
		}
		if (s.unchargedCells < st.unchargedCellsWanted)
		{
			return Instruction.of(Step.TAKE_UNCHARGED_CELLS, Target.UNCHARGED_CELLS,
				countdown + s.unchargedCells + " / " + st.unchargedCellsWanted + " uncharged cells");
		}
		if (s.chargedCell == null)
		{
			return Instruction.of(Step.TAKE_WEAK_CELL, Target.WEAK_CELLS, countdown + "For your opening barrier or guardian");
		}
		if (st.strategy == Strategy.SOLO)
		{
			return Instruction.of(Step.WAIT_FOR_START, Target.ESSENCE_PILE_ELEMENTAL,
				countdown + "Build a weak elemental guardian, then one barrier between the fire and blood portals");
		}
		return Instruction.of(Step.WAIT_FOR_START, Target.CELL_TILE,
			countdown + "Stand on the barrier side of an empty tile; place your cell when the rift opens");
	}

	// ---------------------------------------------------------------- mining phase

	private static Instruction mining(Snapshot s, PlannerSettings st)
	{
		if (s.location == Location.ALTAR_ROOM)
		{
			return Instruction.of(Step.LEAVE_ALTAR, Target.ALTAR_EXIT, "");
		}
		if (s.chargedCell != null && s.gameSeconds < 30)
		{
			return placeCell(s, st);
		}
		if (s.location == Location.HUGE_REMAINS)
		{
			// Wiki: with an inter-round portal, fill up with essence first, then mine fragments.
			if (s.essenceRoom() > 0)
			{
				return Instruction.of(Step.MINE_HUGE_REMAINS, Target.HUGE_REMAINS, "Fill inventory and pouches");
			}
			return Instruction.of(Step.LEAVE_REMAINS, Target.REMAINS_PORTAL_EXIT, "Full, go mine fragments");
		}

		int target = st.openingFragments();
		int lead = st.craftLeadSeconds();
		boolean timeUp = s.secondsUntilAltars >= 0 && s.secondsUntilAltars <= lead;
		boolean craftNow = s.openingCraftStarted || s.fragments >= target || timeUp;
		Target remains = s.agilityLevel >= 56 ? Target.LARGE_REMAINS : Target.GUARDIAN_REMAINS;

		if (!craftNow)
		{
			String until = s.secondsUntilAltars >= 0
				? " · craft in " + Math.max(0, s.secondsUntilAltars - lead) + "s" : "";
			return Instruction.of(Step.MINE_FRAGMENTS, remains, s.fragments + " / " + target + " fragments" + until);
		}

		int goal = essenceGoal(s, st);
		if (s.fragments > 0 && s.essenceCarried() < goal)
		{
			return Instruction.of(Step.CRAFT_ESSENCE, Target.WORKBENCH,
				s.essenceCarried() + " / " + goal + " essence" + (s.hasPouches ? ", fill pouches as you go" : ""));
		}
		if (s.essenceCarried() < st.minEssenceForAltar && s.fragments == 0 && s.secondsUntilAltars > 20)
		{
			return Instruction.of(Step.MINE_FRAGMENTS, remains, "Not enough essence yet");
		}
		String opens = s.secondsUntilAltars >= 0 ? "Altars open in " + s.secondsUntilAltars + "s" : "Altars open soon";
		return new Instruction(Step.ENTER_ALTAR, Target.NONE, null, opens + ", wait near the middle", Urgency.NORMAL);
	}

	/** Essence to carry for the next trip. On mass worlds the first trip is a quick 30 essence. */
	static int essenceGoal(Snapshot s, PlannerSettings st)
	{
		int capacity = s.essenceCarried() + s.essenceRoom();
		if (st.strategy == Strategy.MASS && s.altarTrips == 0)
		{
			return Math.min(st.massFirstTripEssence, capacity);
		}
		return capacity;
	}

	// ---------------------------------------------------------------- crafting phase

	private static Instruction crafting(Snapshot s, PlannerSettings st)
	{
		if (s.location == Location.ALTAR_ROOM)
		{
			return inAltar(s, Urgency.NORMAL);
		}
		if (s.location == Location.HUGE_REMAINS)
		{
			if (s.essenceRoom() > 0)
			{
				return Instruction.of(Step.MINE_HUGE_REMAINS, Target.HUGE_REMAINS,
					s.essenceRoom() + " essence of room left");
			}
			return Instruction.of(Step.LEAVE_REMAINS, Target.REMAINS_PORTAL_EXIT, "Full, head to an altar");
		}

		boolean capped = EnergyMath.isFullyCapped(s.energyElemental, s.energyCatalytic, s.yewLantern);

		// Portal first: a huge-remains portal is a free full load, so it beats everything that can wait.
		if (st.portalAlways && s.portalOpen && s.essenceRoom() > 0 && portalReachable(s))
		{
			return portalStep(s, Urgency.NOW);
		}

		if (s.stones() > 0)
		{
			if (!capped && st.graceHold && s.powerPercent >= 90 && !s.portalOpen
				&& s.secondsSincePortal >= 0 && s.secondsSincePortal < GRACE_PORTAL_SECONDS)
			{
				int wait = GRACE_PORTAL_SECONDS - s.secondsSincePortal;
				return Instruction.of(Step.HOLD_STONES, Target.GREAT_GUARDIAN,
					"Hand in after " + wait + "s so the grace period gets an extra portal");
			}
			int wasted = EnergyMath.wasted(s.energyElemental, s.energyCatalytic,
				s.elementalStones, s.catalyticStones, s.polyStones, s.yewLantern);
			String detail = wasted > 0
				? wasted + " energy is over the cap (still powers the guardian)"
				: s.stones() + " stones";
			return Instruction.of(Step.POWER_UP, Target.GREAT_GUARDIAN, detail);
		}

		if (s.chargedCell != null)
		{
			return placeCell(s, st);
		}

		if (s.repairableTile && s.fragments >= REPAIR_COST)
		{
			return new Instruction(Step.REPAIR_BARRIER, Target.CELL_TILE, null,
				"Costs " + REPAIR_COST + " fragments, the guardian drains fast without it",
				s.powerPercent >= 60 ? Urgency.NOW : Urgency.SOON);
		}

		if (capped)
		{
			return Instruction.of(Step.ENERGY_CAPPED, Target.NONE,
				"Both caps reached, more runes are wasted. Heal barriers or build guardians to help finish");
		}

		if (!st.portalAlways && s.portalOpen && s.essenceRoom() >= st.portalMinRoom && portalReachable(s))
		{
			Urgency u = s.portalTicksLeft >= 0 && s.portalTicksLeft < PORTAL_URGENT_TICKS ? Urgency.NOW : Urgency.SOON;
			return portalStep(s, u);
		}

		if (st.depositRunes && s.runeSlots > 0 && s.freeSlots < st.depositBelowFreeSlots && s.fragments > 0)
		{
			return Instruction.of(Step.DEPOSIT_RUNES, Target.DEPOSIT_POOL, "Free up space before crafting essence");
		}

		if (s.fragments > 0 && s.essenceRoom() > 0)
		{
			int goal = essenceGoal(s, st);
			// Topping up a nearly full load isn't worth the walk (e.g. 2 slots after the portal),
			// and never worth losing the altar to a rotation.
			boolean worthIt = s.essenceCarried() == 0 || goal - s.essenceCarried() >= st.topUpMin;
			if (s.essenceCarried() < goal && worthIt && !rotationRush(s, st))
			{
				return Instruction.of(Step.CRAFT_ESSENCE, Target.WORKBENCH,
					s.essenceCarried() + " / " + goal + " essence");
			}
		}

		if (s.essenceCarried() > 0)
		{
			if (portalDueSoon(s, st))
			{
				return new Instruction(Step.WAIT_FOR_PORTAL, Target.NONE, null,
					"Portal in ~" + s.secondsToPortal + "s. An altar trip now means missing it", Urgency.SOON);
			}
			return enterAltar(s, st);
		}

		Target remains = s.agilityLevel >= 56 ? Target.LARGE_REMAINS : Target.GUARDIAN_REMAINS;
		return Instruction.of(Step.MINE_FRAGMENTS, remains, nextPortalHint(s));
	}

	private static Instruction portalStep(Snapshot s, Urgency urgency)
	{
		String left = s.portalTicksLeft >= 0 ? "Closes in " + ticksToSeconds(s.portalTicksLeft) + "s" : "Open now";
		String room = s.essenceRoom() + " essence of room";
		return new Instruction(Step.ENTER_REMAINS_PORTAL, Target.REMAINS_PORTAL, null, left + ", " + room, urgency);
	}

	private static Instruction inAltar(Snapshot s, Urgency urgency)
	{
		if (s.essence > 0)
		{
			return new Instruction(Step.CRAFT_RUNES, Target.ALTAR, s.altarRoom, s.essence + " essence", urgency);
		}
		if (s.essenceInPouches > 0)
		{
			return new Instruction(Step.EMPTY_POUCHES, Target.ALTAR, s.altarRoom, s.essenceInPouches + " in pouches", urgency);
		}
		return new Instruction(Step.LEAVE_ALTAR, Target.ALTAR_EXIT, s.altarRoom, "", urgency);
	}

	private static Instruction enterAltar(Snapshot s, PlannerSettings st)
	{
		AltarChooser.Choice choice = AltarChooser.choose(s, st);
		if (choice == null)
		{
			String rotate = s.altarTicksLeft >= 0 ? ", they rotate in " + ticksToSeconds(s.altarTicksLeft) + "s" : "";
			return new Instruction(Step.ENTER_ALTAR, Target.NONE, null, "No usable altar open" + rotate, Urgency.NORMAL);
		}
		Urgency u = Urgency.NORMAL;
		Integer travel = s.altarTravelTicks.get(choice.primary);
		if (travel != null && travel >= 0 && s.altarTicksLeft >= 0 && s.altarTicksLeft - travel < 5)
		{
			u = Urgency.SOON;
		}
		return new Instruction(Step.ENTER_ALTAR, Target.ALTAR_PORTAL, choice.primary, choice.reason, u)
			.withSecondary(choice.secondary);
	}

	private static Instruction placeCell(Snapshot s, PlannerSettings st)
	{
		CellTier cell = s.chargedCell;
		int wanted = st.strategy == Strategy.SOLO ? 2 : st.guardiansWanted;
		boolean opening = s.gameSeconds < 30;
		boolean slotFree = s.guardians < Math.min(wanted, s.guardiansMax);
		// Wiki: after the first phase, keep weak cells for barriers; guardians take strong/overcharged.
		boolean worthy = cell.isGuardianWorthy() || (opening && st.strategy != Strategy.SOLO);
		if (st.strategy == Strategy.SOLO && opening && cell == CellTier.WEAK && s.guardians == 0)
		{
			worthy = true;
		}
		if (s.hasChisel && slotFree && worthy)
		{
			Alignment side = s.energyElemental <= s.energyCatalytic ? Alignment.ELEMENTAL : Alignment.CATALYTIC;
			Target pile = side == Alignment.ELEMENTAL ? Target.ESSENCE_PILE_ELEMENTAL : Target.ESSENCE_PILE_CATALYTIC;
			return Instruction.of(Step.BUILD_GUARDIAN, pile,
				cell.getLabel() + " cell, " + s.guardians + "/" + wanted + " guardians up, "
					+ side.getLabel().toLowerCase() + " side");
		}
		String where;
		if (st.strategy == Strategy.SOLO)
		{
			where = "Right-most barrier, between the fire and blood portals";
		}
		else if (s.emptyTile && opening)
		{
			where = "Empty tile, build a barrier";
		}
		else
		{
			where = "Weakest barrier (healing stacks, guardians don't)";
		}
		return Instruction.of(Step.PLACE_CELL, Target.CELL_TILE, cell.getLabel() + " cell: " + where);
	}

	// ---------------------------------------------------------------- closing

	private static Instruction closing(Snapshot s, PlannerSettings st)
	{
		if (s.location == Location.ALTAR_ROOM)
		{
			return inAltar(s, Urgency.NOW);
		}
		if (s.stones() > 0)
		{
			return new Instruction(Step.HAND_IN_NOW, Target.GREAT_GUARDIAN, null,
				"The rifts close in about 30s", Urgency.NOW);
		}
		if (s.essenceCarried() > 0)
		{
			// Some altars stay open after the guardian is charged: use one, then hand in.
			AltarChooser.Choice choice = AltarChooser.choose(s, st);
			if (choice != null)
			{
				return new Instruction(Step.ENTER_ALTAR, Target.ALTAR_PORTAL, choice.primary,
					"Still open, craft and hand in before the rifts close", Urgency.NOW).withSecondary(choice.secondary);
			}
		}
		return Instruction.of(Step.GAME_OVER, Target.NONE, "Stay until the round ends so your points count");
	}

	// ---------------------------------------------------------------- helpers

	/** Running ticks a chisel run of this many essence roughly takes (about one essence per two ticks). */
	static final int TICKS_PER_ESSENCE = 2;

	/**
	 * True when you already carry a worthwhile load and stopping to chisel more would mean the best
	 * altar rotates before you get there, while going now still makes it.
	 */
	static boolean rotationRush(Snapshot s, PlannerSettings st)
	{
		if (s.essenceCarried() < st.minEssenceForAltar || s.altarTicksLeft < 0)
		{
			return false;
		}
		AltarChooser.Choice choice = AltarChooser.choose(s, st);
		if (choice == null || choice.viaTalisman)
		{
			return false;
		}
		Integer travel = s.altarTravelTicks.get(choice.primary);
		if (travel == null || travel < 0 || travel > s.altarTicksLeft)
		{
			return false;
		}
		int craftTicks = Math.min(60, (essenceGoal(s, st) - s.essenceCarried()) * TICKS_PER_ESSENCE);
		return travel + craftTicks > s.altarTicksLeft;
	}

	/** A portal is expected shortly and there's room to make it worth waiting for. */
	static boolean portalDueSoon(Snapshot s, PlannerSettings st)
	{
		return st.holdForPortal && !s.portalOpen && s.secondsToPortal > 0 && s.secondsToPortal <= st.portalSoonSeconds
			&& s.essenceRoom() >= Math.min(st.portalMinRoom, 10);
	}

	static boolean portalReachable(Snapshot s)
	{
		if (s.portalTravelTicks < 0 || s.portalTicksLeft < 0)
		{
			return true;
		}
		return s.portalTravelTicks < s.portalTicksLeft;
	}

	private static String nextPortalHint(Snapshot s)
	{
		if (s.secondsSincePortal < 0)
		{
			return "";
		}
		return "Next portal in about " + Math.max(0, 140 - s.secondsSincePortal) + "s";
	}

	public static int ticksToSeconds(int ticks)
	{
		return (int) Math.round(ticks * 0.6);
	}

	/** For tests and the panel: which altars are currently usable. */
	public static boolean usable(Snapshot s, Altar a)
	{
		return AltarChooser.usable(s).contains(a);
	}
}
