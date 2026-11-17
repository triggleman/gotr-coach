package com.toadfx.gotrcoach.state;

import com.toadfx.gotrcoach.model.Alignment;
import com.toadfx.gotrcoach.model.Altar;
import com.toadfx.gotrcoach.model.CellTier;
import com.toadfx.gotrcoach.model.PouchType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.QuestState;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;

/**
 * Tracks everything about the current Guardians of the Rift round: HUD values, phase, timers,
 * objects in the temple, what the player carries, and banked points. The plugin forwards
 * RuneLite events here; overlays and the planner read from it.
 */
public class GameTracker
{
	public static final int TEMPLE_REGION = 14484;
	public static final int FIRST_PORTAL_SECONDS = 160;
	public static final int PORTAL_INTERVAL_SECONDS = 140;
	/** Ticks the mining phase lasts (2 minutes). */
	public static final int MINING_TICKS = 200;
	private static final int CLOSING_TICKS = 50;

	private static final Pattern COUNTDOWN = Pattern.compile("The rift will become active in (\\d+) seconds");
	private static final Pattern REWARD_TOTAL = Pattern.compile(
		"Total elemental energy:[^>]*>([\\d,]+).*Total catalytic energy:[^>]*>([\\d,]+)");
	private static final Pattern CHECK_POINTS = Pattern.compile(
		"You have\\s+([\\d,]+)\\s+catalytic energy and\\s+([\\d,]+)\\s+elemental energy");

	private final Client client;

	// HUD
	private HudState hud;
	private GamePhase phase = GamePhase.IDLE;
	private Location location = Location.OUTSIDE;
	private Altar altarRoom;

	// Round timing (client ticks, -1 when unknown)
	private int gameStartTick = -1;
	private int altarsOpenTick = -1;
	private int closingTick = -1;
	private int gameEndTick = -1;
	private int countdownEndTick = -1;
	private boolean joinedLate;
	private boolean won;

	// Portal
	private boolean portalOpen;
	private int portalCloseTick = -1;
	private int lastPortalTick = -1;
	private int portalsThisGame;
	private String portalDirection;
	private final List<Integer> portalSpawnSeconds = new ArrayList<>();

	// Points
	private int bankedElemental = -1;
	private int bankedCatalytic = -1;
	private int lastEnergyElemental;
	private int lastEnergyCatalytic;

	// Player
	private boolean yewLantern;
	private Inventory inventory = new Inventory();
	private final Map<PouchType, Integer> pouchContents = new EnumMap<>(PouchType.class);
	private final Map<PouchType, Integer> pouchLearnedCapacity = new EnumMap<>(PouchType.class);
	private Set<Altar> questLocked = EnumSet.noneOf(Altar.class);
	private int questCheckTick = -1;

	// Objects
	private Altar observedElemental;
	private Altar observedCatalytic;
	private boolean guardiansObserved;
	private final Map<Altar, TileObject> altarPortals = new EnumMap<>(Altar.class);
	private final Map<WorldPoint, TileObject> cellTiles = new HashMap<>();
	private final Map<Integer, List<TileObject>> objects = new HashMap<>();
	private TileObject altarObject;
	private TileObject altarExit;
	private NPC greatGuardian;
	private NPC cordelia;
	private final List<NPC> barriers = new ArrayList<>();

	/** Called by the planner wrapper when a new game starts so dependent state resets. */
	private Runnable onGameStart = () -> { };
	private Runnable onGameEnd = () -> { };

	public GameTracker(Client client)
	{
		this.client = client;
	}

	public void setListeners(Runnable onGameStart, Runnable onGameEnd)
	{
		this.onGameStart = onGameStart;
		this.onGameEnd = onGameEnd;
	}

	// ---------------------------------------------------------------- events

