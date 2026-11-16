package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.PlannerSettings;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.GameTracker;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The whole round on one bar, split into labelled sections: Mine, Craft essence, the altar
 * rotations, and portals (past, open and predicted). The section you're in shows a countdown,
 * the white line is now, and a goal bar sits underneath.
 */
public class TimelineOverlay extends Overlay
{
	private static final int MINING_SECONDS = 120;
	private static final int PORTAL_OPEN_SECONDS = 25;
	private static final int PORTAL_TOLERANCE = 5;
	private static final Color MINE = new Color(90, 160, 240);
	private static final Color CRAFT = new Color(150, 120, 230);
	private static final Color ALTARS = new Color(55, 60, 70);
	private static final Color ALTARS_ALT = new Color(68, 74, 86);
	private static final Color ELAPSED_TINT = new Color(70, 200, 110, 70);
	private static final Color PORTAL = new Color(255, 215, 0);
	private static final Color GOAL_BELOW = new Color(220, 70, 60);
	private static final Color GOAL_MET = new Color(70, 200, 110);

	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;

	/** One labelled stretch of the bar, in game seconds. */
	private static final class Section
	{
		final int from;
		final int to;
		final Color fill;
		final String label;
		final Color text;

		Section(int from, int to, Color fill, String label, Color text)
		{
			this.from = from;
			this.to = to;
			this.fill = fill;
			this.label = label;
			this.text = text;
		}
	}

	@Inject
	public TimelineOverlay(GotrCoachPlugin plugin, GotrCoachConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GameTracker tracker = plugin.getTracker();
		if (!config.showTimeline() || tracker == null || tracker.getPhase() == GamePhase.IDLE)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Snapshot s = plugin.getSnapshot();
		int width = config.timelineWidth();
		int barH;
		Font headFont;
		Font barFont;
		switch (config.timelineSize())
		{
			case SMALL:
				barH = 12;
				headFont = FontManager.getRunescapeSmallFont();
				barFont = FontManager.getRunescapeSmallFont();
				break;
			case LARGE:
				barH = 22;
				headFont = FontManager.getRunescapeBoldFont();
				barFont = FontManager.getRunescapeFont();
				break;
			default:
				barH = 16;
				headFont = FontManager.getRunescapeFont();
				barFont = FontManager.getRunescapeSmallFont();
				break;
		}
		g.setFont(headFont);
		FontMetrics fm = g.getFontMetrics();
		int textH = fm.getHeight();
		int pad = 4;
		int alpha = (int) Math.round(config.timelineOpacity() * 2.55);
		Color bg = new Color(20, 20, 24, alpha);

		if (!s.inGame())
		{
			if (!config.timelineBetween())
			{
				return null;
			}
			int h = textH + pad * 2;
			g.setColor(bg);
			g.fillRoundRect(0, 0, width, h, 6, 6);
			String left = s.phase == GamePhase.ENDED ? "Round over" : "Next round";
			String right = s.countdownSeconds >= 0 ? clock(s.countdownSeconds) : "waiting";
			text(g, left, pad, pad + fm.getAscent(), Color.WHITE);
			text(g, right, width - pad - fm.stringWidth(right), pad + fm.getAscent(),
				s.countdownSeconds >= 0 && s.countdownSeconds <= 10 ? PORTAL : Color.WHITE);
			return new Dimension(width, h);
		}

		boolean goal = config.timelineGoal();
		int goalH = goal ? Math.max(5, barH / 2) : 0;
		int h = pad + textH + 2 + barH + (goal ? 3 + goalH : 0) + pad;
		g.setColor(bg);
		g.fillRoundRect(0, 0, width, h, 6, 6);

		int window = config.timelineWindow();
		double exact = plugin.getSmoothGameSeconds();
		int now = (int) exact;
		double start = Math.max(0, exact - window * 0.4);
		double end = start + window;
		int x0 = pad;
		int bw = width - pad * 2;
		int by = pad + textH + 2;
		PlannerSettings settings = plugin.currentSettings();
		int craftAt = Math.max(0, Math.min(MINING_SECONDS, MINING_SECONDS - settings.craftLeadSeconds()));

		// Header: what's happening now and how long it lasts, plus the game clock.
		text(g, header(s, tracker, now, craftAt), x0, pad + fm.getAscent(), Color.WHITE);
		String gameClock = clock(now);
		text(g, gameClock, width - pad - fm.stringWidth(gameClock), pad + fm.getAscent(), new Color(200, 200, 200));

		// Sections.
		List<Section> sections = new ArrayList<>();
		sections.add(new Section(0, craftAt, MINE, countdownLabel("Mine", 0, craftAt, now), Color.WHITE));
		if (craftAt < MINING_SECONDS)
		{
			sections.add(new Section(craftAt, MINING_SECONDS, CRAFT, countdownLabel("Craft", craftAt, MINING_SECONDS, now), Color.WHITE));
		}
		addAltarSections(sections, s, now, (int) end);

		g.setColor(ALTARS);
		g.fillRect(x0, by, bw, barH);
		for (Section sec : sections)
		{
			fillSpan(g, sec.from, sec.to, start, end, x0, bw, by, barH, sec.fill);
		}
		// Tint the time already gone.
		fillSpan(g, (int) start, now, start, end, x0, bw, by, barH, ELAPSED_TINT);

		// Portals on top.
		List<Section> portals = new ArrayList<>();
		if (config.timelinePortals())
		{
			for (int t : tracker.getPortalSpawnSeconds())
			{
				boolean open = s.portalOpen && t == tracker.getLastPortalGameSecond();
				int close = open && s.portalTicksLeft >= 0 ? now + Planner.ticksToSeconds(s.portalTicksLeft) : t + PORTAL_OPEN_SECONDS;
				String label = open ? "Portal " + clock(Math.max(0, close - now)) : "Portal";
				portals.add(new Section(t, close, open ? config.portalColor() : withAlpha(PORTAL, 200), label,
					open ? Color.WHITE : Color.BLACK));
			}
			int next = tracker.getNextPortalGameSecond();
			if (next >= 0 && !s.portalOpen)
			{
				for (int t = next; t < (int) end; t += GameTracker.PORTAL_INTERVAL_SECONDS)
				{
					String label = t == next ? "Portal ~" + clock(Math.max(0, t - now)) : "Portal?";
					portals.add(new Section(t - PORTAL_TOLERANCE, t + PORTAL_OPEN_SECONDS, withAlpha(PORTAL, 90), label, PORTAL));
				}
			}
			for (Section p : portals)
			{
				fillSpan(g, p.from, p.to, start, end, x0, bw, by, barH, p.fill);
			}
		}

		// Section dividers.
		g.setColor(new Color(0, 0, 0, 120));
		for (Section sec : sections)
		{
			int dx = toX(sec.to, start, end, x0, bw);
			if (sec.to > start && sec.to < end)
			{
				g.fillRect(dx, by, 1, barH);
			}
		}

		// Labels, clipped to their own section so they never spill over.
		g.setFont(barFont);
		FontMetrics bfm = g.getFontMetrics();
		for (Section sec : sections)
		{
			int[] free = freeSpan(sec, portals, start, end);
			if (free != null)
			{
				label(g, bfm, new Section(free[0], free[1], sec.fill, sec.label, sec.text), start, end, x0, bw, by, barH);
			}
		}
		for (Section p : portals)
		{
			label(g, bfm, p, start, end, x0, bw, by, barH);
		}

		// Now.
		int nx = toX(exact, start, end, x0, bw);
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(2));
		g.drawLine(nx, by - 3, nx, by + barH + 2);

