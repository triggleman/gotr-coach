package com.toadfx.gotrcoach.plan;

/** Every instruction the planner can give. */
public enum Step
{
	OUTSIDE("Go to Guardians of the Rift"),
	GET_PICKAXE("Get a pickaxe"),
	GET_CHISEL("Get a chisel"),
	TAKE_UNCHARGED_CELLS("Take uncharged cells"),
	TAKE_WEAK_CELL("Take a weak cell"),
	REPAIR_POUCHES("Repair your pouches"),
	WAIT_FOR_START("Wait for the rift to open"),
	ENTER_BARRIER("Enter the barrier"),
	PLACE_CELL("Place your cell"),
	BUILD_GUARDIAN("Build a guardian"),
	REPAIR_BARRIER("Repair the broken barrier"),
	MINE_FRAGMENTS("Mine guardian fragments"),
	CRAFT_ESSENCE("Craft guardian essence"),
	ENTER_ALTAR("Enter the altar"),
	CRAFT_RUNES("Craft runes"),
	EMPTY_POUCHES("Empty pouches and craft"),
	LEAVE_ALTAR("Leave the altar"),
	POWER_UP("Power up the Great Guardian"),
	HOLD_STONES("Hold your stones"),
	DEPOSIT_RUNES("Deposit runes"),
	WAIT_FOR_PORTAL("Portal soon, stay out of altars"),
	ENTER_REMAINS_PORTAL("Take the portal"),
	MINE_HUGE_REMAINS("Mine essence"),
	LEAVE_REMAINS("Leave the huge remains"),
	ENERGY_CAPPED("Energy capped"),
	HAND_IN_NOW("Hand in now"),
	GAME_OVER("Game over");

	private final String label;

	Step(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