	public void onHudScript(Object[] args)
	{
		HudState next = HudState.parse(args, client.getTickCount());
		if (next == null)
		{
			return;
		}
		HudState prev = hud;
		hud = next;
		int tick = next.tick;
		if (phase == GamePhase.MINING || phase == GamePhase.CRAFTING || phase == GamePhase.CLOSING)
		{
			// The HUD resets to 0 when the round ends; remember the best reading for the summary.
			lastEnergyElemental = Math.max(lastEnergyElemental, next.elementalEnergy);
			lastEnergyCatalytic = Math.max(lastEnergyCatalytic, next.catalyticEnergy);
		}

		// Portal tracking.
		if (next.portalOpen())
		{
			if (!portalOpen)
			{
				portalsThisGame++;
				lastPortalTick = tick;
				if (gameStartTick >= 0)
				{
					portalSpawnSeconds.add(ticksToSeconds(tick - gameStartTick));
				}
			}
			portalCloseTick = next.portalTicks >= 0 ? tick + next.portalTicks : -1;
		}
		portalOpen = next.portalOpen();

		GamePhase hudPhase = next.phase();
		switch (hudPhase)
		{
			case MINING:
				if (phase == GamePhase.CRAFTING)
				{
					// Altar indices can read 0 for a moment while the altars rotate: stay in crafting.
					break;
				}
				if (phase != GamePhase.MINING)
				{
					// The start message can be missed (chat filter, plugin enabled late): back-date from the countdown.
					if (gameStartTick < 0 || phase == GamePhase.ENDED || phase == GamePhase.WAITING || phase == GamePhase.IDLE)
					{
						int elapsed = next.guardianTicks >= 0 && next.guardianTicks <= MINING_TICKS ? MINING_TICKS - next.guardianTicks : 0;
						startGame(tick - elapsed, elapsed > 20);
					}
				}
				phase = GamePhase.MINING;
				break;
			case CRAFTING:
				if (phase != GamePhase.CRAFTING)
				{
					if (gameStartTick < 0 || phase == GamePhase.ENDED || phase == GamePhase.WAITING || phase == GamePhase.IDLE)
					{
						// Enabled mid-game: we can't know exactly when it started.
						startGame(tick - MINING_TICKS, true);
					}
					altarsOpenTick = prev != null && prev.altarsOpen() ? altarsOpenTick : tick;
				}
				phase = GamePhase.CRAFTING;
				break;
			case CLOSING:
				if (phase == GamePhase.MINING || phase == GamePhase.CRAFTING)
				{
					beginClosing(tick, next.power >= next.maxPower);
				}
				break;
			case ENDED:
				if (phase == GamePhase.MINING || phase == GamePhase.CRAFTING || phase == GamePhase.CLOSING)
				{
					endGame(tick);
				}
				break;
			default:
				break;
		}
	}

	public void onChatMessage(String message)
	{
		int tick = client.getTickCount();
		if (message.contains("The rift becomes active!"))
		{
			startGame(tick, false);
			phase = GamePhase.MINING;
			return;
		}
		Matcher m = COUNTDOWN.matcher(message);
		if (m.find())
		{
			countdownEndTick = tick + secondsToTicks(Integer.parseInt(m.group(1)));
			if (phase == GamePhase.ENDED || phase == GamePhase.IDLE)
			{
				phase = GamePhase.WAITING;
			}
			return;
		}
		if (message.contains("The Great Guardian successfully closed the rift"))
		{
			beginClosing(tick, true);
			return;
		}
		if (message.contains("The Great Guardian was defeated"))
		{
			beginClosing(tick, false);
			return;
		}
		m = REWARD_TOTAL.matcher(message);
		if (m.find())
		{
			bankedElemental = parseInt(m.group(1));
			bankedCatalytic = parseInt(m.group(2));
			if (phase == GamePhase.CLOSING || phase == GamePhase.CRAFTING || phase == GamePhase.MINING)
			{
				endGame(tick);
			}
			return;
		}
		m = CHECK_POINTS.matcher(message);
		if (m.find())
		{
			bankedCatalytic = parseInt(m.group(1));
			bankedElemental = parseInt(m.group(2));
			return;
		}
		if (message.contains("You found some loot:") && bankedElemental > 0 && bankedCatalytic > 0)
		{
			bankedElemental--;
			bankedCatalytic--;
		}
	}

