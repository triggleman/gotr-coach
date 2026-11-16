package com.toadfx.gotrcoach;

import com.google.inject.Provides;
import com.toadfx.gotrcoach.coach.GameRecord;
import com.toadfx.gotrcoach.coach.GameRecorder;
import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.overlay.InfoPanelOverlay;
import com.toadfx.gotrcoach.overlay.ItemHighlightOverlay;
import com.toadfx.gotrcoach.overlay.MinimapPathOverlay;
import com.toadfx.gotrcoach.overlay.PortalCountdownOverlay;
import com.toadfx.gotrcoach.overlay.Pathfinder;
import com.toadfx.gotrcoach.overlay.SceneOverlay;
import com.toadfx.gotrcoach.overlay.TimelineOverlay;
import com.toadfx.gotrcoach.plan.AltarChooser;
import com.toadfx.gotrcoach.plan.EnergyMath;
import com.toadfx.gotrcoach.plan.Instruction;
import com.toadfx.gotrcoach.plan.Planner;
import com.toadfx.gotrcoach.plan.PlannerSettings;
import com.toadfx.gotrcoach.plan.Snapshot;
import com.toadfx.gotrcoach.plan.Step;
import com.toadfx.gotrcoach.plan.Target;
import com.toadfx.gotrcoach.state.GamePhase;
import com.toadfx.gotrcoach.state.GameTracker;
import com.toadfx.gotrcoach.state.Inventory;
import com.toadfx.gotrcoach.state.Location;
import com.toadfx.gotrcoach.ui.GotrCoachPanel;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "GOTR Coach",
	description = "Guardians of the Rift: next-step guidance with a path, round timeline, altar picks, energy-cap warnings and a grade after every round",
	tags = {"gotr", "guardians", "rift", "runecraft", "runecrafting", "minigame", "coach", "timeline", "portal"}
)
public class GotrCoachPlugin extends Plugin
{
	private static final String KEY_BANKED_E = "bankedElemental";
	private static final String KEY_BANKED_C = "bankedCatalytic";
	private static final String KEY_HISTORY = "history";
	/** Rubble shortcut ends (needs 56 Agility). */
	public static final WorldPoint RUBBLE_TEMPLE_SIDE = new WorldPoint(3633, 9503, 0);
	public static final WorldPoint RUBBLE_REMAINS_SIDE = new WorldPoint(3637, 9503, 0);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private GotrCoachConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private SceneOverlay sceneOverlay;

	@Inject
	private InfoPanelOverlay infoPanelOverlay;

	@Inject
	private TimelineOverlay timelineOverlay;

	@Inject
	private MinimapPathOverlay minimapPathOverlay;

	@Inject
	private ItemHighlightOverlay itemHighlightOverlay;

	@Inject
	private PortalCountdownOverlay portalCountdownOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	private GotrCoachPanel sidePanel;
	private NavigationButton navButton;
	private boolean idlePanelShown;
	private volatile long lastTickNanos = System.nanoTime();
	private volatile int lastTickCount;

	private GameTracker tracker;
	private final Pathfinder pathfinder = new Pathfinder();
	private final GameRecorder recorder = new GameRecorder();

	// What the overlays draw, refreshed every tick.
	private Snapshot snapshot = new Snapshot();
	private Instruction instruction = Instruction.NONE;
	private TileObject targetObject;
	private NPC targetNpc;
	private List<WorldPoint> path = Collections.emptyList();
	private List<WorldPoint> pathTiles = Collections.emptyList();
	private int targetTicks = -1;
	/** Ticks until the target stops being useful (altar rotation, portal closing), or -1. */
	private int deadlineTicks = -1;
	private Altar bestAltar;
	private Altar otherAltar;

	// Alert bookkeeping.
	private int stepSinceTick;
	private boolean notifiedStart;
	private boolean notifiedAltars;
	private boolean notifiedFragments;
	private int notifiedPortals;
	private int notifiedPortalSoonFor = -1;
	private GameRecord pendingSummary;
	private final Set<Alignment> notifiedCap = EnumSet.noneOf(Alignment.class);
	private Altar lastActiveElemental;
	private Altar lastActiveCatalytic;
	private boolean notifiedPouch;
	private boolean hintArrowSet;
	private WorldPoint lastPlayerTile;
	private int lastSavedBankedE = Integer.MIN_VALUE;
	private int lastSavedBankedC = Integer.MIN_VALUE;

