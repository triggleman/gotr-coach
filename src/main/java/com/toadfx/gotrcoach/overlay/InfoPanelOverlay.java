package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.coach.GameRecord;
import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Instruction;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Urgency;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.GameTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The info panel: current step, timers, points, essence and the last round's grade. */
public class InfoPanelOverlay extends OverlayPanel
{
	private static final Color ACCENT = new Color(0, 200, 255);
	private static final Color MUTED = new Color(170, 170, 170);
	private static final Color SOON = new Color(255, 210, 0);
	private static final Color NOW = new Color(255, 80, 60);
	private static final Color GOOD = new Color(90, 230, 110);

	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;

	@Inject
	public InfoPanelOverlay(GotrCoachPlugin plugin, GotrCoachConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GameTracker tracker = plugin.getTracker();
		if (!config.showPanel() || tracker == null || tracker.getPhase() == GamePhase.IDLE)
		{
			return null;
		}
		Snapshot s = plugin.getSnapshot();
		Instruction step = plugin.getInstruction();
		panelComponent.setPreferredSize(new Dimension(config.compactPanel() ? 190 : 215, 0));

		Color stepColor = step.getUrgency() == Urgency.NOW ? NOW : step.getUrgency() == Urgency.SOON ? SOON : ACCENT;
		if (step.getTarget() == com.toadfx.gotrcoach.plan.Target.REMAINS_PORTAL)
		{
			stepColor = config.portalColor();
		}
		panelComponent.getChildren().add(TitleComponent.builder().text(step.title()).color(stepColor).build());
		if (!step.getDetail().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left(step.getDetail()).leftColor(Color.WHITE).build());
		}
		if (step.getSecondaryAltar() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Backup").right(step.getSecondaryAltar().getLabel()).leftColor(MUTED).build());
		}
		if (config.compactPanel())
		{
			return super.render(g);
		}