		// Goal bar.
		if (goal)
		{
			drawGoal(g, s, settings, x0, bw, by + barH + 3, goalH, alpha, headFont);
		}
		return new Dimension(width, h);
	}

	/** Altar rotations: the current one ends when the HUD says; later ones are shown as one stretch. */
	private static void addAltarSections(List<Section> out, Snapshot s, int now, int end)
	{
		if (s.phase != GamePhase.CRAFTING && s.phase != GamePhase.CLOSING)
		{
			out.add(new Section(MINING_SECONDS, Math.max(end, MINING_SECONDS + 1), ALTARS, "Altars", Color.WHITE));
			return;
		}
		int rotateAt = s.altarTicksLeft >= 0 ? now + Planner.ticksToSeconds(s.altarTicksLeft) : -1;
		String open = altarPair(s);
		if (rotateAt < 0)
		{
			out.add(new Section(MINING_SECONDS, Math.max(end, MINING_SECONDS + 1), ALTARS, open, Color.WHITE));
			return;
		}
		out.add(new Section(MINING_SECONDS, rotateAt, ALTARS_ALT, open + " " + clock(Math.max(0, rotateAt - now)), Color.WHITE));
		if (rotateAt < end)
		{
			out.add(new Section(rotateAt, end, ALTARS, "Next altars", new Color(180, 180, 180)));
		}
	}

	private static String altarPair(Snapshot s)
	{
		String e = s.activeElemental == null ? null : s.activeElemental.getLabel();
		String c = s.activeCatalytic == null ? null : s.activeCatalytic.getLabel();
		if (e == null && c == null)
		{
			return "Altars";
		}
		if (e == null || c == null)
		{
			return e == null ? c : e;
		}
		return e + " / " + c;
	}

	private String header(Snapshot s, GameTracker tracker, int now, int craftAt)
	{
		switch (s.phase)
		{
			case MINING:
				if (now < craftAt)
				{
					return "Mining · craft in " + clock(craftAt - now);
				}
				return "Craft essence · altars in " + clock(Math.max(0, s.secondsUntilAltars));
			case CLOSING:
				return "Closing · hand in now";
			default:
				if (s.portalOpen && s.portalTicksLeft >= 0)
				{
					return "Portal open · " + clock(Planner.ticksToSeconds(s.portalTicksLeft)) + " left";
				}
				String open = altarPair(s);
				if (s.altarTicksLeft >= 0)
				{
					return open + " · rotate in " + clock(Planner.ticksToSeconds(s.altarTicksLeft));
				}
				return open;
		}
	}

	/** "Mine 0:23" while you're in that section, just "Mine" otherwise. */
	private static String countdownLabel(String name, int from, int to, int now)
	{
		if (now >= from && now < to)
		{
			return name + " " + clock(to - now);
		}
		return name;
	}

	private void drawGoal(Graphics2D g, Snapshot s, PlannerSettings settings, int x0, int bw, int gy, int goalH, int alpha, Font restore)
	{
		int have;
		int want;
		String label;
		if (s.phase == GamePhase.MINING)
		{
			have = s.fragments;
			want = settings.openingFragments();
			label = have + " / " + want + " fragments";
		}
		else
		{
			have = s.energyElemental + s.energyCatalytic;
			want = config.energyGoal() > 0 ? config.energyGoal() : EnergyMath.totalCap(s.yewLantern);
			label = String.format("%.1f / %.1f points", have / 100.0, want / 100.0);
		}
		g.setColor(new Color(60, 60, 60, alpha));
		g.fillRect(x0, gy, bw, goalH);
		double frac = want <= 0 ? 0 : Math.min(1.0, have / (double) want);
		g.setColor(have >= want ? GOAL_MET : GOAL_BELOW);
		g.fillRect(x0, gy, (int) (bw * frac), goalH);
		if (goalH >= 8)
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics sm = g.getFontMetrics();
			text(g, label, x0 + (bw - sm.stringWidth(label)) / 2, gy + goalH - 1, Color.WHITE);
			g.setFont(restore);
		}
	}

	private static void label(Graphics2D g, FontMetrics fm, Section sec, double start, double end, int x0, int bw, int by, int barH)
	{
		double a = Math.max(sec.from, start);
		double b = Math.min(sec.to, end);
		if (b <= a || sec.label == null || sec.label.isEmpty())
		{
			return;
		}
		int xa = toX(a, start, end, x0, bw);
		int xb = toX(b, start, end, x0, bw);
		int room = xb - xa - 4;
		String text = sec.label;
		if (fm.stringWidth(text) > room)
		{
			// Too narrow: try the countdown alone (e.g. "0:23"), else skip the label.
			int space = text.lastIndexOf(' ');
			text = space > 0 ? text.substring(space + 1) : text;
			if (fm.stringWidth(text) > room)
			{
				return;
			}
		}
		int tx = xa + (xb - xa - fm.stringWidth(text)) / 2;
		int ty = by + (barH + fm.getAscent() - fm.getDescent()) / 2;
		Shape clip = g.getClip();
		g.clipRect(xa, by, xb - xa, barH);
		if (sec.text == Color.BLACK)
		{
			g.setColor(Color.BLACK);
			g.drawString(text, tx, ty);
		}
		else
		{
			text(g, text, tx, ty, sec.text);
		}
		g.setClip(clip);
	}

	/** The widest part of a section not covered by a portal, so labels never sit on top of each other. */
	private static int[] freeSpan(Section sec, List<Section> covers, double start, double end)
	{
		List<int[]> free = new ArrayList<>();
		free.add(new int[]{Math.max(sec.from, (int) Math.ceil(start)), Math.min(sec.to, (int) end)});
		for (Section c : covers)
		{
			List<int[]> next = new ArrayList<>();
			for (int[] f : free)
			{
				if (c.to <= f[0] || c.from >= f[1])
				{
					next.add(f);
					continue;
				}
				if (c.from > f[0])
				{
					next.add(new int[]{f[0], c.from});
				}
				if (c.to < f[1])
				{
					next.add(new int[]{c.to, f[1]});
				}
			}
			free = next;
		}
		int[] best = null;
		for (int[] f : free)
		{
			if (f[1] > f[0] && (best == null || f[1] - f[0] > best[1] - best[0]))
			{
				best = f;
			}
		}
		return best;
	}

	private static int toX(double t, double start, double end, int x0, int bw)
	{
		return x0 + (int) Math.round((t - start) * bw / (end - start));
	}

	private static void fillSpan(Graphics2D g, int from, int to, double start, double end, int x0, int bw, int y, int h, Color c)
	{
		double a = Math.max(from, start);
		double b = Math.min(to, end);
		if (b <= a)
		{
			return;
		}
		int xa = toX(a, start, end, x0, bw);
		int xb = toX(b, start, end, x0, bw);
		g.setColor(c);
		g.fillRect(xa, y, Math.max(1, xb - xa), h);
	}

	static String clock(int seconds)
	{
		return GotrCoachPlugin.formatTime(Math.max(0, seconds));
	}

	private static Color withAlpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	private static void text(Graphics2D g, String s, int x, int y, Color c)
	{
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}
}
