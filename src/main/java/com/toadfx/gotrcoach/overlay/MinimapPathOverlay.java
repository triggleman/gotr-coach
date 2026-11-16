package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The route to the current target, drawn on the minimap. */
public class MinimapPathOverlay extends Overlay
{
	private final Client client;
	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;

	@Inject
	public MinimapPathOverlay(Client client, GotrCoachPlugin plugin, GotrCoachConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		List<WorldPoint> path = plugin.getPath();
		WorldView wv = client.getTopLevelWorldView();
		if (!config.minimapPath() || path.size() < 2 || wv == null)
		{
			return null;
		}
		boolean portal = plugin.getInstruction().getTarget() == com.toadfx.gotrcoach.plan.Target.REMAINS_PORTAL;
		boolean urgent = plugin.getInstruction().getUrgency() == com.toadfx.gotrcoach.plan.Urgency.NOW;
		g.setColor(portal ? config.portalColor() : urgent ? config.urgentColor() : config.pathColor());
		g.setStroke(new BasicStroke(2));
		Point prev = null;
		for (WorldPoint wp : path)
		{
			LocalPoint lp = LocalPoint.fromWorld(wv, wp);
			Point p = lp == null ? null : Perspective.localToMinimap(client, lp);
			if (p != null && prev != null)
			{
				g.drawLine(prev.getX(), prev.getY(), p.getX(), p.getY());
			}
			prev = p;
		}
		return null;
	}
}
