package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.plan.Snapshot;
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

/** A big green "FRAGMENTS REACHED" banner for a few seconds once the opening target is hit. */
public class FragmentAlertOverlay extends Overlay
{
	private static final Color GREEN = new Color(70, 230, 100);
	/** How long the banner stays up after the target is reached. */
	private static final double SHOW_SECONDS = 8;

	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;

	@Inject
	public FragmentAlertOverlay(GotrCoachPlugin plugin, GotrCoachConfig config)
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
		double since = plugin.secondsSinceFragmentsReached();
		if (!config.fragmentsBanner() || !plugin.isFragmentsTargetMet() || since < 0 || since > SHOW_SECONDS)
		{
			return null;
		}
		Snapshot s = plugin.getSnapshot();
		String top = "FRAGMENTS REACHED";
		String big = String.valueOf(s.fragments);
		String bottom = "Time to craft essence";

		double phase = (System.currentTimeMillis() % 600) / 600.0;
		int alpha = (int) (170 + 85 * Math.abs(Math.cos(phase * Math.PI)));
		Color color = new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), alpha);

		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font bigFont = FontManager.getRunescapeBoldFont().deriveFont((float) config.countdownSize());
		Font smallFont = FontManager.getRunescapeBoldFont();
		g.setFont(bigFont);
		FontMetrics bm = g.getFontMetrics();
		g.setFont(smallFont);
		FontMetrics sm = g.getFontMetrics();

		int pad = 8;
		int width = Math.max(140, Math.max(bm.stringWidth(big), Math.max(sm.stringWidth(top), sm.stringWidth(bottom))) + pad * 2);
		int height = pad + sm.getHeight() + bm.getAscent() + sm.getHeight() + pad;
		g.setColor(new Color(15, 15, 18, 200));
		g.fillRoundRect(0, 0, width, height, 10, 10);
		g.setColor(new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), 200));
		g.drawRoundRect(0, 0, width - 1, height - 1, 10, 10);

		int y = pad + sm.getAscent();
		shadow(g, top, (width - sm.stringWidth(top)) / 2, y, color);
		y += sm.getDescent() + bm.getAscent();
		g.setFont(bigFont);
		shadow(g, big, (width - bm.stringWidth(big)) / 2, y, color);
		y += sm.getHeight();
		g.setFont(smallFont);
		shadow(g, bottom, (width - sm.stringWidth(bottom)) / 2, y, Color.WHITE);
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