	/** The "Check" dialogue on the Rewards Guardian also shows banked points. */
	public void readDialogue()
	{
		Widget text = client.getWidget(InterfaceID.Messagebox.TEXT);
		if (text == null || text.getText() == null)
		{
			return;
		}
		Matcher m = CHECK_POINTS.matcher(text.getText().replace("<br>", " "));
		if (m.find())
		{
			bankedCatalytic = parseInt(m.group(1));
			bankedElemental = parseInt(m.group(2));
		}
	}

	public void onGameTick()
	{
		int tick = client.getTickCount();
		readInventory();
		readPouches();
		observeOpenGuardians();
		location = locate();
		if (questCheckTick < 0 || tick - questCheckTick > 100)
		{
			refreshQuests();
			questCheckTick = tick;
		}
		boolean inArea = location != Location.OUTSIDE || hudVisible();
		if (!inArea)
		{
			if (phase != GamePhase.IDLE && (phase == GamePhase.WAITING || phase == GamePhase.ENDED))
			{
				phase = GamePhase.IDLE;
			}
		}
		else if (phase == GamePhase.IDLE)
		{
			phase = GamePhase.WAITING;
		}
		if (phase == GamePhase.CLOSING && closingTick >= 0 && tick > closingTick + CLOSING_TICKS + 10)
		{
			endGame(closingTick + CLOSING_TICKS);
		}
		if (phase == GamePhase.ENDED && gameEndTick >= 0 && tick > gameEndTick + 25)
		{
			// Show "game over" briefly, then start preparing for the next round.
			phase = inArea ? GamePhase.WAITING : GamePhase.IDLE;
		}
		if (countdownEndTick >= 0 && tick > countdownEndTick + 20 && phase == GamePhase.WAITING)
		{
			countdownEndTick = -1;
		}
	}

	public void onGameStateLoading()
	{
		altarPortals.clear();
		cellTiles.clear();
		objects.clear();
		altarObject = null;
		altarExit = null;
		greatGuardian = null;
		cordelia = null;
		barriers.clear();
	}

	public void onObjectSpawned(TileObject object)
	{
		int id = object.getId();
		Altar portal = Altar.fromPortal(id);
		if (portal != null)
		{
			altarPortals.put(portal, object);
			return;
		}
		Altar altar = Altar.fromAltarObject(id);
		if (altar != null)
		{
			altarObject = object;
			altarRoom = altar;
			return;
		}
		if (Altar.fromExitPortal(id) != null)
		{
			altarExit = object;
			return;
		}
		if (isCellTile(id))
		{
			cellTiles.put(object.getWorldLocation(), object);
			return;
		}
		if (TRACKED.contains(id))
		{
			List<TileObject> list = objects.computeIfAbsent(id, k -> new ArrayList<>());
			list.removeIf(o -> o.getWorldLocation().equals(object.getWorldLocation()));
			list.add(object);
		}
	}

	public void onObjectDespawned(TileObject object)
	{
		int id = object.getId();
		Altar portal = Altar.fromPortal(id);
		if (portal != null && altarPortals.get(portal) == object)
		{
			altarPortals.remove(portal);
			return;
		}
		if (altarObject == object)
		{
			altarObject = null;
			altarRoom = null;
			return;
		}
		if (altarExit == object)
		{
			altarExit = null;
			return;
		}
		if (isCellTile(id))
		{
			TileObject current = cellTiles.get(object.getWorldLocation());
			if (current == object)
			{
				cellTiles.remove(object.getWorldLocation());
			}
			return;
		}
		List<TileObject> list = objects.get(id);
		if (list != null)
		{
			list.remove(object);
		}
	}

