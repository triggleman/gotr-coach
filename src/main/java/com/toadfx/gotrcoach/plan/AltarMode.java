package com.toadfx.gotrcoach.plan;

/** How the altar chooser ranks the two open altars. */
public enum AltarMode
{
	/** Wiki even-points rule: Blood/Death, then Fire/Earth, then whichever type you have fewer points in. */
	EVEN_POINTS("Even points (wiki)"),
	/** Always the type you have fewer points in. */
	BALANCED("Strict balance"),
	/** The altar that gives the best cell. */
	BEST_CELL("Best cell"),
	/** The altar that gives the most Runecraft XP per essence. */
	EXPERIENCE("Most XP"),
	/** Elemental only (e.g. for combination runes). */
	ELEMENTAL_ONLY("Elemental only"),
	/** Catalytic only. */
	CATALYTIC_ONLY("Catalytic only");

	private final String label;

	AltarMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