	@Provides
	GotrCoachConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GotrCoachConfig.class);
	}

	@Override
	protected void startUp()
	{
		tracker = new GameTracker(client);
		tracker.setListeners(this::onRoundStart, this::onRoundEnd);
		overlayManager.add(sceneOverlay);
		overlayManager.add(infoPanelOverlay);
		overlayManager.add(timelineOverlay);
		overlayManager.add(minimapPathOverlay);
		overlayManager.add(itemHighlightOverlay);
		overlayManager.add(portalCountdownOverlay);

		BufferedImage logo = ImageUtil.loadImageResource(GotrCoachPlugin.class, "panel_logo.png");
		BufferedImage icon = ImageUtil.loadImageResource(GotrCoachPlugin.class, "panel_icon.png");
		sidePanel = new GotrCoachPanel(this, config, configManager, logo);
		navButton = NavigationButton.builder()
			.tooltip("GOTR Coach")
			.icon(icon)
			.priority(7)
			.panel(sidePanel)
			.build();
		if (config.sidePanel())
		{
			clientToolbar.addNavigation(navButton);
		}

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
				tracker.scanScene();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(infoPanelOverlay);
		overlayManager.remove(timelineOverlay);
		overlayManager.remove(minimapPathOverlay);
		overlayManager.remove(itemHighlightOverlay);
		overlayManager.remove(portalCountdownOverlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = null;
		sidePanel = null;
		clearHintArrow();
		instruction = Instruction.NONE;
		targetObject = null;
		targetNpc = null;
		path = Collections.emptyList();
	}

	// ------------------------------------------------------------ events

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == com.toadfx.gotrcoach.state.HudState.SCRIPT_ID && event.getScriptEvent() != null)
		{
			tracker.onHudScript(event.getScriptEvent().getArguments());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM || type == ChatMessageType.MESBOX)
		{
			tracker.onChatMessage(event.getMessage());
			saveBankedIfChanged();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.HOPPING)
		{
			tracker.onGameStateLoading();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadProfile();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clearHintArrow();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		tracker.onObjectSpawned(e.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		tracker.onObjectDespawned(e.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		tracker.onObjectSpawned(e.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		tracker.onObjectDespawned(e.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned e)
	{
		tracker.onObjectSpawned(e.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned e)
	{
		tracker.onObjectDespawned(e.getDecorativeObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned e)
	{
		tracker.onObjectSpawned(e.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned e)
	{
		tracker.onObjectDespawned(e.getWallObject());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		tracker.onNpcSpawned(e.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		tracker.onNpcDespawned(e.getNpc());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!GotrCoachConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		if ("coachReset".equals(e.getKey()) && config.coachReset())
		{
			recorder.clearHistory();
			configManager.unsetRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_HISTORY);
			configManager.setConfiguration(GotrCoachConfig.GROUP, "coachReset", false);
		}
		if ("hintArrow".equals(e.getKey()) && !config.hintArrow())
		{
			clientThread.invokeLater(this::clearHintArrow);
		}
		if ("sidePanel".equals(e.getKey()) && navButton != null)
		{
			if (config.sidePanel())
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
		}
		if (sidePanel != null)
		{
			GotrCoachPanel panel = sidePanel;
			SwingUtilities.invokeLater(panel::syncSettings);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		lastTickNanos = System.nanoTime();
		lastTickCount = client.getTickCount();
		tracker.onGameTick();
		tracker.readDialogue();
		saveBankedIfChanged();

		if (tracker.getPhase() == GamePhase.IDLE && tracker.getLocation() == Location.OUTSIDE)
		{
			snapshot = new Snapshot();
			instruction = Instruction.NONE;
			targetObject = null;
			targetNpc = null;
			path = Collections.emptyList();
			pathTiles = Collections.emptyList();
			clearHintArrow();
			recorder.trackXp(runecraftXp());
			flushSummary();
			if (!idlePanelShown)
			{
				refreshSidePanel();
				idlePanelShown = true;
			}
			return;
		}
		idlePanelShown = false;

		Player player = client.getLocalPlayer();
		WorldPoint here = player == null ? null : player.getWorldLocation();
		boolean rubble = tracker.getAgilityLevel() >= 56;
		pathfinder.update(client.getTopLevelWorldView(), here,
			rubble ? RUBBLE_TEMPLE_SIDE : null, rubble ? RUBBLE_REMAINS_SIDE : null);

		snapshot = buildSnapshot();
		PlannerSettings settings = settings();
		Instruction next = Planner.plan(snapshot, settings);

		AltarChooser.Choice choice = snapshot.phase == GamePhase.CRAFTING || snapshot.phase == GamePhase.CLOSING
			? AltarChooser.choose(snapshot, settings) : null;
		bestAltar = choice == null ? null : choice.primary;
		otherAltar = choice == null ? null : choice.secondary;

		onStepChange(next);
		instruction = next;
		resolveTarget();
		updateHintArrow();
		notifications();

		boolean idle = player != null && player.getAnimation() == -1
			&& player.getPoseAnimation() == player.getIdlePoseAnimation()
			&& here != null && here.equals(lastPlayerTile);
		lastPlayerTile = here;
		if (config.coaching())
		{
			recorder.tick(snapshot, idle, isBusyStep(next), tracker.getPortalsThisGame());
		}
		recorder.trackXp(runecraftXp());
		flushSummary();
		refreshSidePanel();
	}

	private void refreshSidePanel()
	{
		GotrCoachPanel panel = sidePanel;
		if (panel != null && config.sidePanel())
		{
			SwingUtilities.invokeLater(panel::refresh);
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (tracker == null || !tracker.isInGame())
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		if (config.deprioritizeUnusableAltars() && "Enter".equals(entry.getOption()))
		{
			Altar altar = Altar.fromPortal(entry.getIdentifier());
			boolean talisman = altar != null && snapshot.talismans.contains(altar);
			if (altar != null && !talisman && (!snapshot.canEnter(altar) || (altar != snapshot.activeElemental && altar != snapshot.activeCatalytic)
				|| EnergyMath.isCapped(altar.getAlignment(), snapshot.energyElemental, snapshot.energyCatalytic, snapshot.yewLantern)))
			{
				entry.setDeprioritized(true);
			}
		}
		if (config.deprioritizeGuardianForCell() && snapshot.chargedCell != null && snapshot.stones() == 0
			&& entry.getTarget() != null && entry.getTarget().contains("Great Guardian"))
		{
			entry.setDeprioritized(true);
		}
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		if (tracker == null || client.isMenuOpen() || !tracker.isInGame())
		{
			return;
		}
		boolean hidePower = config.hidePowerUp() && snapshot.stones() == 0;
		boolean hideCell = config.hidePlaceCell() && snapshot.chargedCell == null;
		if (!hidePower && !hideCell)
		{
			return;
		}
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		List<MenuEntry> kept = new ArrayList<>(entries.length);
		boolean changed = false;
		for (MenuEntry e : entries)
		{
			String option = e.getOption();
			String target = e.getTarget() == null ? "" : e.getTarget();
			if (hidePower && "Power-up".equals(option) && target.contains("Great Guardian"))
			{
				changed = true;
				continue;
			}
			if (hideCell && "Place-cell".equals(option))
			{
				changed = true;
				continue;
			}
			kept.add(e);
		}
		if (changed)
		{
			menu.setMenuEntries(kept.toArray(new MenuEntry[0]));
		}
	}

	// ------------------------------------------------------------ snapshot

	private Snapshot buildSnapshot()
	{
		Snapshot s = new Snapshot();
		Inventory inv = tracker.getInventory();
		s.phase = tracker.getPhase();
		s.location = tracker.getLocation();
		s.altarRoom = tracker.getAltarRoom();
		s.gameSeconds = Math.max(0, tracker.getGameSeconds());
		s.countdownSeconds = tracker.getCountdownSeconds();
		s.secondsUntilAltars = tracker.getSecondsUntilAltars();
		s.altarTicksLeft = tracker.getAltarTicksLeft();
		s.powerPercent = tracker.getPowerPercent();
		s.activeElemental = tracker.getActive(Alignment.ELEMENTAL);
		s.activeCatalytic = tracker.getActive(Alignment.CATALYTIC);
		s.portalOpen = tracker.isPortalOpen();
		s.portalTicksLeft = tracker.getPortalTicksLeft();
		s.secondsSincePortal = tracker.getSecondsSincePortal();
		int nextPortal = tracker.getNextPortalGameSecond();
		s.secondsToPortal = s.portalOpen || nextPortal < 0 || !tracker.isInGame() ? -1 : Math.max(0, nextPortal - s.gameSeconds);
		s.fragments = inv.fragments;
		s.essence = inv.essence;
		s.runecraftLevel = tracker.getRunecraftLevel();
		s.agilityLevel = tracker.getAgilityLevel();
		s.essenceInPouches = tracker.getEssenceInPouches();
		s.pouchCapacity = tracker.getPouchCapacity(s.runecraftLevel);
		s.hasPouches = !inv.pouches.isEmpty();
		s.pouchNeedsRepair = inv.anyPouchDegraded();
		s.freeSlots = inv.freeSlots;
		s.elementalStones = inv.elementalStones;
		s.catalyticStones = inv.catalyticStones;
		s.polyStones = inv.polyStones;
		s.chargedCell = inv.chargedCell;
		s.unchargedCells = inv.unchargedCells;
		s.hasChisel = inv.chisel;
		s.runeSlots = inv.runeSlots;
		s.energyElemental = tracker.getEnergyElemental();
		s.energyCatalytic = tracker.getEnergyCatalytic();
		s.bankedElemental = tracker.getBankedElemental();
		s.bankedCatalytic = tracker.getBankedCatalytic();
		s.yewLantern = tracker.isYewLantern();
		s.guardians = tracker.getGuardians();
		s.guardiansMax = tracker.getGuardiansMax();
		s.questLocked = tracker.getQuestLocked();
		s.talismans = inv.talismans;
		s.repairableTile = tracker.hasRepairableTile();
		s.emptyTile = tracker.hasEmptyTile();
		s.lowestBarrierPercent = tracker.getLowestBarrierPercent();
		s.altarTrips = altarTripsThisRound;
		s.openingCraftStarted = openingCraftStarted;
		for (Altar altar : Altar.values())
		{
			TileObject portal = tracker.getAltarPortals().get(altar);
			if (portal != null)
			{
				s.altarTravelTicks.put(altar, pathfinder.ticksTo(portal));
			}
		}
		TileObject remainsPortal = remainsPortal(false);
		s.portalTravelTicks = remainsPortal == null ? -1 : pathfinder.ticksTo(remainsPortal);

		// Round progress the planner relies on.
		if (s.location == Location.ALTAR_ROOM && lastLocation != Location.ALTAR_ROOM && tracker.isInGame())
		{
			altarTripsThisRound++;
			s.altarTrips = altarTripsThisRound;
		}
		if (s.phase == GamePhase.MINING && s.location != Location.HUGE_REMAINS && s.essenceCarried() > lastEssenceCarried
			&& s.fragments < lastFragments)
		{
			openingCraftStarted = true;
			s.openingCraftStarted = true;
		}
		lastLocation = s.location;
		lastEssenceCarried = s.essenceCarried();
		lastFragments = s.fragments;
		return s;
	}

	private int altarTripsThisRound;
	private boolean openingCraftStarted;
	private Location lastLocation = Location.OUTSIDE;
	private int lastEssenceCarried;
	private int lastFragments;

	private PlannerSettings settings()
	{
		PlannerSettings p = new PlannerSettings();
		p.strategy = config.strategy();
		p.altarMode = config.altarMode();
		p.openingFragments = config.openingFragments();
		p.craftLeadSeconds = config.craftLeadSeconds();
		p.massFirstTripEssence = config.massFirstTripEssence();
		p.minEssenceForAltar = config.minEssenceForAltar();
		p.topUpMin = config.topUpMin();
		p.guardiansWanted = config.guardiansWanted();
		p.maxImbalance = config.maxImbalance();
		p.balanceWithBanked = config.balanceWithBanked();
		p.portalMinRoom = config.portalMinRoom();
		p.portalAlways = config.portalPriority() == GotrCoachConfig.PortalPriority.ALWAYS;
		p.holdForPortal = config.holdForPortal();
		p.portalSoonSeconds = config.countdownSeconds();
		p.depositRunes = config.depositRunes();
		p.depositBelowFreeSlots = config.depositBelowFreeSlots();
		p.unchargedCellsWanted = config.unchargedCellsWanted();
		p.graceHold = config.graceHold();
		return p;
	}

	// ------------------------------------------------------------ target

	private void resolveTarget()
	{
		targetObject = null;
		targetNpc = null;
		deadlineTicks = -1;
		Target t = instruction.getTarget();
		switch (t)
		{
			case UNCHARGED_CELLS:
				targetObject = tracker.first(ObjectID.GOTR_UNCHARGED_CELLS);
				break;
			case WEAK_CELLS:
				targetObject = tracker.first(ObjectID.GOTR_WEAK_CELLS);
				break;
			case CELL_TILE:
				targetObject = pickCellTile();
				break;
			case ESSENCE_PILE_ELEMENTAL:
				targetObject = tracker.first(ObjectID.GOTR_GUARDIAN_SPAWN_EAST);
				break;
			case ESSENCE_PILE_CATALYTIC:
				targetObject = tracker.first(ObjectID.GOTR_GUARDIAN_SPAWN_WEST);
				break;
			case GUARDIAN_REMAINS:
				targetObject = nearest(tracker.all(ObjectID.GOTR_ESSENCE_TIER_1_LARGE, ObjectID.GOTR_ESSENCE_TIER_1_LARGE_B));
				break;
			case LARGE_REMAINS:
				targetObject = nearest(tracker.all(ObjectID.GOTR_ESSENCE_TIER_2));
				if (targetObject == null)
				{
					targetObject = nearest(tracker.all(ObjectID.GOTR_ESSENCE_TIER_1_LARGE, ObjectID.GOTR_ESSENCE_TIER_1_LARGE_B));
				}
				break;
			case HUGE_REMAINS:
				targetObject = nearest(tracker.all(ObjectID.GOTR_ESSENCE_TIER_3));
				break;
			case WORKBENCH:
				targetObject = nearest(tracker.all(ObjectID.GOTR_WORKBENCH));
				break;
			case GREAT_GUARDIAN:
				targetNpc = tracker.getGreatGuardian();
				break;
			case DEPOSIT_POOL:
				targetObject = tracker.first(ObjectID.GOTR_DEPOSITCHEST);
				break;
			case ALTAR_PORTAL:
				targetObject = instruction.getAltar() == null ? null : tracker.getAltarPortals().get(instruction.getAltar());
				deadlineTicks = snapshot.altarTicksLeft;
				break;
			case ALTAR:
				targetObject = tracker.getAltarObject();
				break;
			case ALTAR_EXIT:
				targetObject = tracker.getAltarExit();
				break;
			case REMAINS_PORTAL:
				targetObject = remainsPortal(false);
				deadlineTicks = snapshot.portalTicksLeft;
				break;
			case REMAINS_PORTAL_EXIT:
				targetObject = remainsPortal(true);
				break;
			case ENTRANCE_BARRIER:
				targetObject = tracker.first(ObjectID.GOTR_BARRIER, ObjectID.GOTR_BARRIER_CLOSED, ObjectID.GOTR_BARRIER_NOENTRY);
				break;
			case CORDELIA:
				targetNpc = tracker.getCordelia();
				break;
			default:
				break;
		}

		if (targetObject != null)
		{
			pathTiles = pathfinder.pathTo(targetObject);
			path = pathfinder.simplify(pathTiles);
			targetTicks = pathfinder.ticksTo(targetObject);
		}
		else if (targetNpc != null)
		{
			int size = npcSize(targetNpc);
			pathTiles = pathfinder.pathToArea(targetNpc.getWorldLocation(), size);
			path = pathfinder.simplify(pathTiles);
			targetTicks = pathfinder.ticksToArea(targetNpc.getWorldLocation(), size);
		}
		else
		{
			path = Collections.emptyList();
			pathTiles = Collections.emptyList();
			targetTicks = -1;
		}
	}

	private static int npcSize(NPC npc)
	{
		net.runelite.api.NPCComposition comp = npc.getTransformedComposition();
		if (comp == null)
		{
			comp = npc.getComposition();
		}
		return comp == null ? 1 : Math.max(1, comp.getSize());
	}

	/** The huge-remains portal on the temple side, or its exit inside the remains. */
	private TileObject remainsPortal(boolean exit)
	{
		for (TileObject o : tracker.all(ObjectID.GOTR_AGILITY_PORTAL_TOP, ObjectID.GOTR_AGILITY_PORTAL_BOTTOM_CHILD,
			ObjectID.GOTR_AGILITY_PORTAL_BOTTOM_PARENT))
		{
			WorldPoint p = o.getWorldLocation();
			boolean inRemains = p.getX() >= 3587 && p.getX() <= 3594 && p.getY() >= 9496 && p.getY() <= 9516;
			if (inRemains == exit)
			{
				return o;
			}
		}
		return null;
	}

	private TileObject pickCellTile()
	{
		TileObject best = null;
		int bestScore = Integer.MAX_VALUE;
		boolean repair = instruction.getStep() == Step.REPAIR_BARRIER;
		TileObject fire = tracker.getAltarPortals().get(Altar.FIRE);
		TileObject blood = tracker.getAltarPortals().get(Altar.BLOOD);
		boolean solo = config.strategy() == com.toadfx.gotrcoach.plan.Strategy.SOLO && fire != null && blood != null;
		for (TileObject tile : tracker.getCellTiles())
		{
			int id = tile.getId();
			int score;
			if (repair)
			{
				if (id != ObjectID.GOTR_CELL_TILE_BROKEN_REPAIRABLE)
				{
					continue;
				}
				score = 0;
			}
			else if (id == ObjectID.GOTR_CELL_TILE_BROKEN || id == ObjectID.GOTR_CELL_TILE_BROKEN_REPAIRABLE
				|| id == ObjectID.GOTR_CELL_TILE_INACTIVE_NOOP)
			{
				continue;
			}
			else if (id == ObjectID.GOTR_CELL_TILE_INACTIVE)
			{
				score = 0;
			}
			else
			{
				CellTier tier = CellTier.fromTileObject(id);
				score = tier == null ? 50 : tier.getRank() * 10;
			}
			if (solo)
			{
				WorldPoint f = fire.getWorldLocation();
				WorldPoint b = blood.getWorldLocation();
				WorldPoint mid = new WorldPoint((f.getX() + b.getX()) / 2, (f.getY() + b.getY()) / 2, f.getPlane());
				score = tile.getWorldLocation().distanceTo2D(mid) * 100;
			}
			int travel = pathfinder.ticksTo(tile);
			score += travel < 0 ? 40 : travel;
			if (score < bestScore)
			{
				bestScore = score;
				best = tile;
			}
		}
		return best;
	}

	private TileObject nearest(List<TileObject> objects)
	{
		TileObject best = null;
		int bestTicks = Integer.MAX_VALUE;
		for (TileObject o : objects)
		{
			int t = pathfinder.ticksTo(o);
			int key = t < 0 ? Integer.MAX_VALUE - 1 : t;
			if (best == null || key < bestTicks)
			{
				best = o;
				bestTicks = key;
			}
		}
		return best;
	}

	// ------------------------------------------------------------ alerts and chat

	/** Posts the round summary once the end-of-round XP has had time to land, then saves history. */
	private void flushSummary()
	{
		GameRecord r = pendingSummary;
		if (r == null || recorder.awaitingLateXp())
		{
			return;
		}
		pendingSummary = null;
		configManager.setRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_HISTORY, recorder.serializeHistory());
		if (config.coachChat() && r.score >= 0)
		{
			String tip = r.tips.isEmpty() ? "" : " " + r.tips.get(0);
			chat(String.format("<col=00c8ff>GOTR Coach:</col> Grade <col=ffd700>%s</col> (%d) - %.1f points, %,d XP in %s.%s",
				r.grade, r.score, r.points(), r.xpGained, formatTime(r.gameSeconds), tip));
		}
	}

	private int runecraftXp()
	{
		return client.getSkillExperience(net.runelite.api.Skill.RUNECRAFT);
	}

	private boolean isBusyStep(Instruction i)
	{
		Step s = i.getStep();
		return s != Step.WAIT_FOR_START && s != Step.HOLD_STONES && s != Step.ENERGY_CAPPED && s != Step.GAME_OVER
			&& !(s == Step.ENTER_ALTAR && i.getTarget() == Target.NONE);
	}

	private void onStepChange(Instruction next)
	{
		int tick = client.getTickCount();
		if (next.sameAs(instruction))
		{
			return;
		}
		int onStep = GameTracker.ticksToSeconds(tick - stepSinceTick);
		if (config.notifyNewStep() && instruction != Instruction.NONE && onStep >= config.notifyNewStepAfter())
		{
			notifier.notify("GOTR: " + next.title());
		}
		if (config.stepsInChat() && next != Instruction.NONE && next.getStep() != Step.OUTSIDE)
		{
			chat("<col=00c8ff>GOTR Coach:</col> " + next.title()
				+ (next.getDetail().isEmpty() ? "" : " <col=a0a0a0>(" + next.getDetail() + ")</col>"));
		}
		stepSinceTick = tick;
	}

	private void notifications()
	{
		GamePhase phase = snapshot.phase;
		if (phase == GamePhase.WAITING)
		{
			int cd = snapshot.countdownSeconds;
			if (config.notifyStart() && !notifiedStart && cd >= 0 && cd <= 10)
			{
				notifier.notify("GOTR: the rift opens in " + cd + "s");
				notifiedStart = true;
			}
			if (config.notifyPouch() && !notifiedPouch && snapshot.pouchNeedsRepair)
			{
				notifier.notify("GOTR: a pouch needs repairing");
				notifiedPouch = true;
			}
		}
		if (phase == GamePhase.MINING)
		{
			if (config.notifyAltarsOpen() && !notifiedAltars && snapshot.secondsUntilAltars >= 0 && snapshot.secondsUntilAltars <= 5)
			{
				notifier.notify("GOTR: altars open in " + snapshot.secondsUntilAltars + "s");
				notifiedAltars = true;
			}
			if (config.notifyFragments() && !notifiedFragments && snapshot.fragments >= settings().openingFragments())
			{
				notifier.notify("GOTR: " + snapshot.fragments + " fragments, time to craft");
				notifiedFragments = true;
			}
		}
		if (tracker.isInGame())
		{
			int nextPortal = tracker.getNextPortalGameSecond();
			int portalIn = nextPortal - snapshot.gameSeconds;
			if (config.notifyPortalSoon() && !snapshot.portalOpen && nextPortal >= 0 && nextPortal != notifiedPortalSoonFor
				&& portalIn > 0 && portalIn <= config.countdownSeconds())
			{
				notifier.notify("GOTR: portal in ~" + portalIn + "s");
				notifiedPortalSoonFor = nextPortal;
			}
			if (config.notifyPortal() && tracker.getPortalsThisGame() > notifiedPortals && snapshot.portalOpen)
			{
				String dir = tracker.getPortalDirection();
				notifier.notify("GOTR: portal open" + (dir == null || dir.isEmpty() ? "" : " (" + dir + ")"));
			}
			notifiedPortals = tracker.getPortalsThisGame();

			Set<Altar> wanted = config.notifyAltars();
			if (!wanted.isEmpty())
			{
				if (snapshot.activeElemental != null && snapshot.activeElemental != lastActiveElemental && wanted.contains(snapshot.activeElemental))
				{
					notifier.notify("GOTR: " + snapshot.activeElemental.getLabel() + " altar is open");
				}
				if (snapshot.activeCatalytic != null && snapshot.activeCatalytic != lastActiveCatalytic && wanted.contains(snapshot.activeCatalytic))
				{
					notifier.notify("GOTR: " + snapshot.activeCatalytic.getLabel() + " altar is open");
				}
			}
			lastActiveElemental = snapshot.activeElemental;
			lastActiveCatalytic = snapshot.activeCatalytic;

			if (config.notifyCap())
			{
				for (Alignment a : Alignment.values())
				{
					if (!notifiedCap.contains(a) && EnergyMath.isCapped(a, snapshot.energyElemental, snapshot.energyCatalytic, snapshot.yewLantern))
					{
						notifier.notify("GOTR: " + a.getLabel().toLowerCase() + " energy is capped");
						notifiedCap.add(a);
					}
				}
			}
		}
	}

	private void onRoundStart()
	{
		notifiedAltars = false;
		notifiedFragments = false;
		notifiedPortals = 0;
		notifiedPortalSoonFor = -1;
		notifiedCap.clear();
		notifiedPouch = false;
		altarTripsThisRound = 0;
		openingCraftStarted = false;
		lastEssenceCarried = 0;
		lastFragments = 0;
		if (config.coaching())
		{
			recorder.start(config.strategy(), tracker.isJoinedLate(), runecraftXp());
		}
	}

	private void onRoundEnd()
	{
		notifiedStart = false;
		if (!config.coaching())
		{
			return;
		}
		GameRecord r = recorder.finish(tracker.isWon(), Math.max(0, tracker.getGameSeconds()),
			tracker.getEnergyElemental(), tracker.getEnergyCatalytic(), System.currentTimeMillis());
		if (r == null)
		{
			return;
		}
		pendingSummary = r;
	}

	private void updateHintArrow()
	{
		if (!config.hintArrow())
		{
			return;
		}
		if (targetNpc != null)
		{
			client.setHintArrow(targetNpc);
			hintArrowSet = true;
		}
		else if (targetObject != null)
		{
			client.setHintArrow(targetObject.getWorldLocation());
			hintArrowSet = true;
		}
		else
		{
			clearHintArrow();
		}
	}

	private void clearHintArrow()
	{
		if (hintArrowSet)
		{
			client.clearHintArrow();
			hintArrowSet = false;
		}
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}

	public static String formatTime(int seconds)
	{
		if (seconds < 0)
		{
			return "-";
		}
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	// ------------------------------------------------------------ saved data

	private void loadProfile()
	{
		String e = configManager.getRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_BANKED_E);
		String c = configManager.getRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_BANKED_C);
		if (e != null && c != null && tracker.getBankedElemental() < 0)
		{
			try
			{
				tracker.setBanked(Integer.parseInt(e), Integer.parseInt(c));
				lastSavedBankedE = tracker.getBankedElemental();
				lastSavedBankedC = tracker.getBankedCatalytic();
			}
			catch (NumberFormatException ignored)
			{
				// Bad saved value: wait for the next end-of-round message.
			}
		}
		recorder.loadHistory(configManager.getRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_HISTORY));
	}

	private void saveBankedIfChanged()
	{
		int e = tracker.getBankedElemental();
		int c = tracker.getBankedCatalytic();
		if (e < 0 || c < 0 || (e == lastSavedBankedE && c == lastSavedBankedC))
		{
			return;
		}
		configManager.setRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_BANKED_E, e);
		configManager.setRSProfileConfiguration(GotrCoachConfig.GROUP, KEY_BANKED_C, c);
		lastSavedBankedE = e;
		lastSavedBankedC = c;
	}

	// ------------------------------------------------------------ getters for overlays

	public GameTracker getTracker()
	{
		return tracker;
	}

	public Snapshot getSnapshot()
	{
		return snapshot;
	}

	public Instruction getInstruction()
	{
		return instruction;
	}

	public TileObject getTargetObject()
	{
		return targetObject;
	}

	public NPC getTargetNpc()
	{
		return targetNpc;
	}

	public List<WorldPoint> getPath()
	{
		return path;
	}

	/** Every tile of the route (the path above has its zigzags removed for drawing). */
	public List<WorldPoint> getPathTiles()
	{
		return pathTiles;
	}

	/** Game seconds including the fraction since the last tick, so the timeline moves smoothly. */
	public double getSmoothGameSeconds()
	{
		Snapshot s = snapshot;
		GameTracker t = tracker;
		if (t == null || !s.inGame() || t.getGameStartTick() < 0)
		{
			return s.gameSeconds;
		}
		double since = Math.min(0.6, (System.nanoTime() - lastTickNanos) / 1e9);
		return Math.max(0, (lastTickCount - t.getGameStartTick()) * 0.6 + since);
	}

	public int getTargetTicks()
	{
		return targetTicks;
	}

	public int getDeadlineTicks()
	{
		return deadlineTicks;
	}

	public Altar getBestAltar()
	{
		return bestAltar;
	}

	public Altar getOtherAltar()
	{
		return otherAltar;
	}

	public Pathfinder getPathfinder()
	{
		return pathfinder;
	}

	public GameRecorder getRecorder()
	{
		return recorder;
	}

	public PlannerSettings currentSettings()
	{
		return settings();
	}
}
