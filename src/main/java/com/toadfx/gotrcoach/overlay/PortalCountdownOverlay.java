package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.state.GameTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A big "PORTAL IN 12" countdown before each expected portal, flashing for the last five
 * seconds, then "PORTAL OPEN" with the time left once it spawns.
 */
public class PortalCountdownOverlay extends Overlay
{
	private static final Color AMBER = new Color(255, 200, 0);
	/** Keep saying "any moment" this long past the expected time (portals can be a few seconds late). */
	private static final int OVERDUE_SECONDS = 10;

	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;

	@Inject
	public PortalCountdownOverlay(GotrCoachPlugin plugin, GotrCoachConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setMovable(true);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GameTracker tracker = plugin.getTracker();
		Snapshot s = plugin.getSnapshot();
		if (!config.portalCountdown() || tracker == null || !s.inGame())
		{
			return null;
		}

		String top;
		String big;
		String bottom;
		Color color;
		boolean flash = false;
		if (s.portalOpen)
		{
			if (!config.countdownWhileOpen())
			{
				return null;
			}
			int left = s.portalTicksLeft >= 0 ? Planner.ticksToSeconds(s.portalTicksLeft) : -1;
			String dir = tracker.getPortalDirection();
			top = "PORTAL OPEN";
			big = left >= 0 ? String.valueOf(left) : "GO";
			bottom = (dir == null || dir.isEmpty() ? "" : dir + " · ") + (left >= 0 ? "seconds left" : "");
			color = config.portalColor();
		}
		else
		{
			int next = tracker.getNextPortalGameSecond();
			if (next < 0)
			{
				return null;
			}
			double in = next - plugin.getSmoothGameSeconds();
			if (in > config.countdownSeconds() || in < -OVERDUE_SECONDS)
			{
				return null;
			}
			top = "PORTAL IN";
			if (in > 0)
			{
				big = String.valueOf((int) Math.ceil(in));
				flash = in <= 5;
				color = in <= 5 ? config.portalColor() : AMBER;
			}
			else
			{
				big = "ANY MOMENT";
				color = config.portalColor();
			}
			boolean room = s.essenceRoom() >= Math.min(config.portalMinRoom(), 10);
			bottom = room ? "Stay out of altars" : "Inventory full, craft at an altar first";
		}

		// Flash: fade between full and half strength twice a second.
		if (flash)
		{
			double phase = (System.currentTimeMillis() % 500) / 500.0;
			int alpha = (int) (150 + 105 * Math.abs(Math.cos(phase * Math.PI)));
			color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
		}

		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font bigFont = FontManager.getRunescapeBoldFont().deriveFont((float) config.countdownSize());
		Font smallFont = FontManager.getRunescapeBoldFont();
		g.setFont(bigFont);
		FontMetrics bm = g.getFontMetrics();
		g.setFont(smallFont);
		FontMetrics sm = g.getFontMetrics();

		int pad = 8;
		int width = Math.max(bm.stringWidth(big), Math.max(sm.stringWidth(top), sm.stringWidth(bottom))) + pad * 2;
		width = Math.max(width, 140);
		int height = pad + sm.getHeight() + bm.getAscent() + (bottom.isEmpty() ? 0 : sm.getHeight()) + pad;

		g.setColor(new Color(15, 15, 18, 200));
		g.fillRoundRect(0, 0, width, height, 10, 10);
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha(), 200)));
		g.drawRoundRect(0, 0, width - 1, height - 1, 10, 10);

		int y = pad + sm.getAscent();
		g.setFont(smallFont);
		shadow(g, top, (width - sm.stringWidth(top)) / 2, y, color);
		y += sm.getDescent() + bm.getAscent();
		g.setFont(bigFont);
		shadow(g, big, (width - bm.stringWidth(big)) / 2, y, color);
		if (!bottom.isEmpty())
		{
			y += sm.getHeight();
			g.setFont(smallFont);
			shadow(g, bottom, (width - sm.stringWidth(bottom)) / 2, y, Color.WHITE);
		}
		return new Dimension(width, height);
	}

	private static void shadow(Graphics2D g, String text, int x, int y, Color c)
	{
		g.setColor(new Color(0, 0, 0, Math.min(220, c.getAlpha())));
		g.drawString(text, x + 2, y + 2);
		g.setColor(c);
		g.drawString(text, x, y);
	}
}
