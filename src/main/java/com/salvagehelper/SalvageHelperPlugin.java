package com.salvagehelper;

import com.google.inject.Provides;
import java.util.Locale;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.InfoBoxMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Salvage Helper",
	description = "Sailing salvaging: cargo hold swap, count and alerts, wreck timers and double spots, crew reminders and more",
	tags = {"sailing", "salvage", "salvaging", "cargo", "hold", "crew", "crewmate", "hook", "wreck", "shipwreck",
		"timer", "afk", "swap", "hide", "notify", "banner"}
)
public class SalvageHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SalvageHelperConfig config;

	@Inject
	private CargoHoldSwapper cargoHoldSwapper;

	@Inject
	private CrewTracker crewTracker;

	@Inject
	private BoatObjects boatObjects;

	@Inject
	private CargoHoldTracker cargoHoldTracker;

	@Inject
	private WreckTracker wreckTracker;

	@Inject
	private SalvageSession session;

	@Inject
	private HookMonitor hookMonitor;

	@Inject
	private SalvageAlerts alerts;

	@Inject
	private Reminders reminders;

	@Inject
	private Banner banner;

	@Inject
	private BannerOverlay bannerOverlay;

	@Inject
	private HoldCountOverlay holdCountOverlay;

	@Inject
	private HoldSalvageOverlay holdSalvageOverlay;

	@Inject
	private NextStepOverlay nextStepOverlay;

	@Inject
	private WreckOverlay wreckOverlay;

	@Inject
	private SalvageCounterOverlay counterOverlay;

	/** Crew salvage lines seen since the last game tick. */
	private int crewLinesThisTick;
	/** The game said the player deposited into the hold since the last tick. */
	private boolean depositMessage;
	/** Tick the player clicked a deposit option on the hold, or -1. */
	private int depositClickTick = -1;
	/** How long after the click the inventory may take to empty (the walk to the hold). */
	private static final int DEPOSIT_WINDOW_TICKS = 30;

	// Same mechanism as the core Entity Hider: returning false from addEntity skips that actor
	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			return crewTracker.shouldDraw(renderable, ui);
		}
	};

	@Provides
	SalvageHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SalvageHelperConfig.class);
	}

	@Override
	protected void startUp()
	{
		renderCallbackManager.register(renderCallback);
		overlayManager.add(wreckOverlay);
		overlayManager.add(holdCountOverlay);
		overlayManager.add(nextStepOverlay);
		overlayManager.add(holdSalvageOverlay);
		overlayManager.add(counterOverlay);
		overlayManager.add(bannerOverlay);
		alerts.startUp(this);
		clientThread.invokeLater(cargoHoldSwapper::reloadConfig);
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(renderCallback);
		overlayManager.remove(wreckOverlay);
		overlayManager.remove(holdCountOverlay);
		overlayManager.remove(nextStepOverlay);
		overlayManager.remove(holdSalvageOverlay);
		overlayManager.remove(counterOverlay);
		overlayManager.remove(bannerOverlay);
		alerts.shutDown();
		cargoHoldSwapper.reset();
		resetState();
		// Turning the plugin off ends the counter session too; a logout only pauses it
		session.reset();
	}

	private void resetState()
	{
		crewTracker.reset();
		boatObjects.reset();
		cargoHoldTracker.reset();
		wreckTracker.reset();
		session.loggedOut();
		hookMonitor.reset();
		reminders.reset();
		banner.reset();
		crewLinesThisTick = 0;
		depositMessage = false;
		depositClickTick = -1;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SalvageHelperConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("cargoLimit".equals(event.getKey()))
		{
			alerts.limitChanged();
		}

		clientThread.invokeLater(() ->
		{
			cargoHoldSwapper.reloadConfig();
			if (!config.hideIdleCrewmates())
			{
				crewTracker.reset();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		// Every world view is torn down on these, so drop stale references
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			resetState();
		}
		// The sea (top-level scene) is rebuilt on a region load and its wrecks spawn again. Boats keep
		// their own world views, so their hooks and holds are left alone.
		else if (state == GameState.LOADING)
		{
			wreckTracker.reset();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			cargoHoldSwapper.updateInventory(event.getItemContainer());
		}
		else
		{
			cargoHoldTracker.containerChanged(event.getContainerId(), event.getItemContainer());
		}
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		Actor actor = event.getActor();
		String text = event.getOverheadText();
		WorldView theirs = actor.getWorldView();
		if (text == null || text.isEmpty() || theirs == null)
		{
			return;
		}

		WorldView mine = boatObjects.ownBoatView();
		boolean onMyBoat = mine != null && theirs == mine;
		SalvageHelperConfig.CrewTextMode hide = config.hideCrewText();

		if (actor instanceof NPC)
		{
			if (theirs.isTopLevel())
			{
				// Crew always stand on a boat
				return;
			}
			if (onMyBoat)
			{
				readCrewLine(text);
			}
			if (hide == SalvageHelperConfig.CrewTextMode.ALL_BOATS
				|| (hide == SalvageHelperConfig.CrewTextMode.OWN_BOAT && onMyBoat))
			{
				actor.setOverheadText("");
			}
		}
		else if (actor instanceof Player && CrewSpeech.isCrewOrder(text))
		{
			boolean mineToHide = actor == client.getLocalPlayer() || onMyBoat;
			if (hide == SalvageHelperConfig.CrewTextMode.ALL_BOATS
				|| (hide == SalvageHelperConfig.CrewTextMode.OWN_BOAT && mineToHide))
			{
				actor.setOverheadText("");
			}
		}
	}

	private void readCrewLine(String text)
	{
		if (CrewSpeech.isHoldFull(text))
		{
			cargoHoldTracker.holdReportedFull();
		}
		else if (CrewSpeech.isCrewSalvage(text))
		{
			crewLinesThisTick++;
			cargoHoldTracker.crewSalvaged();
			reminders.crewLine();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (CrewSpeech.isPlayerDeposit(event.getMessage()))
		{
			depositMessage = true;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		String target = Text.removeTags(event.getMenuTarget()).toLowerCase(Locale.ROOT);
		if (target.contains("cargo hold") && option.contains("deposit") && !option.equals("deposit-held"))
		{
			depositClickTick = client.getTickCount();
		}
		else if (cancelsWalk(event.getMenuAction()))
		{
			// Walking or acting somewhere else means the player is no longer heading to the hold
			depositClickTick = -1;
		}
	}

	private static boolean cancelsWalk(MenuAction action)
	{
		switch (action)
		{
			case WALK:
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() == client.getLocalPlayer() && event.getHitsplat().getAmount() > 0)
		{
			reminders.playerHit();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.SAILING)
		{
			session.sailingXpChanged(event.getXp());
		}
	}

	// Low priority so this runs after other plugins' menu swaps; otherwise a plugin that rewrites the
	// menu later in the same event puts the original left-click back
	@Subscribe(priority = -100)
	public void onPostMenuSort(PostMenuSort event)
	{
		cargoHoldSwapper.swap();
	}

	@Subscribe(priority = -100)
	public void onMenuOpened(MenuOpened event)
	{
		cargoHoldSwapper.menuOpened();
	}

	@Subscribe
	public void onInfoBoxMenuClicked(InfoBoxMenuClicked event)
	{
		if (alerts.isCargoInfoBox(event.getInfoBox())
			&& CargoInfoBox.SET_LIMIT_OPTION.equals(event.getEntry().getOption()))
		{
			alerts.promptForLimit();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Every container change and chat line of this tick has arrived by now
		Widget holdInterface = client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);
		boolean holdOpen = holdInterface != null && !holdInterface.isHidden();

		int usedBefore = session.getInventoryUsed();
		int salvageBefore = session.getInventorySalvage();
		int gained = session.tick(crewLinesThisTick, holdOpen);

		// A deposit on the closed hold never sends the hold's contents, so add what left the inventory.
		// The click starts it (the walk to the hold can take several ticks); the chat message, when the
		// game sends one, confirms it.
		int tick = client.getTickCount();
		boolean depositPending = depositMessage
			|| (depositClickTick >= 0 && tick - depositClickTick <= DEPOSIT_WINDOW_TICKS);
		int removed = usedBefore - session.getInventoryUsed();
		if (depositPending && !holdOpen && removed > 0)
		{
			cargoHoldTracker.playerDeposited(removed, salvageBefore - session.getInventorySalvage());
			depositClickTick = -1;
		}
		else if (depositClickTick >= 0 && tick - depositClickTick > DEPOSIT_WINDOW_TICKS)
		{
			depositClickTick = -1;
		}
		crewLinesThisTick = 0;
		depositMessage = false;

		if (gained > 0)
		{
			wreckTracker.salvageGained(boatObjects.ownHookWorldPoints());
		}
		wreckTracker.tick();

		boolean hookJustStopped = hookMonitor.tick(session.isOnBoat(), gained > 0);
		alerts.tick(session.isOnBoat(), hookJustStopped);
		reminders.tick();
		crewTracker.update();
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		boatObjects.spawned(object);
		wreckTracker.spawned(object);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		boatObjects.despawned(object);
		wreckTracker.despawned(object);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		crewTracker.npcDespawned(event.getNpc());
	}
}
