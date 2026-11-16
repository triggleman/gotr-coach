package com.toadfx.gotrcoach;

import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.plan.AltarMode;
import com.toadfx.gotrcoach.plan.Strategy;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(GotrCoachConfig.GROUP)
public interface GotrCoachConfig extends Config
{
	String GROUP = "gotrcoach";

	enum PathStyle
	{
		LINE,
		TILES,
		LINE_AND_TILES,
		OFF
	}

	enum PortalPriority
	{
		ALWAYS,
		WHEN_ROOM;

		@Override
		public String toString()
		{
			return this == ALWAYS ? "Always first" : "Only with min room";
		}
	}

	enum TimelineSize
	{
		SMALL,
		MEDIUM,
		LARGE
	}

	// ------------------------------------------------------------ sections

	@ConfigSection(name = "Strategy", description = "How the step planner plays the round", position = 0)
	String strategySection = "strategy";

	@ConfigSection(name = "Guidance", description = "Path line, hint arrow and step alerts", position = 1)
	String guidanceSection = "guidance";

	@ConfigSection(name = "Altars", description = "Outlines and timers on the altar guardians", position = 2)
	String altarSection = "altars";

	@ConfigSection(name = "Highlights", description = "Outlines on objects in the temple", position = 3)
	String highlightSection = "highlights";

	@ConfigSection(name = "Timeline bar", description = "The round timeline", position = 4)
	String timelineSection = "timeline";

	@ConfigSection(name = "Portal countdown", description = "Big on-screen countdown before each portal", position = 4)
	String countdownSection = "countdown";

	@ConfigSection(name = "Info panel", description = "The step and stats panel", position = 5)
	String panelSection = "panel";

	@ConfigSection(name = "Menus", description = "Hide menu options you can't use", position = 6, closedByDefault = true)
	String menuSection = "menus";

	@ConfigSection(name = "Notifications", description = "Desktop alerts", position = 7, closedByDefault = true)
	String notifySection = "notifications";

	@ConfigSection(name = "Coaching", description = "Post-round grade and tips", position = 8)
	String coachSection = "coaching";

	// ------------------------------------------------------------ strategy

	@ConfigItem(keyName = "strategy", name = "Strategy", position = 0, section = strategySection,
		description = "Mass world, the wiki team timeline, or solo / small group (single barrier)")
	default Strategy strategy()
	{
		return Strategy.MASS;
	}

	@ConfigItem(keyName = "altarMode", name = "Altar choice", position = 1, section = strategySection,
		description = "How to pick between the two open altars. Points come from essence crafted, not altar tier")
	default AltarMode altarMode()
	{
		return AltarMode.EVEN_POINTS;
	}

	@ConfigItem(keyName = "openingFragments", name = "Opening fragments", position = 2, section = strategySection,
		description = "Fragments to mine before the first craft. 0 = strategy default (mass 168, team 120, solo 110)")
	@Range(max = 300)
	default int openingFragments()
	{
		return 0;
	}

	@ConfigItem(keyName = "craftLead", name = "Craft lead time", position = 3, section = strategySection,
		description = "Stop mining this many seconds before the altars open. -1 = strategy default (mass 15, team 35, solo 40)")
	@Range(min = -1, max = 120)
	@Units(Units.SECONDS)
	default int craftLeadSeconds()
	{
		return -1;
	}

	@ConfigItem(keyName = "massFirstTrip", name = "Mass first trip essence", position = 4, section = strategySection,
		description = "On mass worlds, chisel only this much essence for the first altar trip (wiki: 30)")
	@Range(min = 1, max = 100)
	default int massFirstTripEssence()
	{
		return 30;
	}

	@ConfigItem(keyName = "minEssence", name = "Min essence per trip", position = 5, section = strategySection,
		description = "Smallest load worth an altar trip when you are out of fragments")
	@Range(min = 1, max = 100)
	default int minEssenceForAltar()
	{
		return 10;
	}

	@ConfigItem(keyName = "topUpMin", name = "Top up only if room for", position = 5, section = strategySection,
		description = "Go back to the workbench to fill up only if at least this much more essence fits. Smaller gaps go straight to the altar")
	@Range(min = 1, max = 30)
	default int topUpMin()
	{
		return 5;
	}