		if (config.panelTimers())
		{
			timers(tracker, s);
		}
		if (config.panelPoints() && (s.inGame() || s.phase == GamePhase.ENDED))
		{
			points(s);
		}
		if (config.panelPoints() && s.bankedElemental >= 0)
		{
			int searches = Math.min(s.bankedElemental, s.bankedCatalytic);
			line("Banked", s.bankedElemental + " E / " + s.bankedCatalytic + " C", MUTED);
			line("Searches", String.valueOf(searches), MUTED);
		}
		if (!s.talismans.isEmpty() && s.inGame())
		{
			line("Talisman", talismanList(s), new Color(200, 120, 255));
		}
		if (config.panelEssence() && s.inGame())
		{
			String ess = s.essenceCarried() + (s.hasPouches ? " (" + s.essenceInPouches + " in pouches)" : "");
			line("Essence", ess, Color.WHITE);
			line("Fragments", String.valueOf(s.fragments), Color.WHITE);
		}
		int xp = plugin.getRecorder().currentXp();
		if (config.panelPoints() && s.inGame() && xp >= 0)
		{
			line("XP this round", String.format("%,d", xp), GOOD);
		}
		if (config.coaching() && config.coachPanel() && !s.inGame())
		{
			lastRound();
		}
		if (config.panelCredit())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Made by ToadFx").leftColor(new Color(110, 110, 110)).build());
		}
		return super.render(g);
	}

	private void timers(GameTracker tracker, Snapshot s)
	{
		if (s.phase == GamePhase.WAITING && s.countdownSeconds >= 0)
		{
			line("Rift opens", s.countdownSeconds + "s", SOON);
			return;
		}
		if (!s.inGame())
		{
			return;
		}
		line("Game", GotrCoachPlugin.formatTime(s.gameSeconds), Color.WHITE);
		if (s.phase == GamePhase.MINING && s.secondsUntilAltars >= 0)
		{
			line("Altars open", s.secondsUntilAltars + "s", s.secondsUntilAltars <= 10 ? SOON : Color.WHITE);
		}
		if (s.phase == GamePhase.CRAFTING && s.altarTicksLeft >= 0)
		{
			String open = (s.activeElemental == null ? "-" : s.activeElemental.getLabel()) + " / "
				+ (s.activeCatalytic == null ? "-" : s.activeCatalytic.getLabel());
			line(open, Planner.ticksToSeconds(s.altarTicksLeft) + "s", Color.WHITE);
		}
		if (s.portalOpen)
		{
			String dir = tracker.getPortalDirection();
			line("Portal" + (dir == null || dir.isEmpty() ? "" : " " + dir),
				s.portalTicksLeft >= 0 ? Planner.ticksToSeconds(s.portalTicksLeft) + "s left" : "open", GOOD);
		}
		else if (s.phase == GamePhase.CRAFTING || s.phase == GamePhase.MINING)
		{
			int next = tracker.getNextPortalGameSecond();
			if (next >= 0)
			{
				int in = next - s.gameSeconds;
				line("Next portal", in >= 0 ? "~" + in + "s" : "any moment", in <= 10 ? SOON : MUTED);
			}
		}
		line("Guardian", s.powerPercent + "%", s.powerPercent >= 60 ? GOOD : Color.WHITE);
		line("Guardians up", s.guardians + " / " + s.guardiansMax, MUTED);
	}

	private void points(Snapshot s)
	{
		int cap = EnergyMath.totalCap(s.yewLantern);
		boolean capE = EnergyMath.isCapped(Alignment.ELEMENTAL, s.energyElemental, s.energyCatalytic, s.yewLantern);
		boolean capC = EnergyMath.isCapped(Alignment.CATALYTIC, s.energyElemental, s.energyCatalytic, s.yewLantern);
		line("Elemental", s.energyElemental + (capE ? " (capped)" : ""), capE ? SOON : Color.WHITE);
		line("Catalytic", s.energyCatalytic + (capC ? " (capped)" : ""), capC ? SOON : Color.WHITE);
		int[] after = EnergyMath.afterHandIn(s.energyElemental, s.energyCatalytic, s.elementalStones, s.catalyticStones,
			s.polyStones, s.yewLantern);
		int total = after[0] + after[1];
		String potential = String.format("%.1f E / %.1f C", after[0] / 100.0, after[1] / 100.0);
		line("Points", potential, total >= EnergyMath.XP_THRESHOLD ? GOOD : Color.WHITE);
		line("To cap", Math.max(0, cap - total) + " energy", MUTED);
	}

	private void lastRound()
	{
		GameRecord last = plugin.getRecorder().getLast();
		if (last == null || last.score < 0)
		{
			return;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Last round: " + last.grade + " (" + last.score + ")").color(gradeColor(last.grade)).build());
		line("Points", String.format("%.1f", last.points()), Color.WHITE);
		line("Time", GotrCoachPlugin.formatTime(last.gameSeconds), Color.WHITE);
		line("Portals", last.portalsEntered + " / " + last.portalsSpawned, Color.WHITE);
		if (last.xpGained > 0)
		{
			line("XP", String.format("%,d", last.xpGained), GOOD);
		}
		int shown = 0;
		for (String tip : last.tips)
		{
			if (shown++ >= 2)
			{
				break;
			}
			panelComponent.getChildren().add(LineComponent.builder().left("• " + tip).leftColor(MUTED).build());
		}
		double[] avg = plugin.getRecorder().averages(config.coachAverage());
		if (avg != null && avg[3] > 1)
		{
			line("Avg (" + (int) avg[3] + ")", String.format("%.0f · %.1f pts", avg[0], avg[1]), MUTED);
		}
	}

	static String talismanList(Snapshot s)
	{
		StringBuilder sb = new StringBuilder();
		for (com.toadfx.gotrcoach.model.Altar a : s.talismans)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(a.getLabel());
		}
		return sb.toString();
	}

	private static Color gradeColor(String grade)
	{
		switch (grade)
		{
			case "S":
				return new Color(255, 215, 0);
			case "A":
				return GOOD;
			case "B":
				return ACCENT;
			case "C":
				return SOON;
			default:
				return NOW;
		}
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(rightColor).build());
	}
}
