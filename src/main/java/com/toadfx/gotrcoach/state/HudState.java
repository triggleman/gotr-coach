package com.toadfx.gotrcoach.state;

/**
 * One reading of the GOTR HUD script (5980). The script receives the whole minigame state as
 * arguments, which is more reliable than reading widget text. Index 0 is the script id.
 */
public final class HudState
{
	public static final int SCRIPT_ID = 5980;
	private static final int ARG_COUNT = 12;

	public final int elementalEnergy;
	public final int catalyticEnergy;
	public final int power;
	public final int maxPower;
	/** Above 0 while a huge-remains portal is open. */
	public final int portalLocation;
	/** 1-4 for the active elemental altar, 0 when none is open. */
	public final int elementalIndex;
	/** 1-8 for the active catalytic altar, 0 when none is open. */
	public final int catalyticIndex;
	public final int guardians;
	public final int maxGuardians;
	/** Mining: ticks until the altars open. Crafting: ticks until they rotate. */
	public final int guardianTicks;
	/** Ticks until the open portal closes. */
	public final int portalTicks;
	/** Client tick this was read on. */
	public final int tick;

	private HudState(int[] v, int tick)
	{
		this.elementalEnergy = v[1];
		this.catalyticEnergy = v[2];
		this.power = v[3];
		this.maxPower = v[4];
		this.portalLocation = v[5];
		this.elementalIndex = v[6];
		this.catalyticIndex = v[7];
		this.guardians = v[8];
		this.maxGuardians = v[9];
		this.guardianTicks = v[10];
		this.portalTicks = v[11];
		this.tick = tick;
	}

	/** Parses script arguments, or returns null if they don't look like the GOTR HUD. */
	public static HudState parse(Object[] args, int tick)
	{
		if (args == null || args.length < ARG_COUNT)
		{
			return null;
		}
		int[] v = new int[ARG_COUNT];
		for (int i = 1; i < ARG_COUNT; i++)
		{
			Object o = args[i];
			if (o instanceof Integer)
			{
				v[i] = (Integer) o;
			}
			else
			{
				try
				{
					v[i] = Integer.parseInt(String.valueOf(o));
				}
				catch (NumberFormatException e)
				{
					return null;
				}
			}
		}
		return new HudState(v, tick);
	}

	public int powerPercent()
	{
		return maxPower <= 0 ? 0 : Math.min(100, power * 100 / maxPower);
	}

	public boolean portalOpen()
	{
		return portalLocation > 0;
	}

	public boolean altarsOpen()
	{
		return elementalIndex > 0 || catalyticIndex > 0;
	}

	/** Which phase the HUD numbers describe. */
	public GamePhase phase()
	{
		if (maxPower <= 0)
		{
			return GamePhase.WAITING;
		}
		if (power >= maxPower)
		{
			return GamePhase.CLOSING;
		}
		if (portalLocation == 0 && power == 0 && portalTicks == -1)
		{
			return GamePhase.ENDED;
		}
		if (power == 0 && portalLocation == -1)
		{
			return GamePhase.CLOSING;
		}
		if (!altarsOpen())
		{
			return power > 0 ? GamePhase.MINING : GamePhase.WAITING;
		}
		return GamePhase.CRAFTING;
	}
}
