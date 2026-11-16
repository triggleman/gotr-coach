package com.toadfx.gotrcoach.plan;

import com.toadfx.gotrcoach.model.Altar;
import java.util.Objects;

/** One planner result: what to do, where, and why. */
public final class Instruction
{
	public static final Instruction NONE = new Instruction(Step.OUTSIDE, Target.NONE, null, "", Urgency.NORMAL);

	private final Step step;
	private final Target target;
	private final Altar altar;
	private final String detail;
	private final Urgency urgency;
	private Altar secondaryAltar;

	public Instruction(Step step, Target target, Altar altar, String detail, Urgency urgency)
	{
		this.step = step;
		this.target = target;
		this.altar = altar;
		this.detail = detail == null ? "" : detail;
		this.urgency = urgency;
	}

	public static Instruction of(Step step, Target target, String detail)
	{
		return new Instruction(step, target, null, detail, Urgency.NORMAL);
	}

	public Step getStep()
	{
		return step;
	}

	public Target getTarget()
	{
		return target;
	}

	public Altar getAltar()
	{
		return altar;
	}

	public String getDetail()
	{
		return detail;
	}

	public Urgency getUrgency()
	{
		return urgency;
	}

	public Altar getSecondaryAltar()
	{
		return secondaryAltar;
	}

	public Instruction withSecondary(Altar secondary)
	{
		this.secondaryAltar = secondary;
		return this;
	}

	public Instruction withUrgency(Urgency u)
	{
		Instruction i = new Instruction(step, target, altar, detail, u);
		i.secondaryAltar = secondaryAltar;
		return i;
	}

	/** Title shown in the panel, e.g. "Enter the altar: Fire". */
	public String title()
	{
		if (altar != null && (step == Step.ENTER_ALTAR))
		{
			return "Enter " + altar.getLabel() + " altar";
		}
		return step.getLabel();
	}

	/** Two instructions are the same step if the player would do the same thing. */
	public boolean sameAs(Instruction other)
	{
		return other != null && step == other.step && target == other.target && altar == other.altar;
	}

	@Override
	public String toString()
	{
		return title() + (detail.isEmpty() ? "" : " - " + detail);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Instruction))
		{
			return false;
		}
		Instruction i = (Instruction) o;
		return sameAs(i) && detail.equals(i.detail) && urgency == i.urgency;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(step, target, altar, detail, urgency);
	}
}
