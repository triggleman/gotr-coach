package com.toadfx.gotrcoach.overlay;

import com.toadfx.gotrcoach.GotrCoachConfig;
import com.toadfx.gotrcoach.GotrCoachPlugin;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.model.PouchType;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Step;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the inventory items the current step uses. */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private final GotrCoachPlugin plugin;
	private final GotrCoachConfig config;

	@Inject
	public ItemHighlightOverlay(GotrCoachPlugin plugin, GotrCoachConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!config.itemHighlights() || plugin.getTracker() == null)
		{
			return;
		}
		Snapshot s = plugin.getSnapshot();
		if (!wanted(plugin.getInstruction().getStep(), itemId, s))
		{
			return;
		}
		Rectangle r = item.getCanvasBounds();
		Color c = config.targetColor();
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
		g.fill(r);
		g.setColor(c);
		g.setStroke(new BasicStroke(2));
		g.draw(r);
	}

	static boolean wanted(Step step, int itemId, Snapshot s)
	{
		PouchType pouch = PouchType.fromItem(itemId);
		switch (step)
		{
			case CRAFT_ESSENCE:
				return pouch != null && s.essence > 0 && s.essenceInPouches < s.pouchCapacity;
			case MINE_HUGE_REMAINS:
				return pouch != null && s.freeSlots == 0 && s.essenceInPouches < s.pouchCapacity;
			case EMPTY_POUCHES:
				return pouch != null;
			case REPAIR_POUCHES:
				return pouch != null && pouch.isDegraded(itemId);
			case PLACE_CELL:
			case BUILD_GUARDIAN:
				return CellTier.fromItem(itemId) != null;
			case POWER_UP:
			case HAND_IN_NOW:
				return itemId == ItemID.GOTR_GUARDIAN_STONE_ELEMENTAL || itemId == ItemID.GOTR_GUARDIAN_STONE_CATALYTIC
					|| itemId == ItemID.GOTR_GUARDIAN_STONE_POLYELEMENTAL || itemId == ItemID.GOTR_GUARDIAN_STONE_POLYCATALYTIC;
			default:
				return false;
		}
	}
}
