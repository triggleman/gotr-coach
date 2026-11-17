package com.toadfx.gotrcoach.ui;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.coach.GameRecord;
import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.plan.AltarMode;
import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Instruction;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Strategy;
import com.toadfx.gotrcoach.plan.Target;
import com.toadfx.gotrcoach.plan.Urgency;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.GameTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The GOTR Coach sidebar tab: the current step, the live round, quick settings,
 * the last round's grade and tips, and recent history.
 */
public class GotrCoachPanel extends PluginPanel
{
	private static final Color ACCENT = new Color(0, 200, 255);
	private static final Color SOON = new Color(255, 210, 0);
	private static final Color NOW = new Color(255, 80, 60);
	private static final Color GOOD = new Color(90, 230, 110);
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final int HISTORY_ROWS = 8;

	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;
	private final ConfigManager configManager;

	private final JLabel stepTitle = new JLabel();
	private final JLabel stepDetail = new JLabel();
	private final JPanel roundRows = new JPanel();
	private final JPanel pointRows = new JPanel();
	private final JPanel lastRows = new JPanel();
	private final JPanel historyRows = new JPanel();
	private final JPanel sessionRows = new JPanel();
	private final JPanel bestRows = new JPanel();

	private final JComboBox<Strategy> strategyBox = new JComboBox<>(Strategy.values());
	private final JComboBox<AltarMode> altarBox = new JComboBox<>(AltarMode.values());
	private final JComboBox<GotrCoachConfig.PortalPriority> portalBox = new JComboBox<>(GotrCoachConfig.PortalPriority.values());
	private final JSpinner fragmentsSpinner = new JSpinner(new SpinnerNumberModel(120, 10, 300, 5));
	private final JButton fragmentsAuto = new JButton("Auto");
	private final JLabel fragmentsNote = new JLabel();
	private final JCheckBox depositBox = new JCheckBox("Deposit runes step");
	private final JCheckBox graceBox = new JCheckBox("Grace portal hold");
	private boolean syncing;

	public GotrCoachPanel(GotrCoachPlugin plugin, GotrCoachConfig config, ConfigManager configManager, BufferedImage logo)
	{
		super(true);
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);

		if (logo != null)
		{
			JLabel logoLabel = new JLabel(new ImageIcon(logo));
			logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
			JPanel logoRow = new JPanel(new BorderLayout());
			logoRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			logoRow.add(logoLabel, BorderLayout.CENTER);
			logoRow.setBorder(new EmptyBorder(0, 0, 8, 0));
			content.add(logoRow);
		}

		// Current step
		JPanel stepCard = card();
		stepTitle.setFont(FontManager.getRunescapeBoldFont());
		stepTitle.setForeground(ACCENT);
		stepDetail.setFont(FontManager.getRunescapeSmallFont());
		stepDetail.setForeground(Color.WHITE);
		stepCard.add(stepTitle);
		stepCard.add(Box.createVerticalStrut(2));
		stepCard.add(stepDetail);
		content.add(section("Next step", stepCard));

		content.add(section("Round", rows(roundRows)));
		content.add(section("Points", rows(pointRows)));
		content.add(section("Quick settings", settingsCard()));
		content.add(section("Session", sessionCard()));
		content.add(section("Last round", rows(lastRows)));
		content.add(section("Personal bests", rows(bestRows)));
		content.add(section("History", rows(historyRows)));

		JLabel credit = new JLabel("Made by ToadFx");
		credit.setFont(FontManager.getRunescapeSmallFont());
		credit.setForeground(new Color(120, 120, 120));
		credit.setBorder(new EmptyBorder(6, 2, 0, 0));
		content.add(credit);

