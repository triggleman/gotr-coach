package com.toadfx.gotrcoach.plan;

/** Which wiki strategy the planner follows. */
public enum Strategy
{
	MASS("Mass world"),
	TEAM("Team (wiki general)"),
	SOLO("Solo / small group");

	private final String label;

	Strategy(String label)
	{
		this.label = label;
	}

	/** Fragments to mine before the first craft. Wiki: mass 165-170, team ~120, solo ~110. */
	public int defaultOpeningFragments()
	{
		switch (this)
		{
			case MASS:
				return 168;
			case SOLO:
				return 110;
			default:
				return 120;
		}
	}

	/** Seconds before the altars open to stop mining and start crafting essence. */
	public int defaultCraftLeadSeconds()
	{
		switch (this)
		{
			case MASS:
				return 15;
			case SOLO:
				return 40;
			default:
				return 35;
		}
	}

	@Override
	public String toString()
	{
		return label;
	}
}