	public void onNpcSpawned(NPC npc)
	{
		int id = npc.getId();
		if (id == NpcID.GOTR_GREAT_GUARDIAN)
		{
			greatGuardian = npc;
		}
		else if (id == NpcID.GOTR_CORDELIA || id == NpcID.GOTR_CORDELIA_1OP || id == NpcID.GOTR_CORDELIA_2OPS)
		{
			cordelia = npc;
		}
		else if (isBarrierNpc(id))
		{
			barriers.add(npc);
		}
	}

	public void onNpcDespawned(NPC npc)
	{
		if (npc == greatGuardian)
		{
			greatGuardian = null;
		}
		else if (npc == cordelia)
		{
			cordelia = null;
		}
		barriers.remove(npc);
	}

	/** Picks up objects already in the scene, e.g. when the plugin is switched on inside the temple. */
	public void scanScene()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		Scene scene = wv.getScene();
		if (scene == null)
		{
			return;
		}
		Tile[][][] tiles = scene.getTiles();
		int plane = wv.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return;
		}
		for (Tile[] row : tiles[plane])
		{
			if (row == null)
			{
				continue;
			}
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}
				GameObject[] gos = tile.getGameObjects();
				if (gos != null)
				{
					for (GameObject go : gos)
					{
						if (go != null)
						{
							onObjectSpawned(go);
						}
					}
				}
				if (tile.getGroundObject() != null)
				{
					onObjectSpawned(tile.getGroundObject());
				}
				if (tile.getDecorativeObject() != null)
				{
					onObjectSpawned(tile.getDecorativeObject());
				}
				if (tile.getWallObject() != null)
				{
					onObjectSpawned(tile.getWallObject());
				}
			}
		}
		for (NPC npc : wv.npcs())
		{
			if (npc != null)
			{
				onNpcSpawned(npc);
			}
		}
	}

	// ---------------------------------------------------------------- internals

	private void startGame(int tick, boolean late)
	{
		if (gameStartTick >= 0 && (phase == GamePhase.MINING || phase == GamePhase.CRAFTING) && !late)
		{
			// Chat message arrived after the HUD already started the game: just correct the time.
			gameStartTick = tick;
			joinedLate = false;
			return;
		}
		gameStartTick = tick;
		joinedLate = late;
		altarsOpenTick = -1;
		closingTick = -1;
		gameEndTick = -1;
		countdownEndTick = -1;
		portalsThisGame = 0;
		lastPortalTick = -1;
		portalSpawnSeconds.clear();
		lastEnergyElemental = 0;
		lastEnergyCatalytic = 0;
		won = false;
		onGameStart.run();
	}

	private void beginClosing(int tick, boolean success)
	{
		if (phase == GamePhase.CLOSING || phase == GamePhase.ENDED)
		{
			return;
		}
		phase = GamePhase.CLOSING;
		closingTick = tick;
		won = success;
	}

	private void endGame(int tick)
	{
		if (phase == GamePhase.ENDED)
		{
			return;
		}
		if (closingTick < 0)
		{
			closingTick = tick;
		}
		phase = GamePhase.ENDED;
		gameEndTick = tick;
		onGameEnd.run();
	}

	/**
	 * Open guardians play their "active" animation. This keeps working after the Great Guardian is
	 * charged, when the rifts stay open for a short while but the HUD no longer reports them.
	 */
	private void observeOpenGuardians()
	{
		Altar elemental = null;
		Altar catalytic = null;
		boolean any = false;
		for (Map.Entry<Altar, TileObject> e : altarPortals.entrySet())
		{
			if (!(e.getValue() instanceof GameObject))
			{
				continue;
			}
			Renderable r = ((GameObject) e.getValue()).getRenderable();
			if (!(r instanceof DynamicObject))
			{
				continue;
			}
			any = true;
			Animation anim = ((DynamicObject) r).getAnimation();
			if (anim != null && anim.getId() == AnimationID.STATUE_RUNESTONE01_ACTIVE)
			{
				if (e.getKey().getAlignment() == Alignment.ELEMENTAL)
				{
					elemental = e.getKey();
				}
				else
				{
					catalytic = e.getKey();
				}
			}
		}
		guardiansObserved = any;
		observedElemental = elemental;
		observedCatalytic = catalytic;
	}

	private void readInventory()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		inventory = Inventory.read(inv == null ? null : inv.getItems());
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		yewLantern = worn != null && worn.contains(ItemID.ABYSSAL_LANTERN_YEW);
		if (worn != null && !inventory.pickaxe)
		{
			for (net.runelite.api.Item item : worn.getItems())
			{
				if (item != null && Inventory.isPickaxe(item.getId()))
				{
					inventory.pickaxe = true;
					break;
				}
			}
		}
	}

	/** Yew logs in the abyssal lantern raise the total energy cap to 1,320. */
	public boolean isYewLantern()
	{
		return yewLantern;
	}

	private void readPouches()
	{
		for (PouchType type : PouchType.values())
		{
			if (!inventory.pouches.containsKey(type))
			{
				pouchContents.remove(type);
				continue;
			}
			int stored = client.getVarbitValue(type.getVarbit());
			pouchContents.put(type, stored);
			if (inventory.pouches.get(type) && stored > pouchLearnedCapacity.getOrDefault(type, 0))
			{
				pouchLearnedCapacity.put(type, stored);
			}
		}
	}

	private void refreshQuests()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Set<Altar> locked = EnumSet.noneOf(Altar.class);
		for (Altar altar : Altar.values())
		{
			if (altar.getQuest() != null && altar.getQuest().getState(client) != QuestState.FINISHED)
			{
				locked.add(altar);
			}
		}
		questLocked = locked;
	}

	private Location locate()
	{
		Player player = client.getLocalPlayer();
		if (player == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return Location.OUTSIDE;
		}
		WorldPoint where = player.getWorldLocation();
		if (where.getRegionID() == TEMPLE_REGION)
		{
			if (where.getX() > 3587 && where.getX() < 3594 && where.getY() > 9496 && where.getY() < 9516)
			{
				return Location.HUGE_REMAINS;
			}
			return inLobby(where) ? Location.LOBBY : Location.TEMPLE;
		}
		if (altarObject != null && altarRoom != null && (hudVisible() || phase == GamePhase.MINING
			|| phase == GamePhase.CRAFTING || phase == GamePhase.CLOSING))
		{
			return Location.ALTAR_ROOM;
		}
		TileObject barrier = first(ObjectID.GOTR_BARRIER, ObjectID.GOTR_BARRIER_CLOSED, ObjectID.GOTR_BARRIER_NOENTRY);
		if (barrier != null && where.distanceTo2D(barrier.getWorldLocation()) <= 40)
		{
			return Location.LOBBY;
		}
		return Location.OUTSIDE;
	}

	private boolean inLobby(WorldPoint where)
	{
		TileObject barrier = first(ObjectID.GOTR_BARRIER, ObjectID.GOTR_BARRIER_CLOSED, ObjectID.GOTR_BARRIER_NOENTRY);
		if (barrier == null)
		{
			return false;
		}
		int barrierY = barrier.getWorldLocation().getY();
		boolean templeNorth = greatGuardian == null || greatGuardian.getWorldLocation().getY() > barrierY;
		return templeNorth ? where.getY() < barrierY : where.getY() > barrierY;
	}

	public boolean hudVisible()
	{
		Widget w = client.getWidget(InterfaceID.GotrHud.CONTENT);
		return w != null && !w.isHidden();
	}

	private static boolean isCellTile(int id)
	{
		return id == ObjectID.GOTR_CELL_TILE_BROKEN || id == ObjectID.GOTR_CELL_TILE_BROKEN_REPAIRABLE
			|| id == ObjectID.GOTR_CELL_TILE_INACTIVE || id == ObjectID.GOTR_CELL_TILE_INACTIVE_NOOP
			|| CellTier.fromTileObject(id) != null;
	}

	private static boolean isBarrierNpc(int id)
	{
		return id == NpcID.GOTR_BARRIER_NPC_TIER1 || id == NpcID.GOTR_BARRIER_NPC_TIER1_LARGE
			|| id == NpcID.GOTR_BARRIER_NPC_TIER2 || id == NpcID.GOTR_BARRIER_NPC_TIER2_LARGE
			|| id == NpcID.GOTR_BARRIER_NPC_TIER3 || id == NpcID.GOTR_BARRIER_NPC_TIER3_LARGE
			|| id == NpcID.GOTR_BARRIER_NPC_TIER4 || id == NpcID.GOTR_BARRIER_NPC_TIER4_LARGE;
	}

	private static final Set<Integer> TRACKED = Set.of(
		ObjectID.GOTR_WORKBENCH,
		ObjectID.GOTR_DEPOSITCHEST,
		ObjectID.GOTR_UNCHARGED_CELLS,
		ObjectID.GOTR_WEAK_CELLS,
		ObjectID.GOTR_GUARDIAN_SPAWN_EAST,
		ObjectID.GOTR_GUARDIAN_SPAWN_WEST,
		ObjectID.GOTR_ESSENCE_TIER_1_LARGE,
		ObjectID.GOTR_ESSENCE_TIER_1_LARGE_B,
		ObjectID.GOTR_ESSENCE_TIER_2,
		ObjectID.GOTR_ESSENCE_TIER_3,
		ObjectID.GOTR_AGILITY_PORTAL_TOP,
		ObjectID.GOTR_AGILITY_PORTAL_BOTTOM_CHILD,
		ObjectID.GOTR_AGILITY_PORTAL_BOTTOM_PARENT,
		ObjectID.GOTR_AGILITY_SHORTCUT_TOP,
		ObjectID.GOTR_AGILITY_SHORTCUT_MIDDLE,
		ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM,
		ObjectID.GOTR_BARRIER,
		ObjectID.GOTR_BARRIER_CLOSED,
		ObjectID.GOTR_BARRIER_NOENTRY
	);

	private static int parseInt(String s)
	{
		return Integer.parseInt(s.replace(",", ""));
	}

	public static int secondsToTicks(int seconds)
	{
		return (int) Math.round(seconds / 0.6);
	}

	public static int ticksToSeconds(int ticks)
	{
		return (int) Math.round(ticks * 0.6);
	}

	// ---------------------------------------------------------------- queries

	public TileObject first(int... ids)
	{
		for (int id : ids)
		{
			List<TileObject> list = objects.get(id);
			if (list != null && !list.isEmpty())
			{
				return list.get(0);
			}
		}
		return null;
	}

	public List<TileObject> all(int... ids)
	{
		List<TileObject> out = new ArrayList<>();
		for (int id : ids)
		{
			List<TileObject> list = objects.get(id);
			if (list != null)
			{
				out.addAll(list);
			}
		}
		return out;
	}

	public HudState getHud()
	{
		return hud;
	}

	public GamePhase getPhase()
	{
		return phase;
	}

	public Location getLocation()
	{
		return location;
	}

	public Altar getAltarRoom()
	{
		return location == Location.ALTAR_ROOM ? altarRoom : null;
	}

	public Inventory getInventory()
	{
		return inventory;
	}

	public int getEssenceInPouches()
	{
		int total = 0;
		for (int v : pouchContents.values())
		{
			total += v;
		}
		return total;
	}

	public int getPouchCapacity(int runecraftLevel)
	{
		int total = 0;
		for (Map.Entry<PouchType, Boolean> e : inventory.pouches.entrySet())
		{
			int nominal = e.getKey().capacity(runecraftLevel);
			if (e.getValue())
			{
				// A degraded pouch holds less: use the most we have seen it hold, or three quarters.
				int learned = pouchLearnedCapacity.getOrDefault(e.getKey(), 0);
				total += learned > 0 ? Math.min(learned, nominal) : nominal * 3 / 4;
			}
			else
			{
				total += nominal;
			}
		}
		return total;
	}

	public Map<PouchType, Integer> getPouchContents()
	{
		return pouchContents;
	}

	public Set<Altar> getQuestLocked()
	{
		return questLocked;
	}

	public boolean isInGame()
	{
		return phase == GamePhase.MINING || phase == GamePhase.CRAFTING || phase == GamePhase.CLOSING;
	}

	/** Seconds since the rift opened, or -1. */
	public int getGameSeconds()
	{
		if (gameStartTick < 0 || !(isInGame() || phase == GamePhase.ENDED))
		{
			return -1;
		}
		int end = phase == GamePhase.ENDED && gameEndTick >= 0 ? gameEndTick : client.getTickCount();
		return Math.max(0, ticksToSeconds(end - gameStartTick));
	}

	public int getGameStartTick()
	{
		return gameStartTick;
	}

	public int getAltarsOpenTick()
	{
		return altarsOpenTick;
	}

	public int getClosingTick()
	{
		return closingTick;
	}

	public int getGameEndTick()
	{
		return gameEndTick;
	}

	public boolean isJoinedLate()
	{
		return joinedLate;
	}

	public boolean isWon()
	{
		return won;
	}

	public int getCountdownSeconds()
	{
		if (countdownEndTick < 0)
		{
			return -1;
		}
		return Math.max(0, ticksToSeconds(countdownEndTick - client.getTickCount()));
	}

	/** Mining phase: seconds until the altars open. */
	public int getSecondsUntilAltars()
	{
		if (phase != GamePhase.MINING)
		{
			return -1;
		}
		if (hud != null && hud.guardianTicks >= 0 && hud.guardianTicks <= MINING_TICKS + 50)
		{
			int sinceRead = client.getTickCount() - hud.tick;
			return Math.max(0, ticksToSeconds(hud.guardianTicks - sinceRead));
		}
		if (gameStartTick >= 0)
		{
			return Math.max(0, ticksToSeconds(gameStartTick + MINING_TICKS - client.getTickCount()));
		}
		return -1;
	}

	/** Crafting phase: ticks until the open altars rotate. */
	public int getAltarTicksLeft()
	{
		if (phase != GamePhase.CRAFTING || hud == null || hud.guardianTicks < 0)
		{
			return -1;
		}
		return Math.max(0, hud.guardianTicks - (client.getTickCount() - hud.tick));
	}

	/**
	 * The open altar of a type. While crafting the HUD is the source (with the guardian animations as a
	 * fallback); while closing, the animations are, since the HUD stops reporting the altars left open.
	 */
	public Altar getActive(Alignment alignment)
	{
		if (phase != GamePhase.CRAFTING && phase != GamePhase.CLOSING)
		{
			return null;
		}
		Altar observed = alignment == Alignment.ELEMENTAL ? observedElemental : observedCatalytic;
		Altar fromHud = hud == null ? null
			: Altar.fromHud(alignment, alignment == Alignment.ELEMENTAL ? hud.elementalIndex : hud.catalyticIndex);
		if (phase == GamePhase.CLOSING)
		{
			return guardiansObserved ? observed : fromHud;
		}
		return fromHud != null ? fromHud : observed;
	}

	public boolean isPortalOpen()
	{
		return portalOpen && isInGame();
	}

	public int getPortalTicksLeft()
	{
		if (!isPortalOpen() || portalCloseTick < 0)
		{
			return -1;
		}
		return Math.max(0, portalCloseTick - client.getTickCount());
	}

	public int getSecondsSincePortal()
	{
		if (lastPortalTick < 0 || !isInGame())
		{
			return -1;
		}
		return ticksToSeconds(client.getTickCount() - lastPortalTick);
	}

	/** Game second at which the next huge-remains portal is expected, or -1. */
	public int getNextPortalGameSecond()
	{
		if (gameStartTick < 0)
		{
			return -1;
		}
		if (lastPortalTick < 0)
		{
			return FIRST_PORTAL_SECONDS;
		}
		return ticksToSeconds(lastPortalTick - gameStartTick) + PORTAL_INTERVAL_SECONDS;
	}

	public int getLastPortalGameSecond()
	{
		if (gameStartTick < 0 || lastPortalTick < 0)
		{
			return -1;
		}
		return ticksToSeconds(lastPortalTick - gameStartTick);
	}

	/** Game seconds at which this round's portals spawned. */
	public List<Integer> getPortalSpawnSeconds()
	{
		return portalSpawnSeconds;
	}

	public int getPortalsThisGame()
	{
		return portalsThisGame;
	}

	public String getPortalDirection()
	{
		Widget w = client.getWidget(InterfaceID.GotrHud.PORTAL_POSITION);
		if (w != null && !w.isHidden() && w.getText() != null)
		{
			portalDirection = w.getText().trim();
		}
		else
		{
			portalDirection = null;
		}
		return portalDirection;
	}

	public int getEnergyElemental()
	{
		return hud == null || !isInGame() ? lastEnergyElemental : Math.max(hud.elementalEnergy, lastEnergyElemental);
	}

	public int getEnergyCatalytic()
	{
		return hud == null || !isInGame() ? lastEnergyCatalytic : Math.max(hud.catalyticEnergy, lastEnergyCatalytic);
	}

	public int getPowerPercent()
	{
		return hud == null ? 0 : hud.powerPercent();
	}

	public int getGuardians()
	{
		return hud == null ? 0 : hud.guardians;
	}

	public int getGuardiansMax()
	{
		return hud == null || hud.maxGuardians <= 0 ? 10 : hud.maxGuardians;
	}

	public int getBankedElemental()
	{
		return bankedElemental;
	}

	public int getBankedCatalytic()
	{
		return bankedCatalytic;
	}

	public void setBanked(int elemental, int catalytic)
	{
		this.bankedElemental = elemental;
		this.bankedCatalytic = catalytic;
	}

	public Map<Altar, TileObject> getAltarPortals()
	{
		return altarPortals;
	}

	public Collection<TileObject> getCellTiles()
	{
		return cellTiles.values();
	}

	public TileObject getAltarObject()
	{
		return location == Location.ALTAR_ROOM ? altarObject : null;
	}

	public TileObject getAltarExit()
	{
		return location == Location.ALTAR_ROOM ? altarExit : null;
	}

	public NPC getGreatGuardian()
	{
		return greatGuardian;
	}

	public NPC getCordelia()
	{
		return cordelia;
	}

	public boolean hasRepairableTile()
	{
		for (TileObject t : cellTiles.values())
		{
			if (t.getId() == ObjectID.GOTR_CELL_TILE_BROKEN_REPAIRABLE)
			{
				return true;
			}
		}
		return false;
	}

	public boolean hasEmptyTile()
	{
		for (TileObject t : cellTiles.values())
		{
			if (t.getId() == ObjectID.GOTR_CELL_TILE_INACTIVE)
			{
				return true;
			}
		}
		return false;
	}

	/** Lowest barrier health in percent, or -1. */
	public int getLowestBarrierPercent()
	{
		int lowest = -1;
		for (NPC npc : barriers)
		{
			int ratio = npc.getHealthRatio();
			int scale = npc.getHealthScale();
			if (ratio >= 0 && scale > 0)
			{
				int pct = ratio * 100 / scale;
				lowest = lowest < 0 ? pct : Math.min(lowest, pct);
			}
		}
		return lowest;
	}

	public int getRunecraftLevel()
	{
		return client.getBoostedSkillLevel(Skill.RUNECRAFT);
	}

	public int getAgilityLevel()
	{
		return client.getBoostedSkillLevel(Skill.AGILITY);
	}
}