		syncSettings();
		refresh();
	}

	// ------------------------------------------------------------ layout helpers

	private static JPanel card()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(6, 8, 6, 8));
		return p;
	}

	private static JPanel rows(JPanel holder)
	{
		holder.setLayout(new GridLayout(0, 1, 0, 1));
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.setBorder(new EmptyBorder(4, 8, 4, 8));
		return holder;
	}

	private static JPanel section(String title, JPanel body)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(0, 0, 8, 0));
		JLabel head = new JLabel(title);
		head.setFont(FontManager.getRunescapeSmallFont());
		head.setForeground(MUTED);
		head.setBorder(new EmptyBorder(0, 2, 3, 0));
		wrap.add(head, BorderLayout.NORTH);
		wrap.add(body, BorderLayout.CENTER);
		return wrap;
	}

	private static void row(JPanel holder, String left, String right, Color rightColor)
	{
		JPanel r = new JPanel(new BorderLayout());
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel l = new JLabel(left);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(MUTED);
		JLabel v = new JLabel(right);
		v.setFont(FontManager.getRunescapeSmallFont());
		v.setForeground(rightColor);
		r.add(l, BorderLayout.WEST);
		r.add(v, BorderLayout.EAST);
		holder.add(r);
	}

	private static void note(JPanel holder, String text, Color color)
	{
		JLabel l = new JLabel(html(text));
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		holder.add(l);
	}

	private static String html(String text)
	{
		String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width:" + (PANEL_WIDTH - 40) + "px'>" + safe + "</body></html>";
	}

	private JPanel sessionCard()
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.add(rows(sessionRows), BorderLayout.CENTER);
		JButton reset = new JButton("Reset session");
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setFocusPainted(false);
		reset.addActionListener(e ->
		{
			plugin.resetSession();
			javax.swing.SwingUtilities.invokeLater(this::refresh);
		});
		JPanel buttonRow = new JPanel(new BorderLayout());
		buttonRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttonRow.setBorder(new EmptyBorder(0, 8, 6, 8));
		buttonRow.add(reset, BorderLayout.CENTER);
		wrap.add(buttonRow, BorderLayout.SOUTH);
		return wrap;
	}

	private JPanel settingsCard()
	{
		JPanel p = card();
		p.add(labelled("Strategy", strategyBox));
		p.add(Box.createVerticalStrut(4));
		p.add(labelled("Altar choice", altarBox));
		p.add(Box.createVerticalStrut(4));
		p.add(labelled("Portal", portalBox));
		p.add(Box.createVerticalStrut(4));
		p.add(fragmentsRow());
		fragmentsNote.setFont(FontManager.getRunescapeSmallFont());
		fragmentsNote.setForeground(MUTED);
		fragmentsNote.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(fragmentsNote);
		p.add(Box.createVerticalStrut(4));
		for (JCheckBox box : new JCheckBox[]{depositBox, graceBox})
		{
			box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			box.setForeground(Color.WHITE);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(box);
		}

		strategyBox.addActionListener(e -> set("strategy", strategyBox.getSelectedItem()));
		altarBox.addActionListener(e -> set("altarMode", altarBox.getSelectedItem()));
		portalBox.addActionListener(e -> set("portalPriority", portalBox.getSelectedItem()));
		depositBox.addActionListener(e -> set("depositRunes", depositBox.isSelected()));
		graceBox.addActionListener(e -> set("graceHold", graceBox.isSelected()));
		return p;
	}

	/** Opening fragments: type a number or step by 5; Auto returns to the strategy default. */
	private JPanel fragmentsRow()
	{
		JPanel r = new JPanel(new BorderLayout(4, 0));
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		r.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel l = new JLabel("Fragments");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(MUTED);
		l.setPreferredSize(new Dimension(70, 20));
		fragmentsSpinner.setFont(FontManager.getRunescapeSmallFont());
		fragmentsAuto.setFont(FontManager.getRunescapeSmallFont());
		fragmentsAuto.setFocusPainted(false);
		fragmentsAuto.setToolTipText("Use the strategy's default (mass 168, team 120, solo 110)");
		r.add(l, BorderLayout.WEST);
		r.add(fragmentsSpinner, BorderLayout.CENTER);
		r.add(fragmentsAuto, BorderLayout.EAST);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		fragmentsSpinner.addChangeListener(e -> set("openingFragments", ((Number) fragmentsSpinner.getValue()).intValue()));
		fragmentsAuto.addActionListener(e -> set("openingFragments", 0));
		return r;
	}

	private JPanel labelled(String name, JComboBox<?> box)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		r.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel l = new JLabel(name);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(MUTED);
		l.setPreferredSize(new Dimension(70, 20));
		box.setFont(FontManager.getRunescapeSmallFont());
		r.add(l, BorderLayout.WEST);
		r.add(box, BorderLayout.CENTER);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		return r;
	}

	private void set(String key, Object value)
	{
		if (syncing || value == null)
		{
			return;
		}
		configManager.setConfiguration(GotrCoachConfig.GROUP, key, value);
	}

	/** Pull the quick settings from the config (after a change in the config panel). */
	public void syncSettings()
	{
		syncing = true;
		try
		{
			strategyBox.setSelectedItem(config.strategy());
			altarBox.setSelectedItem(config.altarMode());
			portalBox.setSelectedItem(config.portalPriority());
			int saved = config.openingFragments();
			int shown = saved > 0 ? saved : config.strategy().defaultOpeningFragments();
			fragmentsSpinner.setValue(Math.max(10, Math.min(300, shown)));
			fragmentsNote.setText(saved > 0 ? "Custom, Auto resets it" : "Auto: strategy default");
			depositBox.setSelected(config.depositRunes());
			graceBox.setSelected(config.graceHold());
		}
		finally
		{
			syncing = false;
		}
	}

	// ------------------------------------------------------------ refresh (Swing thread)

	public void refresh()
	{
		GameTracker tracker = plugin.getTracker();
		Snapshot s = plugin.getSnapshot();
		Instruction step = plugin.getInstruction();

		// Step
		if (tracker == null || tracker.getPhase() == GamePhase.IDLE)
		{
			stepTitle.setText("Not in Guardians of the Rift");
			stepTitle.setForeground(MUTED);
			stepDetail.setText(html("Head to the Temple of the Eye to start."));
		}
		else
		{
			Color c = step.getUrgency() == Urgency.NOW ? NOW : step.getUrgency() == Urgency.SOON ? SOON : ACCENT;
			if (step.getTarget() == Target.REMAINS_PORTAL)
			{
				c = config.portalColor();
			}
			stepTitle.setText(html(step.title()));
			stepTitle.setForeground(c);
			String detail = step.getDetail();
			if (step.getSecondaryAltar() != null)
			{
				detail += (detail.isEmpty() ? "" : ". ") + "Backup: " + step.getSecondaryAltar().getLabel();
			}
			stepDetail.setText(html(detail));
		}

		// Round
		roundRows.removeAll();
		if (tracker != null && s.inGame())
		{
			row(roundRows, "Game", GotrCoachPlugin.formatTime(s.gameSeconds), Color.WHITE);
			if (s.phase == GamePhase.MINING)
			{
				row(roundRows, "Altars open", Math.max(0, s.secondsUntilAltars) + "s", Color.WHITE);
			}
			else
			{
				String open = (s.activeElemental == null ? "-" : s.activeElemental.getLabel()) + " / "
					+ (s.activeCatalytic == null ? "-" : s.activeCatalytic.getLabel());
				row(roundRows, "Open altars", open, Color.WHITE);
				if (s.altarTicksLeft >= 0)
				{
					row(roundRows, "Rotate in", Planner.ticksToSeconds(s.altarTicksLeft) + "s", Color.WHITE);
				}
			}
			if (s.portalOpen)
			{
				row(roundRows, "Portal", s.portalTicksLeft >= 0 ? Planner.ticksToSeconds(s.portalTicksLeft) + "s left" : "open",
					config.portalColor());
			}
			else
			{
				int next = tracker.getNextPortalGameSecond();
				if (next >= 0)
				{
					int in = next - s.gameSeconds;
					row(roundRows, "Next portal", in >= 0 ? "~" + in + "s" : "any moment", in <= 10 ? SOON : Color.WHITE);
				}
			}
			row(roundRows, "Portals", tracker.getPortalsThisGame() + " this round", MUTED);
			if (!s.talismans.isEmpty())
			{
				StringBuilder t = new StringBuilder();
				for (com.toadfx.gotrcoach.model.Altar a : s.talismans)
				{
					t.append(t.length() > 0 ? ", " : "").append(a.getLabel());
				}
				row(roundRows, "Talisman", t.toString(), new Color(200, 120, 255));
			}
			row(roundRows, "Guardian power", s.powerPercent + "%", s.powerPercent >= 60 ? GOOD : Color.WHITE);
			row(roundRows, "Guardians up", s.guardians + " / " + s.guardiansMax, Color.WHITE);
			row(roundRows, "Essence", s.essenceCarried() + " (" + s.essenceInPouches + " in pouches)", Color.WHITE);
			row(roundRows, "Fragments", String.valueOf(s.fragments), Color.WHITE);
			int xp = plugin.getRecorder().currentXp();
			if (xp >= 0)
			{
				row(roundRows, "XP this round", String.format("%,d", xp), GOOD);
			}
		}
		else if (tracker != null && tracker.getPhase() != GamePhase.IDLE)
		{
			row(roundRows, "Rift opens", s.countdownSeconds >= 0 ? s.countdownSeconds + "s" : "waiting", SOON);
			int cells = config.unchargedCellsWanted();
			row(roundRows, "Pickaxe", s.hasPickaxe ? "OK" : "MISSING", s.hasPickaxe ? GOOD : NOW);
			row(roundRows, "Chisel", s.hasChisel ? "OK" : "MISSING", s.hasChisel ? GOOD : NOW);
			row(roundRows, "Uncharged cells", s.unchargedCells + " / " + cells, s.unchargedCells >= cells ? GOOD : NOW);
		}
		else
		{
			note(roundRows, "No round running.", MUTED);
		}

		// Points
		pointRows.removeAll();
		if (s.inGame() || s.phase == GamePhase.ENDED)
		{
			boolean capE = EnergyMath.isCapped(Alignment.ELEMENTAL, s.energyElemental, s.energyCatalytic, s.yewLantern);
			boolean capC = EnergyMath.isCapped(Alignment.CATALYTIC, s.energyElemental, s.energyCatalytic, s.yewLantern);
			row(pointRows, "Elemental energy", s.energyElemental + (capE ? " (capped)" : ""), capE ? SOON : Color.WHITE);
			row(pointRows, "Catalytic energy", s.energyCatalytic + (capC ? " (capped)" : ""), capC ? SOON : Color.WHITE);
			int[] after = EnergyMath.afterHandIn(s.energyElemental, s.energyCatalytic, s.elementalStones,
				s.catalyticStones, s.polyStones, s.yewLantern);
			row(pointRows, "Points (with stones)", String.format("%.1f E / %.1f C", after[0] / 100.0, after[1] / 100.0), GOOD);
			int cap = EnergyMath.totalCap(s.yewLantern);
			row(pointRows, "Room to cap", Math.max(0, cap - after[0] - after[1]) + " energy", MUTED);
		}
		if (s.bankedElemental >= 0)
		{
			row(pointRows, "Banked", s.bankedElemental + " E / " + s.bankedCatalytic + " C", Color.WHITE);
			row(pointRows, "Searches", String.valueOf(Math.min(s.bankedElemental, s.bankedCatalytic)), Color.WHITE);
		}
		else if (pointRows.getComponentCount() == 0)
		{
			note(pointRows, "Banked points show after a round, or use Check on the Rewards Guardian.", MUTED);
		}

		// Last round
		lastRows.removeAll();
		GameRecord last = plugin.getRecorder().getLast();
		if (last == null || last.score < 0)
		{
			note(lastRows, "Finish a round to get a grade.", MUTED);
		}
		else
		{
			row(lastRows, "Grade", last.grade + " (" + last.score + ")", gradeColor(last.grade));
			row(lastRows, "Points", String.format("%.1f", last.points()), Color.WHITE);
			row(lastRows, "Time", GotrCoachPlugin.formatTime(last.gameSeconds), Color.WHITE);
			row(lastRows, "Portals", last.portalsEntered + " / " + last.portalsSpawned, Color.WHITE);
			if (last.xpGained > 0)
			{
				row(lastRows, "XP", String.format("%,d", last.xpGained), GOOD);
			}
			row(lastRows, "Crafting started", last.craftStartSecond >= 0 ? last.craftStartSecond + "s" : "-", Color.WHITE);
			for (String tip : last.tips)
			{
				note(lastRows, "• " + tip, MUTED);
			}
		}

		// Session
		sessionRows.removeAll();
		com.toadfx.gotrcoach.coach.Session session = plugin.getSession();
		long now = System.currentTimeMillis();
		if (session.isEmpty())
		{
			note(sessionRows, "Stats start with your first round.", MUTED);
		}
		else
		{
			long mins = session.elapsedMillis(now) / 60_000;
			row(sessionRows, "Time", (mins / 60) + "h " + (mins % 60) + "m", Color.WHITE);
			row(sessionRows, "Rounds", session.getRounds() + " (" + session.getWins() + " won)", Color.WHITE);
			row(sessionRows, "XP", String.format("%,d", session.getXp()), GOOD);
			row(sessionRows, "XP / hr", String.format("%,.0f", session.xpPerHour(now)), GOOD);
			row(sessionRows, "Points", String.format("%.1f", session.getPoints()), Color.WHITE);
			row(sessionRows, "Points / hr", String.format("%.1f", session.pointsPerHour(now)), Color.WHITE);
			row(sessionRows, "Rounds / hr", String.format("%.1f", session.roundsPerHour(now)), MUTED);
			if (session.averageScore() >= 0)
			{
				row(sessionRows, "Avg grade", String.format("%.0f", session.averageScore()), ACCENT);
			}
		}

		// Personal bests
		bestRows.removeAll();
		com.toadfx.gotrcoach.coach.PersonalBests b = plugin.getBests();
		if (b.isEmpty())
		{
			note(bestRows, "Finish a round to set your bests.", MUTED);
		}
		else
		{
			row(bestRows, "Most points", String.format("%.1f", b.mostEnergy / 100.0), new Color(255, 215, 0));
			if (b.mostXp > 0)
			{
				row(bestRows, "Most XP", String.format("%,d", b.mostXp), new Color(255, 215, 0));
			}
			if (b.fastestWin > 0)
			{
				row(bestRows, "Fastest round", GotrCoachPlugin.formatTime(b.fastestWin), new Color(255, 215, 0));
			}
			if (b.bestScore >= 0)
			{
				row(bestRows, "Best grade", b.bestGrade + " (" + b.bestScore + ")", gradeColor(b.bestGrade));
			}
		}

		// History
		historyRows.removeAll();
		List<GameRecord> history = plugin.getRecorder().getHistory();
		if (history.isEmpty())
		{
			note(historyRows, "No rounds saved yet.", MUTED);
		}
		else
		{
			double[] avg = plugin.getRecorder().averages(config.coachAverage());
			if (avg != null)
			{
				row(historyRows, "Average (" + (int) avg[3] + ")", String.format("%.0f · %.1f pts · %s", avg[0], avg[1],
					GotrCoachPlugin.formatTime((int) avg[2])), ACCENT);
				double avgXp = plugin.getRecorder().averageXp(config.coachAverage());
				if (avgXp > 0)
				{
					row(historyRows, "Average XP", String.format("%,.0f per round", avgXp), ACCENT);
				}
			}
			int shown = 0;
			for (GameRecord r : history)
			{
				if (shown++ >= HISTORY_ROWS)
				{
					break;
				}
				String xpText = r.xpGained > 0 ? String.format(" · %,dxp", r.xpGained) : "";
				row(historyRows, r.grade + "  " + GotrCoachPlugin.formatTime(r.gameSeconds),
					String.format("%.1f pts · %d/%d", r.points(), r.portalsEntered, r.portalsSpawned) + xpText, gradeColor(r.grade));
			}
		}

		revalidate();
		repaint();
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
}
