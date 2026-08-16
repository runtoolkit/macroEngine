package io.runtoolkit.macroengine;

import io.runtoolkit.macroengine.api.dialog.DialogService;
import io.runtoolkit.macroengine.api.interaction.InteractionService;
import io.runtoolkit.macroengine.api.perm.PermissionService;
import io.runtoolkit.macroengine.api.wand.WandService;
import io.runtoolkit.macroengine.command.MacroCommands;
import io.runtoolkit.macroengine.config.EngineConfig;
import io.runtoolkit.macroengine.event.EventBus;
import io.runtoolkit.macroengine.fiber.FiberRuntime;
import io.runtoolkit.macroengine.input.InputSystems;
import io.runtoolkit.macroengine.lib.Debounce;
import io.runtoolkit.macroengine.lib.Once;
import io.runtoolkit.macroengine.lib.Scheduler;
import io.runtoolkit.macroengine.lib.Throttle;
import io.runtoolkit.macroengine.load.EngineBootstrap;
import io.runtoolkit.macroengine.player.PlayerRegistry;
import io.runtoolkit.macroengine.queue.TaskQueue;
import io.runtoolkit.macroengine.schedule.ScheduleService;
import io.runtoolkit.macroengine.state.EngineState;
import io.runtoolkit.macroengine.systems.geo.RegionWatch;
import io.runtoolkit.macroengine.systems.hook.HookSystems;
import io.runtoolkit.macroengine.systems.log.LogService;
import io.runtoolkit.macroengine.systems.rate_limit.RateLimiter;
import io.runtoolkit.macroengine.systems.uuid.UuidService;
import io.runtoolkit.macroengine.tick.TickChannel;
import io.runtoolkit.macroengine.tick.TickEngine;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MacroEngineMod implements ModInitializer {
	public static final String MOD_ID = "macroengine";
	public static final String VERSION = "6.1.0";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MacroEngineMod instance;

	private final EngineConfig config = new EngineConfig();
	private final EngineState state = new EngineState();
	private final TickEngine tickEngine = new TickEngine(this);
	private final TaskQueue taskQueue = new TaskQueue();
	private final ScheduleService schedules = new ScheduleService();
	private final PlayerRegistry players = new PlayerRegistry();
	private final EventBus events = new EventBus();
	private final LogService log = new LogService();
	private final UuidService uuids = new UuidService();
	private final HookSystems hooks = new HookSystems();
	private final RegionWatch regionWatch = new RegionWatch();
	private final InputSystems input = new InputSystems();
	private final Scheduler scheduler = new Scheduler();
	private final Once once = new Once();
	private final Throttle throttle = new Throttle();
	private final Debounce<String> debounce = new Debounce<>();
	private final PermissionService perms = new PermissionService();
	private final WandService wands = new WandService();
	private final DialogService dialogs = new DialogService();
	private final FiberRuntime fibers = new FiberRuntime();
	private final InteractionService interactions = new InteractionService();
	private final RateLimiter rateLimiter = new RateLimiter();

	private MinecraftServer server;
	private boolean loaded;

	@Override
	public void onInitialize() {
		instance = this;
		LOGGER.info("MacroEngine v{} (1.21.1 / Yarn) initializing", VERSION);

		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			players.onJoin(handler.getPlayer());
			events.fire(EventBus.Type.ON_JOIN, handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			events.fire(EventBus.Type.ON_LEAVE, handler.getPlayer());
			players.onLeave(handler.getPlayer());
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity sp) {
				wands.onUseItem(sp, hand);
			}
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity sp) {
				interactions.fireUseEntity(sp, entity);
			}
			return ActionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity sp) {
				interactions.fireAttackEntity(sp, entity);
			}
			return ActionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			MacroCommands.register(dispatcher, this));

		LOGGER.info("MacroEngine hooks registered");
	}

	private void onServerStarted(MinecraftServer server) {
		this.server = server;
		EngineBootstrap.bootstrap(this);
		registerDefaultChannels();
		this.loaded = true;
		tickEngine.resume();
		LOGGER.info("MacroEngine v{} LOADED — channels={}", VERSION, tickEngine.channelCount());
	}

	private void registerDefaultChannels() {
		tickEngine.clearChannels();
		tickEngine.register(new TickChannel("time_systems", 1, 0, true, TickEngine::runTimeSystems));
		tickEngine.register(new TickChannel("player_systems", 1, 0, true, TickEngine::runPlayerSystems));
		tickEngine.register(new TickChannel("queue_systems", 1, 0, true, TickEngine::runQueueSystems));
		tickEngine.register(new TickChannel("hud_systems", 2, 1, true, TickEngine::runHudSystems));
		tickEngine.register(new TickChannel("admin_systems", 4, 2, true, TickEngine::runAdminSystems));
		tickEngine.register(new TickChannel("input_systems", 1, 0, true, TickEngine::runInputSystems));
		tickEngine.register(new TickChannel("hook_systems", 1, 0, true, TickEngine::runHookSystems));
		tickEngine.register(new TickChannel("geo_systems", 5, 3, true, TickEngine::runGeoSystems));
		tickEngine.register(new TickChannel("wand_systems", 1, 0, true, (s, e) -> e.mod().getWands().tick()));
	}

	private void onServerStopping(MinecraftServer server) {
		tickEngine.pause();
		taskQueue.clear();
		schedules.clear();
		fibers.clear();
		once.clear();
		throttle.clear();
		debounce.clear();
		this.loaded = false;
		this.server = null;
		LOGGER.info("MacroEngine shut down");
	}

	private void onEndTick(MinecraftServer server) {
		if (!loaded || config.tickPaused || tickEngine.isPaused()) return;
		if (config.tickRate <= 0) return;
		tickEngine.tick(server);
		schedules.tick(server);
		scheduler.tick(server);
		debounce.tick(tickEngine.getTickCounter(), s -> {});
	}

	public static MacroEngineMod get() { return instance; }
	public EngineConfig getConfig() { return config; }
	public EngineState getState() { return state; }
	public TickEngine getTickEngine() { return tickEngine; }
	public TaskQueue getTaskQueue() { return taskQueue; }
	public ScheduleService getSchedules() { return schedules; }
	public PlayerRegistry getPlayers() { return players; }
	public EventBus getEvents() { return events; }
	public LogService getLog() { return log; }
	public UuidService getUuids() { return uuids; }
	public HookSystems getHooks() { return hooks; }
	public RegionWatch getRegionWatch() { return regionWatch; }
	public InputSystems getInput() { return input; }
	public Scheduler getScheduler() { return scheduler; }
	public Once getOnce() { return once; }
	public Throttle getThrottle() { return throttle; }
	public Debounce<String> getDebounce() { return debounce; }
	public PermissionService getPerms() { return perms; }
	public WandService getWands() { return wands; }
	public DialogService getDialogs() { return dialogs; }
	public FiberRuntime getFibers() { return fibers; }
	public InteractionService getInteractions() { return interactions; }
	public RateLimiter getRateLimiter() { return rateLimiter; }
	public MinecraftServer getServer() { return server; }
	public boolean isLoaded() { return loaded; }
}