	@ConfigItem(keyName = "guardiansWanted", name = "Guardians wanted", position = 6, section = strategySection,
		description = "Build guardians until this many are up, then put cells on barriers (solo always uses 2)")
	@Range(max = 10)
	default int guardiansWanted()
	{
		return 4;
	}

	@ConfigItem(keyName = "maxImbalance", name = "Max energy imbalance", position = 7, section = strategySection,
		description = "Switch to the weaker type once the gap is this big. 0 = never")
	@Range(max = 1000)
	default int maxImbalance()
	{
		return 200;
	}

	@ConfigItem(keyName = "balanceBanked", name = "Balance with banked points", position = 8, section = strategySection,
		description = "Count your saved reward points when balancing elemental and catalytic")
	default boolean balanceWithBanked()
	{
		return true;
	}

	@ConfigItem(keyName = "portalPriority", name = "Portal priority", position = 9, section = strategySection,
		description = "Always first: drop everything for an open portal you can reach. Only with min room: wait until you have the room below")
	default PortalPriority portalPriority()
	{
		return PortalPriority.ALWAYS;
	}

	@ConfigItem(keyName = "portalMinRoom", name = "Portal min room", position = 10, section = strategySection,
		description = "With 'Only with min room': essence room needed before the portal is worth it")
	@Range(max = 68)
	default int portalMinRoom()
	{
		return 20;
	}

	@ConfigItem(keyName = "depositRunes", name = "Deposit runes step", position = 11, section = strategySection,
		description = "Add a 'deposit runes' step before crafting essence. Off = never asks you to deposit")
	default boolean depositRunes()
	{
		return false;
	}

	@ConfigItem(keyName = "depositBelow", name = "Deposit below free slots", position = 12, section = strategySection,
		description = "Deposit runes once fewer than this many inventory slots are free")
	@Range(max = 28)
	default int depositBelowFreeSlots()
	{
		return 24;
	}

	@ConfigItem(keyName = "uncharged", name = "Uncharged cells to take", position = 13, section = strategySection,
		description = "Uncharged cells to grab between rounds")
	@Range(max = 10)
	default int unchargedCellsWanted()
	{
		return 10;
	}

	@ConfigItem(keyName = "graceHold", name = "Grace portal hold", position = 14, section = strategySection,
		description = "Near the end, hold stones until 95s after the last portal so the grace period spawns an extra one (wiki)")
	default boolean graceHold()
	{
		return true;
	}

	// ------------------------------------------------------------ guidance

	@ConfigItem(keyName = "pathStyle", name = "Path to next step", position = 0, section = guidanceSection,
		description = "Draw the walking route to the current target")
	default PathStyle pathStyle()
	{
		return PathStyle.LINE;
	}

	@ConfigItem(keyName = "minimapPath", name = "Path on minimap", position = 1, section = guidanceSection,
		description = "Also draw the route on the minimap")
	default boolean minimapPath()
	{
		return false;
	}

	@ConfigItem(keyName = "pathWidth", name = "Path width", position = 2, section = guidanceSection,
		description = "Line thickness")
	@Range(min = 1, max = 8)
	default int pathWidth()
	{
		return 3;
	}

	@ConfigItem(keyName = "pathTimeColors", name = "Colour by arrival time", position = 3, section = guidanceSection,
		description = "Instead of one colour: green if you'll make it, yellow if it's close, red if the altar rotates or the portal closes first")
	default boolean pathTimeColors()
	{
		return false;
	}

	@ConfigItem(keyName = "pathLeg", name = "Path detail", position = 2, section = guidanceSection,
		description = "Longest straight stretch of the line, in tiles. Lower follows your walk more closely with more bends; 1 = every tile")
	@Range(min = 1, max = 30)
	@Units(" tiles")
	default int pathLeg()
	{
		return 4;
	}

	@ConfigItem(keyName = "roundCorners", name = "Rounded corners", position = 3, section = guidanceSection,
		description = "Curve the path at each corner instead of straight segments")
	default boolean roundCorners()
	{
		return false;
	}

