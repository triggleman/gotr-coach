package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.plan.AltarChooser;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Target;
import com.toadfx.gotrcoach.plan.Urgency;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.GameTracker;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/** Everything drawn in the game world: altar outlines and timers, the target, the path, highlights. */
public class SceneOverlay extends Overlay
{
	private static final int LABEL_HEIGHT = 160;
	/** Rune icons sit just above the label. */
	private static final int ICON_HEIGHT = 250;

	private final Client client;
	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;
	private final ModelOutlineRenderer outlines;
	private final ItemManager itemManager;

	@Inject
	public SceneOverlay(Client client, GotrCoachPlugin plugin, GotrCoachConfig config, ModelOutlineRenderer outlines,
		ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlines = outlines;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GameTracker tracker = plugin.getTracker();
		if (tracker == null || tracker.getPhase() == GamePhase.IDLE)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Snapshot s = plugin.getSnapshot();
		int width = config.outlineWidth();
		int feather = config.outlineFeather();

		drawPath(g);
		drawHighlights(g, tracker, s, width, feather);
		drawAltars(g, tracker, s, width, feather);
		drawTarget(g, width, feather);
		return null;
	}

	// ------------------------------------------------------------ path

	private void drawPath(Graphics2D g)
	{
		GotrCoachConfig.PathStyle style = config.pathStyle();
		List<WorldPoint> path = plugin.getPath();
		if (style == GotrCoachConfig.PathStyle.OFF || path.size() < 2)
		{
			return;
		}
		Color color = pathColor();
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		boolean tiles = style == GotrCoachConfig.PathStyle.TILES || style == GotrCoachConfig.PathStyle.LINE_AND_TILES;
		boolean line = style == GotrCoachConfig.PathStyle.LINE || style == GotrCoachConfig.PathStyle.LINE_AND_TILES;
		if (tiles)
		{
			Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(60, color.getAlpha()));
			List<WorldPoint> all = plugin.getPathTiles();
			for (int i = 1; i < all.size(); i++)
			{
				LocalPoint lp = LocalPoint.fromWorld(wv, all.get(i));
				if (lp == null)
				{
					continue;
				}
				Polygon poly = Perspective.getCanvasTilePoly(client, lp);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(g, poly, color, fill, new BasicStroke(1));
				}
			}
		}
		if (line)
		{
			drawLine(g, wv, path, color);
		}
	}

	/** One colour for the path: red for the portal, the urgent colour when urgent, else the path colour. */
	private Color pathColor()
	{
		if (portalStep())
		{
			return config.portalColor();
		}
		if (plugin.getInstruction().getUrgency() == Urgency.NOW)
		{
			return config.urgentColor();
		}
		return arrivalColor(plugin.getTargetTicks(), plugin.getDeadlineTicks(), config.pathColor());
	}

	/**
	 * The route as straight legs (or rounded corners if turned on). It starts at the player's drawn
	 * position, drops legs already walked, goes dashed over the rubble shortcut, and disappears once
	 * you're next to the target.
	 */
	private void drawLine(Graphics2D g, WorldView wv, List<WorldPoint> path, Color color)
	{
		net.runelite.api.Player me = client.getLocalPlayer();
		LocalPoint here = me == null ? null : me.getLocalLocation();
		if (here == null)
		{
			return;
		}
		WorldPoint hereTile = WorldPoint.fromLocal(client, here);
		WorldPoint goal = path.get(path.size() - 1);
		if (hereTile != null && hereTile.getPlane() == goal.getPlane()
			&& Math.abs(hereTile.getX() - goal.getX()) <= 1 && Math.abs(hereTile.getY() - goal.getY()) <= 1)
		{
			return;
		}

		// Skip legs already walked: start after the leg the player is standing nearest to.
		int first = 1;
		double nearest = 2.0;
		double px = here.getX() / 128.0;
		double py = here.getY() / 128.0;
		for (int k = 0; k + 1 < path.size(); k++)
		{
			if (isShortcut(path.get(k), path.get(k + 1)))
			{
				continue;
			}
			LocalPoint a = LocalPoint.fromWorld(wv, path.get(k));
			LocalPoint b = LocalPoint.fromWorld(wv, path.get(k + 1));
			if (a == null || b == null)
			{
				continue;
			}
			double d = distanceToLeg(px, py, a.getX() / 128.0, a.getY() / 128.0, b.getX() / 128.0, b.getY() / 128.0);
			if (d <= nearest)
			{
				nearest = d;
				first = k + 1;
			}
		}

		Point prev = Perspective.localToCanvas(client, here, wv.getPlane());
		WorldPoint prevWorld = hereTile;
		Color stroke = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
		g.setColor(stroke);
		BasicStroke solid = new BasicStroke(config.pathWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		BasicStroke dashed = new BasicStroke(config.pathWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
			new float[]{8f, 8f}, 0f);

		if (config.roundCorners())
		{
			java.awt.geom.Path2D.Double shape = new java.awt.geom.Path2D.Double();
			List<Point> pts = new java.util.ArrayList<>();
			if (prev != null)
			{
				pts.add(prev);
			}
			for (int i = first; i < path.size(); i++)
			{
				LocalPoint lp = LocalPoint.fromWorld(wv, path.get(i));
				Point p = lp == null ? null : Perspective.localToCanvas(client, lp, wv.getPlane());
				if (p != null)
				{
					pts.add(p);
				}
			}
			if (pts.size() < 2)
			{
				return;
			}
			shape.moveTo(pts.get(0).getX(), pts.get(0).getY());
			for (int i = 1; i < pts.size() - 1; i++)
			{
				Point c = pts.get(i);
				Point n = pts.get(i + 1);
				shape.quadTo(c.getX(), c.getY(), (c.getX() + n.getX()) / 2.0, (c.getY() + n.getY()) / 2.0);
			}
			shape.lineTo(pts.get(pts.size() - 1).getX(), pts.get(pts.size() - 1).getY());
			g.setStroke(solid);
			g.draw(shape);
			return;
		}

		for (int i = first; i < path.size(); i++)
		{
			WorldPoint wp = path.get(i);
			LocalPoint lp = LocalPoint.fromWorld(wv, wp);
			if (lp == null)
			{
				break;
			}
			Point p = Perspective.localToCanvas(client, lp, wv.getPlane());
			if (p != null && prev != null)
			{
				WorldPoint from = i == first ? prevWorld : path.get(i - 1);
				g.setStroke(from != null && isShortcut(from, wp) ? dashed : solid);
				g.drawLine(prev.getX(), prev.getY(), p.getX(), p.getY());
			}
			prev = p;
		}
	}

	/** The rubble climb between the temple and the large remains. */
	private static boolean isShortcut(WorldPoint a, WorldPoint b)
	{
		WorldPoint x = GotrCoachPlugin.RUBBLE_TEMPLE_SIDE;
		WorldPoint y = GotrCoachPlugin.RUBBLE_REMAINS_SIDE;
		return (same(a, x) && same(b, y)) || (same(a, y) && same(b, x));
	}

	private static boolean same(WorldPoint a, WorldPoint b)
	{
		return a.getX() == b.getX() && a.getY() == b.getY() && a.getPlane() == b.getPlane();
	}

	private static double distanceToLeg(double px, double py, double ax, double ay, double bx, double by)
	{
		double abx = bx - ax;
		double aby = by - ay;
		double len = abx * abx + aby * aby;
		double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * abx + (py - ay) * aby) / len));
		double dx = px - ax - t * abx;
		double dy = py - ay - t * aby;
		return Math.sqrt(dx * dx + dy * dy);
	}

	// ------------------------------------------------------------ target

	private void drawTarget(Graphics2D g, int width, int feather)
	{
		boolean urgent = plugin.getInstruction().getUrgency() == Urgency.NOW;
		Color color = urgent ? config.urgentColor() : config.targetColor();
		String label = null;
		if (config.travelLabel() && plugin.getTargetTicks() > 0)
		{
			label = plugin.getTargetTicks() + (plugin.getTargetTicks() == 1 ? " tick" : " ticks");
		}
		Color labelColor = arrivalColor(plugin.getTargetTicks(), plugin.getDeadlineTicks(), color);
		if (portalStep())
		{
			color = config.portalColor();
			labelColor = config.portalColor();
		}

		TileObject obj = plugin.getTargetObject();
		NPC npc = plugin.getTargetNpc();
		boolean altarTarget = plugin.getInstruction().getTarget() == Target.ALTAR_PORTAL;
		if (obj != null)
		{
			if (config.outlineTarget() && !altarTarget)
			{
				outlines.drawOutline(obj, width + 1, color, feather);
			}
			if (label != null && !altarTarget)
			{
				Point p = obj.getCanvasTextLocation(g, label, LABEL_HEIGHT / 2);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, label, labelColor);
				}
			}
		}
		else if (npc != null)
		{
			if (config.outlineTarget())
			{
				outlines.drawOutline(npc, width + 1, color, feather);
			}
			if (label != null)
			{
				Point p = npc.getCanvasTextLocation(g, label, npc.getLogicalHeight() + 40);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, label, labelColor);
				}
			}
		}
	}

	// ------------------------------------------------------------ altars

	private void drawAltars(Graphics2D g, GameTracker tracker, Snapshot s, int width, int feather)
	{
		Map<Altar, TileObject> portals = tracker.getAltarPortals();
		// Talisman guardians show for as long as you carry the talisman.
		for (Altar altar : s.talismans)
		{
			if (altar == s.activeElemental || altar == s.activeCatalytic || !s.inGame())
			{
				continue;
			}
			TileObject portal = portals.get(altar);
			if (portal == null)
			{
				continue;
			}
			boolean usable = Planner.usable(s, altar);
			Color color = !usable ? config.unusableColor()
				: altar == plugin.getBestAltar() ? config.bestAltarColor() : config.talismanColor();
			if (config.outlineAltars())
			{
				outlines.drawOutline(portal, altar == plugin.getBestAltar() ? width + 1 : width, color, feather);
			}
			drawRuneIcon(g, portal, altar, usable ? 1f : 0.5f);
			if (config.altarLabels())
			{
				String text = altar.getLabel() + " · talisman";
				Point p = portal.getCanvasTextLocation(g, text, LABEL_HEIGHT);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, text, color);
				}
			}
		}
		if (s.phase != GamePhase.CRAFTING && s.phase != GamePhase.CLOSING)
		{
			return;
		}
		if (config.runeIconsAll())
		{
			for (Map.Entry<Altar, TileObject> e : portals.entrySet())
			{
				if (e.getKey() != s.activeElemental && e.getKey() != s.activeCatalytic && !s.talismans.contains(e.getKey()))
				{
					drawRuneIcon(g, e.getValue(), e.getKey(), 0.4f);
				}
			}
		}
		for (Altar altar : new Altar[]{s.activeElemental, s.activeCatalytic})
		{
			if (altar == null)
			{
				continue;
			}
			TileObject portal = portals.get(altar);
			if (portal == null)
			{
				continue;
			}
			boolean usable = Planner.usable(s, altar);
			Color color;
			if (!usable)
			{
				color = config.unusableColor();
			}
			else if (altar == plugin.getBestAltar())
			{
				color = config.bestAltarColor();
			}
			else if (config.colorByCellTier())
			{
				color = altar.getCellTier().getColor();
			}
			else
			{
				color = config.otherAltarColor();
			}
			if (config.outlineAltars())
			{
				int w = altar == plugin.getBestAltar() ? width + 1 : width;
				outlines.drawOutline(portal, w, color, feather);
			}
			if (config.runeIcons())
			{
				drawRuneIcon(g, portal, altar, usable ? 1f : 0.5f);
			}
			if (config.altarLabels())
			{
				StringBuilder text = new StringBuilder(altar.getLabel());
				if (s.altarTicksLeft >= 0)
				{
					text.append(' ').append(Planner.ticksToSeconds(s.altarTicksLeft)).append('s');
				}
				Color textColor = usable ? Color.WHITE : config.unusableColor();
				Integer travel = s.altarTravelTicks.get(altar);
				if (config.altarReach() && usable && travel != null && travel >= 0)
				{
					text.append(" · ").append(travel).append('t');
					textColor = arrivalColor(travel, s.altarTicksLeft, Color.WHITE);
				}
				if (!usable)
				{
					text.append(s.runecraftLevel < altar.getLevel() ? " (lvl " + altar.getLevel() + ")"
						: s.questLocked.contains(altar) ? " (quest)" : " (capped)");
				}
				Point p = portal.getCanvasTextLocation(g, text.toString(), LABEL_HEIGHT);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, text.toString(), textColor);
				}
			}
		}
	}

	private void drawRuneIcon(Graphics2D g, TileObject portal, Altar altar, float alpha)
	{
		BufferedImage icon = itemManager.getImage(altar.getRuneItemId());
		LocalPoint lp = portal.getLocalLocation();
		if (icon == null || lp == null)
		{
			return;
		}
		Composite before = g.getComposite();
		if (alpha < 1f)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		}
		OverlayUtil.renderImageLocation(client, g, lp, icon, ICON_HEIGHT);
		g.setComposite(before);
	}

	// ------------------------------------------------------------ highlights

	private void drawHighlights(Graphics2D g, GameTracker tracker, Snapshot s, int width, int feather)
	{
		Color color = config.highlightColor();
		boolean inGame = s.inGame();

		if (config.highlightGreatGuardian() && s.stones() > 0 && tracker.getGreatGuardian() != null
			&& plugin.getTargetNpc() != tracker.getGreatGuardian())
		{
			outlines.drawOutline(tracker.getGreatGuardian(), width, color, feather);
		}

		if (config.highlightEssencePiles() && inGame && s.chargedCell != null && s.hasChisel && s.guardians < s.guardiansMax)
		{
			for (TileObject pile : tracker.all(ObjectID.GOTR_GUARDIAN_SPAWN_EAST, ObjectID.GOTR_GUARDIAN_SPAWN_WEST))
			{
				if (pile != plugin.getTargetObject())
				{
					outlines.drawOutline(pile, width, s.chargedCell.getColor(), feather);
				}
			}
		}

		if (config.highlightCellTable() && s.unchargedCells == 0)
		{
			TileObject table = tracker.first(ObjectID.GOTR_UNCHARGED_CELLS);
			if (table != null && table != plugin.getTargetObject())
			{
				outlines.drawOutline(table, width, color, feather);
			}
		}

		if (config.highlightCellTiles() && inGame)
		{
			for (TileObject tile : tracker.getCellTiles())
			{
				int id = tile.getId();
				Color c;
				if (id == ObjectID.GOTR_CELL_TILE_BROKEN || id == ObjectID.GOTR_CELL_TILE_BROKEN_REPAIRABLE)
				{
					c = config.urgentColor();
				}
				else
				{
					CellTier tier = CellTier.fromTileObject(id);
					if (tier == null)
					{
						continue;
					}
					c = tier.getColor();
				}
				LocalPoint lp = tile.getLocalLocation();
				Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(g, poly, c);
				}
			}
		}

		if (config.highlightPortal() && tracker.isPortalOpen())
		{
			for (TileObject portal : tracker.all(ObjectID.GOTR_AGILITY_PORTAL_TOP))
			{
				if (portal != plugin.getTargetObject())
				{
					outlines.drawOutline(portal, width, config.portalColor(), feather);
				}
				int left = tracker.getPortalTicksLeft();
				if (left >= 0)
				{
					String text = "Portal " + Planner.ticksToSeconds(left) + "s";
					Color tc = config.portalColor();
					Point p = portal.getCanvasTextLocation(g, text, LABEL_HEIGHT);
					if (p != null)
					{
						OverlayUtil.renderTextLocation(g, p, text, tc);
					}
				}
			}
		}

		if (config.highlightDepositPool() && inGame && s.runeSlots > 0)
		{
			TileObject pool = tracker.first(ObjectID.GOTR_DEPOSITCHEST);
			if (pool != null && pool != plugin.getTargetObject())
			{
				outlines.drawOutline(pool, width, color, feather);
			}
		}
	}

	private boolean portalStep()
	{
		return plugin.getInstruction().getTarget() == Target.REMAINS_PORTAL;
	}

	/**
	 * Green when you will arrive with time to spare, yellow when it is close, red when the deadline
	 * passes first. With no deadline (or colouring off) the normal colour is used.
	 */
	Color arrivalColor(int travelTicks, int deadlineTicks, Color normal)
	{
		if (!config.pathTimeColors() || travelTicks < 0 || deadlineTicks < 0)
		{
			return normal;
		}
		int spare = deadlineTicks - travelTicks;
		if (spare < 0)
		{
			return new Color(255, 60, 60, normal.getAlpha());
		}
		if (spare < 5)
		{
			return new Color(255, 210, 0, normal.getAlpha());
		}
		return new Color(60, 230, 90, normal.getAlpha());
	}

	/** Exposed for the other altar's reach check in the panel. */
	public static boolean reachable(Snapshot s, Altar altar)
	{
		return AltarChooser.reachable(s, altar);
	}
}
