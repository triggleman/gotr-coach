package com.toadfx.gotrcoach.model;

/** The two kinds of guardian energy, and so the two kinds of reward points. */
public enum Alignment
{
	ELEMENTAL("Elemental"),
	CATALYTIC("Catalytic");

	private final String label;

	Alignment(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	public Alignment other()
	{
		return this == ELEMENTAL ? CATALYTIC : ELEMENTAL;
	}
}