	@Alpha
	@ConfigItem(keyName = "pathColor", name = "Path colour", position = 4, section = guidanceSection,
		description = "The path's colour (it turns to the urgent colour when a step has to happen now)")
	default Color pathColor()
	{
		return new Color(0, 255, 180, 160);
	}

	@ConfigItem(keyName = "travelLabel", name = "Travel time label", position = 5, section = guidanceSection,
		description = "Show '9 ticks' style travel time on the target")
	default boolean travelLabel()
	{
		return true;
	}

	@ConfigItem(keyName = "outlineTarget", name = "Outline target", position = 6, section = guidanceSection,
		description = "Outline the object the current step uses")
	default boolean outlineTarget()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "targetColor", name = "Target colour", position = 7, section = guidanceSection,
		description = "Outline colour for the current step's target")
	default Color targetColor()
	{
		return new Color(0, 255, 180, 220);
	}

	@Alpha
	@ConfigItem(keyName = "urgentColor", name = "Urgent colour", position = 8, section = guidanceSection,
		description = "Used when a step has to happen now")
	default Color urgentColor()
	{
		return new Color(255, 80, 80, 230);
	}

	@Alpha
	@ConfigItem(keyName = "portalColor", name = "Portal colour", position = 9, section = guidanceSection,
		description = "Path, outline and label colour whenever the step is the huge-remains portal")
	default Color portalColor()
	{
		return new Color(255, 30, 30, 240);
	}

	@ConfigItem(keyName = "hintArrow", name = "Hint arrow", position = 10, section = guidanceSection,
		description = "Use the game's own hint arrow on the target")
	default boolean hintArrow()
	{
		return false;
	}

	@ConfigItem(keyName = "itemHighlights", name = "Highlight items", position = 11, section = guidanceSection,
		description = "Highlight the inventory items the current step uses (pouches, cells, stones)")
	default boolean itemHighlights()
	{
		return true;
	}

	@ConfigItem(keyName = "stepsInChat", name = "Steps in chat", position = 12, section = guidanceSection,
		description = "Post each new step in the chatbox (only you see it)")
	default boolean stepsInChat()
	{
		return false;
	}

	@ConfigItem(keyName = "notifyStep", name = "Notify on new step after AFK", position = 13, section = guidanceSection,
		description = "Desktop alert when the step changes after you've been on one step a while")
	default boolean notifyNewStep()
	{
		return false;
	}

	@ConfigItem(keyName = "notifyStepAfter", name = "...only after", position = 14, section = guidanceSection,
		description = "Seconds on one step before a change triggers the alert")
	@Range(min = 5, max = 300)
	@Units(Units.SECONDS)
	default int notifyNewStepAfter()
	{
		return 30;
	}

	// ------------------------------------------------------------ altars

	@ConfigItem(keyName = "outlineAltars", name = "Outline open altars", position = 0, section = altarSection,
		description = "Outline the two active altar guardians")
	default boolean outlineAltars()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "bestAltarColor", name = "Best altar colour", position = 1, section = altarSection,
		description = "The altar the planner picked")
	default Color bestAltarColor()
	{
		return new Color(60, 255, 90, 230);
	}

	@Alpha
	@ConfigItem(keyName = "otherAltarColor", name = "Other altar colour", position = 2, section = altarSection,
		description = "The other open altar")
	default Color otherAltarColor()
	{
		return new Color(255, 200, 0, 180);
	}

	@Alpha
	@ConfigItem(keyName = "talismanColor", name = "Talisman altar colour", position = 2, section = altarSection,
		description = "Guardians you hold a portal talisman for (usable even while closed)")
	default Color talismanColor()
	{
		return new Color(200, 120, 255, 220);
	}

	@ConfigItem(keyName = "colorByTier", name = "Colour by cell tier", position = 3, section = altarSection,
		description = "Colour the other altar by the cell it gives (white, blue, green, red) instead")
	default boolean colorByCellTier()
	{
		return false;
	}

	@Alpha
	@ConfigItem(keyName = "unusableColor", name = "Unusable altar colour", position = 4, section = altarSection,
		description = "Altars you can't enter (level, quest, or energy cap)")
	default Color unusableColor()
	{
		return new Color(120, 120, 120, 160);
	}

	@ConfigItem(keyName = "altarLabels", name = "Rune and timer labels", position = 5, section = altarSection,
		description = "Rune name and seconds until rotation above the open altars")
	default boolean altarLabels()
	{
		return true;
	}

	@ConfigItem(keyName = "runeIcons", name = "Rune icons", position = 7, section = altarSection,
		description = "Draw the rune's icon above the open altars")
	default boolean runeIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "runeIconsAll", name = "Rune icons on all guardians", position = 8, section = altarSection,
		description = "Also show faded rune icons on the guardians that aren't open, so you can learn the layout")
	default boolean runeIconsAll()
	{
		return false;
	}

	@ConfigItem(keyName = "altarReach", name = "Show reach time", position = 6, section = altarSection,
		description = "Add your travel time and colour it by whether you'll get there before it rotates")
	default boolean altarReach()
	{
		return true;
	}

	// ------------------------------------------------------------ highlights

	@ConfigItem(keyName = "hlGuardian", name = "Great Guardian with stones", position = 0, section = highlightSection,
		description = "Outline the Great Guardian when you have stones to hand in")
	default boolean highlightGreatGuardian()
	{
		return true;
	}

	@ConfigItem(keyName = "hlPiles", name = "Essence piles", position = 1, section = highlightSection,
		description = "Outline the essence piles when you can build a guardian (chisel, cell, free slot)")
	default boolean highlightEssencePiles()
	{
		return true;
	}

	@ConfigItem(keyName = "hlCellTable", name = "Cell table when out", position = 2, section = highlightSection,
		description = "Outline the uncharged cell table when you have none")
	default boolean highlightCellTable()
	{
		return true;
	}

	@ConfigItem(keyName = "hlCellTiles", name = "Cell tiles by tier", position = 3, section = highlightSection,
		description = "Outline barrier tiles in their tier colour, broken ones in red")
	default boolean highlightCellTiles()
	{
		return false;
	}

	@ConfigItem(keyName = "hlPortal", name = "Huge remains portal", position = 4, section = highlightSection,
		description = "Outline the portal and show seconds left")
	default boolean highlightPortal()
	{
		return true;
	}

	@ConfigItem(keyName = "hlDeposit", name = "Deposit pool with runes", position = 5, section = highlightSection,
		description = "Outline the deposit pool when you're carrying runes")
	default boolean highlightDepositPool()
	{
		return false;
	}

	@Alpha
	@ConfigItem(keyName = "hlColor", name = "Highlight colour", position = 6, section = highlightSection,
		description = "Colour for the highlights in this section")
	default Color highlightColor()
	{
		return new Color(255, 255, 255, 200);
	}

	@ConfigItem(keyName = "outlineWidth", name = "Outline width", position = 7, section = highlightSection,
		description = "Width of every outline")
	@Range(min = 1, max = 10)
	default int outlineWidth()
	{
		return 2;
	}

	@ConfigItem(keyName = "outlineFeather", name = "Outline feather", position = 8, section = highlightSection,
		description = "Softness of every outline")
	@Range(max = 4)
	default int outlineFeather()
	{
		return 2;
	}

	// ------------------------------------------------------------ timeline

	@ConfigItem(keyName = "showTimeline", name = "Show timeline bar", position = 0, section = timelineSection,
		description = "One bar for the whole round: mining, altars, portals, and your goal")
	default boolean showTimeline()
	{
		return true;
	}

	@ConfigItem(keyName = "timelineSize", name = "Size", position = 1, section = timelineSection,
		description = "Bar thickness and text size")
	default TimelineSize timelineSize()
	{
		return TimelineSize.MEDIUM;
	}

	@ConfigItem(keyName = "timelineWidth", name = "Width", position = 2, section = timelineSection,
		description = "Bar width")
	@Range(min = 200, max = 800)
	@Units(Units.PIXELS)
	default int timelineWidth()
	{
		return 360;
	}

	@ConfigItem(keyName = "timelineOpacity", name = "Opacity", position = 3, section = timelineSection,
		description = "Background opacity")
	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	default int timelineOpacity()
	{
		return 85;
	}

	@ConfigItem(keyName = "timelineWindow", name = "Window", position = 4, section = timelineSection,
		description = "How much of the round the bar shows at once")
	@Range(min = 120, max = 900)
	@Units(Units.SECONDS)
	default int timelineWindow()
	{
		return 300;
	}

	@ConfigItem(keyName = "timelinePortals", name = "Portal markers", position = 5, section = timelineSection,
		description = "Show past and predicted portals")
	default boolean timelinePortals()
	{
		return true;
	}

	@ConfigItem(keyName = "timelineGoal", name = "Goal sub-bar", position = 6, section = timelineSection,
		description = "Fragments while mining, energy while crafting")
	default boolean timelineGoal()
	{
		return true;
	}

	@ConfigItem(keyName = "energyGoal", name = "Energy goal", position = 7, section = timelineSection,
		description = "Combined energy goal for the sub-bar (100 energy = 1 point). 0 = the cap")
	@Range(max = 1320)
	default int energyGoal()
	{
		return 0;
	}

	@ConfigItem(keyName = "timelineBetween", name = "Between-round countdown", position = 8, section = timelineSection,
		description = "Show the next-round countdown when no round is running")
	default boolean timelineBetween()
	{
		return true;
	}

	// ------------------------------------------------------------ portal countdown

	@ConfigItem(keyName = "portalCountdown", name = "Show countdown", position = 0, section = countdownSection,
		description = "Large 'Portal in 12' countdown on screen before the next portal is expected")
	default boolean portalCountdown()
	{
		return true;
	}

	@ConfigItem(keyName = "countdownSeconds", name = "Start counting at", position = 1, section = countdownSection,
		description = "Seconds before the expected portal that the countdown, alert and altar hold begin")
	@Range(min = 5, max = 60)
	@Units(Units.SECONDS)
	default int countdownSeconds()
	{
		return 15;
	}

	@ConfigItem(keyName = "countdownSize", name = "Text size", position = 2, section = countdownSection,
		description = "Countdown text size")
	@Range(min = 16, max = 72)
	@Units(Units.PIXELS)
	default int countdownSize()
	{
		return 34;
	}

	@ConfigItem(keyName = "countdownOpen", name = "Show while open", position = 3, section = countdownSection,
		description = "After it spawns, keep showing 'PORTAL OPEN' with the time left")
	default boolean countdownWhileOpen()
	{
		return true;
	}

	@ConfigItem(keyName = "holdForPortal", name = "Hold altar trips", position = 4, section = countdownSection,
		description = "During the countdown, don't send you into an altar (you'd be inside when it spawns). Full inventories still go")
	default boolean holdForPortal()
	{
		return true;
	}

	// ------------------------------------------------------------ panel

	@ConfigItem(keyName = "sidePanel", name = "Side panel", position = -1, section = panelSection,
		description = "GOTR Coach tab in the RuneLite sidebar: live round, quick settings, last round and history")
	default boolean sidePanel()
	{
		return true;
	}

	@ConfigItem(keyName = "showPanel", name = "Show info panel", position = 0, section = panelSection,
		description = "Current step, timers and points")
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "compactPanel", name = "Compact", position = 1, section = panelSection,
		description = "Only the current step")
	default boolean compactPanel()
	{
		return false;
	}

	@ConfigItem(keyName = "panelTimers", name = "Timers", position = 2, section = panelSection,
		description = "Game clock, altar rotation, portal")
	default boolean panelTimers()
	{
		return true;
	}

	@ConfigItem(keyName = "panelPoints", name = "Points", position = 3, section = panelSection,
		description = "Energy this round, potential points, banked points")
	default boolean panelPoints()
	{
		return true;
	}

	@ConfigItem(keyName = "panelEssence", name = "Essence", position = 4, section = panelSection,
		description = "Essence carried and pouch room")
	default boolean panelEssence()
	{
		return true;
	}

	@ConfigItem(keyName = "panelCredit", name = "Show credit", position = 5, section = panelSection,
		description = "Small 'Made by ToadFx' line")
	default boolean panelCredit()
	{
		return true;
	}

	// ------------------------------------------------------------ menus

	@ConfigItem(keyName = "hidePowerUp", name = "Hide Power-up without stones", position = 0, section = menuSection,
		description = "Removes the Great Guardian's Power-up option when you have no stones")
	default boolean hidePowerUp()
	{
		return true;
	}

	@ConfigItem(keyName = "hidePlaceCell", name = "Hide Place-cell without a cell", position = 1, section = menuSection,
		description = "Removes Place-cell on tiles when you have no charged cell")
	default boolean hidePlaceCell()
	{
		return true;
	}

	@ConfigItem(keyName = "deprioAltars", name = "Deprioritise unusable altars", position = 2, section = menuSection,
		description = "Inactive or locked guardians never take the left click")
	default boolean deprioritizeUnusableAltars()
	{
		return true;
	}

	@ConfigItem(keyName = "hideGuardianForCell", name = "Deprioritise Great Guardian for cells", position = 3, section = menuSection,
		description = "While holding a charged cell, the Great Guardian never takes the left click over a tile")
	default boolean deprioritizeGuardianForCell()
	{
		return true;
	}

	// ------------------------------------------------------------ notifications

	@ConfigItem(keyName = "notifyStart", name = "Before the round starts", position = 0, section = notifySection,
		description = "Alert when the rift is about to open")
	default boolean notifyStart()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyAltars", name = "Altars opening", position = 1, section = notifySection,
		description = "Alert a few seconds before the first altars open")
	default boolean notifyAltarsOpen()
	{
		return false;
	}

	@ConfigItem(keyName = "notifyPortal", name = "Portal spawned", position = 2, section = notifySection,
		description = "Alert when a huge-remains portal opens")
	default boolean notifyPortal()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyPortalSoon", name = "Portal about to spawn", position = 2, section = notifySection,
		description = "Desktop alert when the portal countdown starts (see Portal countdown for the timing)")
	default boolean notifyPortalSoon()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyFragments", name = "Opening fragments reached", position = 3, section = notifySection,
		description = "Alert when you've mined your opening fragment target")
	default boolean notifyFragments()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyAltarSet", name = "Altar opens", position = 4, section = notifySection,
		description = "Alert when any of these altars becomes active")
	default Set<Altar> notifyAltars()
	{
		return EnumSet.noneOf(Altar.class);
	}

	@ConfigItem(keyName = "notifyCap", name = "Energy cap reached", position = 5, section = notifySection,
		description = "Alert when a type hits the energy cap")
	default boolean notifyCap()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyPouch", name = "Pouch needs repair", position = 6, section = notifySection,
		description = "Alert between rounds when a pouch has degraded")
	default boolean notifyPouch()
	{
		return false;
	}

	// ------------------------------------------------------------ coaching

	@ConfigItem(keyName = "coaching", name = "Grade each round", position = 0, section = coachSection,
		description = "Compare each round to the wiki timeline and give tips")
	default boolean coaching()
	{
		return true;
	}

	@ConfigItem(keyName = "coachChat", name = "Summary in chat", position = 1, section = coachSection,
		description = "Post the grade and top tip in the chatbox after each round (only you see it)")
	default boolean coachChat()
	{
		return true;
	}

	@ConfigItem(keyName = "coachPanel", name = "Last round in panel", position = 2, section = coachSection,
		description = "Show the last round's grade and tips in the info panel between rounds")
	default boolean coachPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "coachAverage", name = "Rounds to average", position = 3, section = coachSection,
		description = "How many recent rounds the averages cover")
	@Range(min = 1, max = 50)
	default int coachAverage()
	{
		return 10;
	}

	@ConfigItem(keyName = "coachReset", name = "Reset history", position = 4, section = coachSection,
		description = "Tick to clear saved rounds. It unticks itself")
	default boolean coachReset()
	{
		return false;
	}
}
