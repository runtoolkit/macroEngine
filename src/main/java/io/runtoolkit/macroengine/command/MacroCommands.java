package io.runtoolkit.macroengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.event.EventBus;
import io.runtoolkit.macroengine.input.InputValidator;
import io.runtoolkit.macroengine.systems.geo.RegionWatch;
import io.runtoolkit.macroengine.tick.TickChannel;
import io.runtoolkit.macroengine.tick.TickEngine;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;


public final class MacroCommands {
	private MacroCommands() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, MacroEngineMod mod) {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("macroengine")
			.requires(s -> s.hasPermissionLevel(2));

		root.then(CommandManager.literal("status").executes(ctx -> {
			ctx.getSource().sendFeedback(() -> Text.literal(mod.getTickEngine().statusText()), false);
			return 1;
		}));

		root.then(CommandManager.literal("pause").executes(ctx -> {
			mod.getTickEngine().pause();
			ctx.getSource().sendFeedback(() -> Text.literal("[ME] paused"), true);
			return 1;
		}));

		root.then(CommandManager.literal("resume").executes(ctx -> {
			mod.getTickEngine().resume();
			mod.getConfig().tickPaused = false;
			ctx.getSource().sendFeedback(() -> Text.literal("[ME] resumed"), true);
			return 1;
		}));

		root.then(CommandManager.literal("version").executes(ctx -> {
			ctx.getSource().sendFeedback(() -> Text.literal(
				"MacroEngine v" + MacroEngineMod.VERSION
					+ " loaded=" + mod.isLoaded()
					+ " fibers=" + mod.getFibers().size()
					+ " queue=" + mod.getTaskQueue().size()), false);
			return 1;
		}));

		root.then(CommandManager.literal("debug").executes(ctx -> {
			mod.getConfig().debug = !mod.getConfig().debug;
			ctx.getSource().sendFeedback(() -> Text.literal("[ME] debug=" + mod.getConfig().debug), true);
			return 1;
		}));

		registerChannel(root, mod);
		registerRunQueueSchedule(root, mod);
		registerFiberBatch(root, mod);
		registerEvent(root, mod);
		registerInteraction(root, mod);
		registerToggle(root, mod);
		registerPlayerHelpers(root, mod);
		registerBossbar(root, mod);
		registerRegion(root, mod);
		registerInput(root, mod);
		registerPerm(root, mod);
		registerWand(root, mod);
		registerHook(root, mod);
		registerFreezeScoreboard(root, mod);

		dispatcher.register(root);
	}

	private static void registerChannel(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("channel")
			.then(CommandManager.literal("list").executes(ctx -> {
				for (TickChannel c : mod.getTickEngine().channels()) {
					String line = c.id + " rate=" + c.rate + " off=" + c.offset + " on=" + c.enabled;
					ctx.getSource().sendFeedback(() -> Text.literal(line), false);
				}
				return mod.getTickEngine().channelCount();
			}))
			.then(CommandManager.literal("enable")
				.then(CommandManager.argument("id", StringArgumentType.string()).executes(ctx ->
					setEnabled(ctx.getSource(), mod, StringArgumentType.getString(ctx, "id"), true))))
			.then(CommandManager.literal("disable")
				.then(CommandManager.argument("id", StringArgumentType.string()).executes(ctx ->
					setEnabled(ctx.getSource(), mod, StringArgumentType.getString(ctx, "id"), false))))
			.then(CommandManager.literal("setrate")
				.then(CommandManager.argument("id", StringArgumentType.string())
					.then(CommandManager.argument("rate", IntegerArgumentType.integer(1, 1200)).executes(ctx -> {
						String id = StringArgumentType.getString(ctx, "id");
						int rate = IntegerArgumentType.getInteger(ctx, "rate");
						TickEngine eng = mod.getTickEngine();
						return eng.find(id).map(c -> {
							eng.unregister(id);
							eng.register(new TickChannel(id, rate, c.offset, c.enabled, c.handler));
							ctx.getSource().sendFeedback(() -> Text.literal(id + " rate=" + rate), true);
							return 1;
						}).orElseGet(() -> {
							ctx.getSource().sendError(Text.literal("unknown channel: " + id));
							return 0;
						});
					}))))
			.then(CommandManager.literal("register")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("rate", IntegerArgumentType.integer(1, 1200))
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "id");
							if (mod.getTickEngine().find(id).isPresent()) {
								ctx.getSource().sendError(Text.literal("channel already exists: " + id));
								return 0;
							}
							int rate = IntegerArgumentType.getInteger(ctx, "rate");
							String cmd = StringArgumentType.getString(ctx, "command");
							mod.getTickEngine().register(new TickChannel(id, rate, 0, true,
								(server, eng) -> eng.mod().getCommands().runAsServer(server, cmd)));
							ctx.getSource().sendFeedback(() -> Text.literal("[ME] channel registered " + id), true);
							return 1;
						})))))
			.then(CommandManager.literal("unregister")
				.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
					String id = StringArgumentType.getString(ctx, "id");
					if (!mod.getTickEngine().unregister(id)) {
						ctx.getSource().sendError(Text.literal("unknown channel: " + id));
						return 0;
					}
					ctx.getSource().sendFeedback(() -> Text.literal("[ME] channel removed " + id), true);
					return 1;
				}))));
	}

	private static void registerRunQueueSchedule(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("run")
			.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
				int r = mod.getCommands().runAsServer(ctx.getSource().getServer(),
					StringArgumentType.getString(ctx, "command"));
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] ran → " + r), true);
				return r;
			})));

		root.then(CommandManager.literal("queue")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("delay", IntegerArgumentType.integer(0))
					.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
						mod.getTaskQueue().enqueueCommand(
							IntegerArgumentType.getInteger(ctx, "delay"),
							StringArgumentType.getString(ctx, "command"));
						ctx.getSource().sendFeedback(() -> Text.literal(
							"[ME] queued size=" + mod.getTaskQueue().size()), true);
						return 1;
					}))))
			.then(CommandManager.literal("size").executes(ctx -> {
				int s = mod.getTaskQueue().size();
				ctx.getSource().sendFeedback(() -> Text.literal("queue=" + s), false);
				return s;
			}))
			.then(CommandManager.literal("clear").executes(ctx -> {
				mod.getTaskQueue().clear();
				return 1;
			})));

		root.then(CommandManager.literal("schedule")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("key", StringArgumentType.word())
					.then(CommandManager.argument("delay", IntegerArgumentType.integer(0))
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							mod.getSchedules().scheduleCommand(
								StringArgumentType.getString(ctx, "key"),
								IntegerArgumentType.getInteger(ctx, "delay"),
								StringArgumentType.getString(ctx, "command"));
							return 1;
						})))))
			.then(CommandManager.literal("repeat")
				.then(CommandManager.argument("key", StringArgumentType.word())
					.then(CommandManager.argument("interval", IntegerArgumentType.integer(1))
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							mod.getSchedules().scheduleRepeatingCommand(
								StringArgumentType.getString(ctx, "key"),
								IntegerArgumentType.getInteger(ctx, "interval"),
								StringArgumentType.getString(ctx, "command"));
							return 1;
						})))))
			.then(CommandManager.literal("cancel")
				.then(CommandManager.argument("key", StringArgumentType.word()).executes(ctx -> {
					mod.getSchedules().cancel(StringArgumentType.getString(ctx, "key"));
					return 1;
				})))
			.then(CommandManager.literal("list").executes(ctx -> {
				mod.getSchedules().snapshot().forEach((k, e) ->
					ctx.getSource().sendFeedback(() -> Text.literal(
						k + " next=" + e.nextTick() + " rep=" + e.repeat()), false));
				return mod.getSchedules().size();
			})));
	}

	private static void registerFiberBatch(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("fiber")
			.then(CommandManager.literal("spawn")
				.then(CommandManager.argument("name", StringArgumentType.word())
					.then(CommandManager.argument("times", IntegerArgumentType.integer(1, 10000))
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							var f = mod.getFibers().spawnCommand(
								StringArgumentType.getString(ctx, "name"),
								StringArgumentType.getString(ctx, "command"),
								IntegerArgumentType.getInteger(ctx, "times"));
							ctx.getSource().sendFeedback(() -> Text.literal(
								"[ME] fiber " + f.name + " id=" + f.id), true);
							return 1;
						})))))
			.then(CommandManager.literal("kill")
				.then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
					mod.getFibers().kill(StringArgumentType.getString(ctx, "name"));
					return 1;
				})))
			.then(CommandManager.literal("alive")
				.then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
					boolean a = mod.getFibers().isAlive(StringArgumentType.getString(ctx, "name"));
					ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(a)), false);
					return a ? 1 : 0;
				})))
			.then(CommandManager.literal("count").executes(ctx -> {
				int n = mod.getFibers().size();
				ctx.getSource().sendFeedback(() -> Text.literal("fibers=" + n), false);
				return n;
			})));

		root.then(CommandManager.literal("batch")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
					mod.getBatch().addCommand(StringArgumentType.getString(ctx, "command"));
					ctx.getSource().sendFeedback(() -> Text.literal(
						"[ME] batch size=" + mod.getBatch().size()), false);
					return 1;
				})))
			.then(CommandManager.literal("flush").executes(ctx -> {
				int n = mod.getBatch().flush(ctx.getSource().getServer());
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] flushed " + n), true);
				return n;
			}))
			.then(CommandManager.literal("clear").executes(ctx -> {
				mod.getBatch().clear();
				return 1;
			})));
	}

	private static void registerEvent(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("event")
			.then(CommandManager.literal("on")
				.then(CommandManager.argument("type", StringArgumentType.word())
					.then(CommandManager.argument("id", StringArgumentType.word())
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							String type = StringArgumentType.getString(ctx, "type");
							String id = StringArgumentType.getString(ctx, "id");
							String cmd = StringArgumentType.getString(ctx, "command");
							EventBus.Type t = parseType(type);
							if (t == null) mod.getEvents().onCustomCommand(type, id, cmd);
							else mod.getEvents().onCommand(t, id, cmd);
							return 1;
						})))))
			.then(CommandManager.literal("fire")
				.then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
					String type = StringArgumentType.getString(ctx, "type");
					EventBus.Type t = parseType(type);
					if (t != null) mod.getEvents().fire(t, ctx.getSource().getPlayer());
					else mod.getEvents().fireCustom(type, ctx.getSource().getPlayer());
					return 1;
				}))));
	}

	private static void registerInteraction(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("interaction")
			.then(CommandManager.literal("bind_use")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("target", EntityArgumentType.entity())
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							var entity = EntityArgumentType.getEntity(ctx, "target");
							mod.getInteractions().bindUse(
								StringArgumentType.getString(ctx, "id"),
								entity.getUuid(),
								StringArgumentType.getString(ctx, "command"));
							ctx.getSource().sendFeedback(() -> Text.literal(
								"[ME] bind_use " + StringArgumentType.getString(ctx, "id")
									+ " → " + entity.getUuid()), true);
							return 1;
						})))))
			.then(CommandManager.literal("bind_attack")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("target", EntityArgumentType.entity())
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							var entity = EntityArgumentType.getEntity(ctx, "target");
							mod.getInteractions().bindAttack(
								StringArgumentType.getString(ctx, "id"),
								entity.getUuid(),
								StringArgumentType.getString(ctx, "command"));
							ctx.getSource().sendFeedback(() -> Text.literal(
								"[ME] bind_attack " + StringArgumentType.getString(ctx, "id")
									+ " → " + entity.getUuid()), true);
							return 1;
						})))))
			.then(CommandManager.literal("bind_use_look")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
						var entity = findLookedEntity(p, 8.0);
						if (entity == null) {
							ctx.getSource().sendError(Text.literal("No entity in crosshair (8 blocks)"));
							return 0;
						}
						mod.getInteractions().bindUse(
							StringArgumentType.getString(ctx, "id"),
							entity.getUuid(),
							StringArgumentType.getString(ctx, "command"));
						ctx.getSource().sendFeedback(() -> Text.literal(
							"[ME] bind_use_look → " + entity.getName().getString()
								+ " " + entity.getUuid()), true);
						return 1;
					}))))
			.then(CommandManager.literal("bind_attack_look")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
						var entity = findLookedEntity(p, 8.0);
						if (entity == null) {
							ctx.getSource().sendError(Text.literal("No entity in crosshair (8 blocks)"));
							return 0;
						}
						mod.getInteractions().bindAttack(
							StringArgumentType.getString(ctx, "id"),
							entity.getUuid(),
							StringArgumentType.getString(ctx, "command"));
						ctx.getSource().sendFeedback(() -> Text.literal(
							"[ME] bind_attack_look → " + entity.getName().getString()
								+ " " + entity.getUuid()), true);
						return 1;
					}))))
			.then(CommandManager.literal("unbind")
				.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
					mod.getInteractions().remove(StringArgumentType.getString(ctx, "id"));
					return 1;
				})))
			.then(CommandManager.literal("list").executes(ctx -> {
				mod.getInteractions().listUse().forEach((id, b) ->
					ctx.getSource().sendFeedback(() -> Text.literal("use " + id + " → " + b.target()), false));
				mod.getInteractions().listAttack().forEach((id, b) ->
					ctx.getSource().sendFeedback(() -> Text.literal("atk " + id + " → " + b.target()), false));
				return 1;
			}))
			.then(CommandManager.literal("enable").executes(ctx -> {
				mod.getInteractions().setEnabled(true);
				return 1;
			}))
			.then(CommandManager.literal("disable").executes(ctx -> {
				mod.getInteractions().setEnabled(false);
				return 1;
			})));
	}

	private static void registerToggle(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("toggle")
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("key", StringArgumentType.word())
					.then(CommandManager.argument("value", StringArgumentType.word()).executes(ctx -> {
						boolean v = StringArgumentType.getString(ctx, "value").equalsIgnoreCase("true");
						mod.getToggles().set(StringArgumentType.getString(ctx, "key"), v);
						return 1;
					}))))
			.then(CommandManager.literal("get")
				.then(CommandManager.argument("key", StringArgumentType.word()).executes(ctx -> {
					boolean v = mod.getToggles().get(StringArgumentType.getString(ctx, "key"));
					ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(v)), false);
					return v ? 1 : 0;
				})))
			.then(CommandManager.literal("flip")
				.then(CommandManager.argument("key", StringArgumentType.word()).executes(ctx -> {
					boolean v = mod.getToggles().toggle(StringArgumentType.getString(ctx, "key"));
					ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(v)), false);
					return v ? 1 : 0;
				})))
			.then(CommandManager.literal("list").executes(ctx -> {
				mod.getToggles().all().forEach((k, v) ->
					ctx.getSource().sendFeedback(() -> Text.literal(k + "=" + v), false));
				return 1;
			})));
	}

	private static void registerPlayerHelpers(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("give")
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("item", StringArgumentType.string())
					.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
						mod.getCommands().give(EntityArgumentType.getPlayer(ctx, "player"),
							StringArgumentType.getString(ctx, "item"),
							IntegerArgumentType.getInteger(ctx, "count"));
						return 1;
					})))));

		root.then(CommandManager.literal("effect")
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("effect", StringArgumentType.string())
					.then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
						.then(CommandManager.argument("amp", IntegerArgumentType.integer(0)).executes(ctx -> {
							mod.getCommands().effect(EntityArgumentType.getPlayer(ctx, "player"),
								StringArgumentType.getString(ctx, "effect"),
								IntegerArgumentType.getInteger(ctx, "seconds"),
								IntegerArgumentType.getInteger(ctx, "amp"));
							return 1;
						}))))));

		root.then(CommandManager.literal("heal")
			.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
				mod.getCommands().heal(EntityArgumentType.getPlayer(ctx, "player"));
				return 1;
			})));

		root.then(CommandManager.literal("gamemode")
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("mode", StringArgumentType.word()).executes(ctx -> {
					mod.getCommands().gamemode(EntityArgumentType.getPlayer(ctx, "player"),
						StringArgumentType.getString(ctx, "mode"));
					return 1;
				}))));

		root.then(CommandManager.literal("msg")
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
					mod.getCommands().msg(EntityArgumentType.getPlayer(ctx, "player"),
						StringArgumentType.getString(ctx, "text"));
					return 1;
				}))));

		root.then(CommandManager.literal("actionbar")
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
					mod.getCommands().actionbar(EntityArgumentType.getPlayer(ctx, "player"),
						StringArgumentType.getString(ctx, "text"));
					return 1;
				}))));

		root.then(CommandManager.literal("title")
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
					mod.getTitles().title(EntityArgumentType.getPlayer(ctx, "player"),
						StringArgumentType.getString(ctx, "text"), "", 10, 40, 10);
					return 1;
				}))));

		root.then(CommandManager.literal("rename")
			.then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
				boolean ok = mod.getItems().renameHeld(ctx.getSource().getPlayerOrThrow(),
					StringArgumentType.getString(ctx, "name"));
				if (!ok) {
					ctx.getSource().sendError(Text.literal("empty hand"));
					return 0;
				}
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] renamed"), true);
				return 1;
			})));
	}

	private static void registerBossbar(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("bossbar")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("title", StringArgumentType.greedyString()).executes(ctx -> {
						String id = StringArgumentType.getString(ctx, "id");
						if (mod.getBossbars().exists(id)) {
							ctx.getSource().sendError(Text.literal("bossbar already exists: " + id));
							return 0;
						}
						mod.getBossbars().add(id, StringArgumentType.getString(ctx, "title"),
							BossBar.Color.BLUE, BossBar.Style.PROGRESS);
						ServerPlayerEntity self = ctx.getSource().getPlayer();
						if (self != null) mod.getBossbars().setPlayers(id, self, true);
						ctx.getSource().sendFeedback(() -> Text.literal("[ME] bossbar " + id), true);
						return 1;
					}))))
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("value", IntegerArgumentType.integer(0))
						.then(CommandManager.argument("max", IntegerArgumentType.integer(1)).executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "id");
							int value = IntegerArgumentType.getInteger(ctx, "value");
							int max = IntegerArgumentType.getInteger(ctx, "max");
							if (!mod.getBossbars().setValue(id, value, max)) {
								ctx.getSource().sendError(Text.literal("unknown bossbar: " + id + " — use bossbar add first"));
								return 0;
							}
							ServerPlayerEntity self = ctx.getSource().getPlayer();
							if (self != null) mod.getBossbars().setPlayers(id, self, true);
							ctx.getSource().sendFeedback(() -> Text.literal(
								"[ME] bossbar " + id + " " + value + "/" + max), true);
							return 1;
						})))))
			.then(CommandManager.literal("players")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
						String id = StringArgumentType.getString(ctx, "id");
						if (!mod.getBossbars().setPlayers(id, EntityArgumentType.getPlayer(ctx, "player"), true)) {
							ctx.getSource().sendError(Text.literal("unknown bossbar: " + id));
							return 0;
						}
						return 1;
					}))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
					String id = StringArgumentType.getString(ctx, "id");
					if (!mod.getBossbars().remove(id)) {
						ctx.getSource().sendError(Text.literal("unknown bossbar: " + id));
						return 0;
					}
					return 1;
				}))));
	}

	private static void registerRegion(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("region")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("x1", IntegerArgumentType.integer())
						.then(CommandManager.argument("y1", IntegerArgumentType.integer())
							.then(CommandManager.argument("z1", IntegerArgumentType.integer())
								.then(CommandManager.argument("x2", IntegerArgumentType.integer())
									.then(CommandManager.argument("y2", IntegerArgumentType.integer())
										.then(CommandManager.argument("z2", IntegerArgumentType.integer()).executes(ctx -> {
											ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
											String dim = p.getWorld().getRegistryKey().getValue().toString();
											int x1 = IntegerArgumentType.getInteger(ctx, "x1");
											int y1 = IntegerArgumentType.getInteger(ctx, "y1");
											int z1 = IntegerArgumentType.getInteger(ctx, "z1");
											int x2 = IntegerArgumentType.getInteger(ctx, "x2");
											int y2 = IntegerArgumentType.getInteger(ctx, "y2");
											int z2 = IntegerArgumentType.getInteger(ctx, "z2");
											mod.getRegionWatch().add(new RegionWatch.Region(
												StringArgumentType.getString(ctx, "id"), dim,
												Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
												Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
											return 1;
										})))))))))
			.then(CommandManager.literal("list").executes(ctx -> {
				for (RegionWatch.Region r : mod.getRegionWatch().list()) {
					ctx.getSource().sendFeedback(() -> Text.literal(
						r.id() + " [" + r.minX() + "," + r.minY() + "," + r.minZ()
							+ "→" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]"), false);
				}
				return 1;
			}))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
					mod.getRegionWatch().remove(StringArgumentType.getString(ctx, "id"));
					return 1;
				}))));
	}

	private static void registerInput(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("input")
			.then(CommandManager.literal("summon_cbm").executes(ctx -> {
				mod.getInput().summonCbm(ctx.getSource().getPlayerOrThrow());
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] CBM summoned"), true);
				return 1;
			}))
			.then(CommandManager.literal("book").executes(ctx -> {
				mod.getInput().giveWritableBook(ctx.getSource().getPlayerOrThrow());
				ctx.getSource().sendFeedback(() -> Text.literal(
					"[ME] writable book given — sign/edit to capture, or input book_capture <text>"), true);
				return 1;
			}))
			.then(CommandManager.literal("book_capture")
				.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
					mod.getInput().submitBook(ctx.getSource().getPlayerOrThrow(),
						StringArgumentType.getString(ctx, "text"));
					ctx.getSource().sendFeedback(() -> Text.literal("[ME] book captured"), true);
					return 1;
				})))
			.then(CommandManager.literal("last").executes(ctx -> {
				var in = mod.getInput();
				ctx.getSource().sendFeedback(() -> Text.literal(
					"book=" + nullSafe(in.getLastBookRaw())
						+ "\ncbm=" + nullSafe(in.getLastCbmCommand())
						+ "\ndialog=" + nullSafe(in.getLastDialogRaw())), false);
				return 1;
			}))
			.then(CommandManager.literal("validate")
				.then(CommandManager.argument("type", StringArgumentType.word())
					.then(CommandManager.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
						String type = StringArgumentType.getString(ctx, "type");
						String value = StringArgumentType.getString(ctx, "value");
						String result = switch (type) {
							case "int" -> InputValidator.asInt(value).isPresent()
								? "OK " + InputValidator.asInt(value).getAsInt() : "FAIL";
							case "float" -> InputValidator.asFloat(value).isPresent()
								? "OK " + InputValidator.asFloat(value).getAsDouble() : "FAIL";
							case "bool" -> InputValidator.asBool(value).map(b -> "OK " + b).orElse("FAIL");
							case "tag" -> InputValidator.isTagSafe(value) ? "OK" : "FAIL";
							default -> "unknown type";
						};
						ctx.getSource().sendFeedback(() -> Text.literal(result), false);
						return 1;
					})))));

		root.then(CommandManager.literal("dialog")
			.then(CommandManager.literal("open").executes(ctx -> {
				mod.getDialogs().open(ctx.getSource().getPlayerOrThrow(), (pl, text) ->
					pl.sendMessage(Text.literal("[ME] dialog → " + text), false));
				return 1;
			}))
			.then(CommandManager.literal("open")
				.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
					mod.getDialogs().open(ctx.getSource().getPlayerOrThrow(),
						StringArgumentType.getString(ctx, "id"),
						(pl, text) -> pl.sendMessage(Text.literal("[ME] dialog → " + text), false));
					return 1;
				})))
			.then(CommandManager.literal("show")
				.then(CommandManager.argument("title", StringArgumentType.word())
					.then(CommandManager.argument("body", StringArgumentType.greedyString()).executes(ctx -> {
						mod.getDialogs().show(ctx.getSource().getPlayerOrThrow(),
							StringArgumentType.getString(ctx, "title"),
							StringArgumentType.getString(ctx, "body"));
						return 1;
					}))))
			.then(CommandManager.literal("submit")
				.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
					boolean ok = mod.getDialogs().submit(ctx.getSource().getPlayerOrThrow(),
						StringArgumentType.getString(ctx, "text"));
					ctx.getSource().sendFeedback(() -> Text.literal(ok ? "[ME] submitted" : "[ME] captured"), false);
					return 1;
				})))
			.then(CommandManager.literal("close").executes(ctx -> {
				mod.getDialogs().close(ctx.getSource().getPlayerOrThrow());
				return 1;
			}))
			.then(CommandManager.literal("is_open").executes(ctx -> {
				boolean o = mod.getDialogs().isOpen(ctx.getSource().getPlayerOrThrow());
				ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(o)), false);
				return o ? 1 : 0;
			})));
	}

	private static void registerPerm(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("perm")
			.then(CommandManager.literal("grant")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
						var pl = EntityArgumentType.getPlayer(ctx, "player");
						String perm = StringArgumentType.getString(ctx, "perm");
						boolean added = mod.getPerms().grant(pl.getUuid(), perm);
						ctx.getSource().sendFeedback(() -> Text.literal(
							added ? "[ME] granted " + perm : "[ME] already has " + perm), true);
						return added ? 1 : 0;
					}))))
			.then(CommandManager.literal("revoke")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
						var pl = EntityArgumentType.getPlayer(ctx, "player");
						String perm = StringArgumentType.getString(ctx, "perm");
						boolean ok = mod.getPerms().revoke(pl.getUuid(), perm);
						if (!ok) {
							ctx.getSource().sendError(Text.literal("player did not have " + perm));
							return 0;
						}
						return 1;
					}))))
			.then(CommandManager.literal("check")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
						boolean has = mod.getPerms().has(EntityArgumentType.getPlayer(ctx, "player"),
							StringArgumentType.getString(ctx, "perm"));
						ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(has)), false);
						return has ? 1 : 0;
					}))))
			.then(CommandManager.literal("list")
				.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
					var pl = EntityArgumentType.getPlayer(ctx, "player");
					var set = mod.getPerms().list(pl.getUuid());
					ctx.getSource().sendFeedback(() -> Text.literal(
						"admin=" + mod.getPerms().isAdmin(pl) + " perms=" + set), false);
					return set.size();
				})))
			.then(CommandManager.literal("admin")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("value", StringArgumentType.word()).executes(ctx -> {
						boolean on = StringArgumentType.getString(ctx, "value").equalsIgnoreCase("true");
						mod.getPerms().setAdmin(EntityArgumentType.getPlayer(ctx, "player").getUuid(), on);
						ctx.getSource().sendFeedback(() -> Text.literal("[ME] admin=" + on), true);
						return 1;
					}))))
			.then(CommandManager.literal("bind")
				.then(CommandManager.argument("commandId", StringArgumentType.word())
					.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
						String cid = StringArgumentType.getString(ctx, "commandId");
						String perm = StringArgumentType.getString(ctx, "perm");
						if (!mod.getPerms().bindCommand(cid, perm)) {
							ctx.getSource().sendError(Text.literal("command bind already exists: " + cid));
							return 0;
						}
						ctx.getSource().sendFeedback(() -> Text.literal("[ME] " + cid + " requires " + perm), true);
						return 1;
					}))))
			.then(CommandManager.literal("unbind")
				.then(CommandManager.argument("commandId", StringArgumentType.word()).executes(ctx -> {
					String cid = StringArgumentType.getString(ctx, "commandId");
					if (!mod.getPerms().unbindCommand(cid)) {
						ctx.getSource().sendError(Text.literal("no bind: " + cid));
						return 0;
					}
					return 1;
				})))
			.then(CommandManager.literal("binds").executes(ctx -> {
				mod.getPerms().listCommandBinds().forEach((k, v) ->
					ctx.getSource().sendFeedback(() -> Text.literal(k + " → " + v), false));
				return 1;
			})));
	}

	private static void registerWand(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("wand")
			.then(CommandManager.literal("list").executes(ctx -> {
				var binds = mod.getWands().list();
				if (binds.isEmpty()) {
					ctx.getSource().sendFeedback(() -> Text.literal("(no wand binds)"), false);
				} else {
					binds.forEach((tag, b) -> ctx.getSource().sendFeedback(() -> Text.literal(
						tag + " cd=" + b.cooldownTicks()
							+ (b.command() != null ? " cmd=" + b.command() : " handler")), false));
				}
				return binds.size();
			}))
			.then(CommandManager.literal("register")
				.then(CommandManager.argument("tag", StringArgumentType.word())
					.then(CommandManager.argument("cooldown", IntegerArgumentType.integer(0, 1200))
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							String tag = StringArgumentType.getString(ctx, "tag");
							int cd = IntegerArgumentType.getInteger(ctx, "cooldown");
							String cmd = StringArgumentType.getString(ctx, "command");
							mod.getWands().register(tag, cd, cmd);
							ctx.getSource().sendFeedback(() -> Text.literal("[ME] wand register " + tag), true);
							return 1;
						})))))
			.then(CommandManager.literal("unregister")
				.then(CommandManager.argument("tag", StringArgumentType.word()).executes(ctx -> {
					mod.getWands().unregister(StringArgumentType.getString(ctx, "tag"));
					return 1;
				})))
			.then(CommandManager.literal("unregister_all").executes(ctx -> {
				mod.getWands().unregisterAll();
				return 1;
			}))
			.then(CommandManager.literal("give")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("tag", StringArgumentType.word())
						.then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							mod.getWands().give(
								EntityArgumentType.getPlayer(ctx, "player"),
								StringArgumentType.getString(ctx, "tag"),
								StringArgumentType.getString(ctx, "name"));
							return 1;
						})))))
			.then(CommandManager.literal("give_custom")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("item", StringArgumentType.string())
						.then(CommandManager.argument("tag", StringArgumentType.word())
							.then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
								mod.getWands().giveCustom(
									EntityArgumentType.getPlayer(ctx, "player"),
									StringArgumentType.getString(ctx, "item"),
									StringArgumentType.getString(ctx, "tag"),
									StringArgumentType.getString(ctx, "name"), 1);
								return 1;
							}))))))
			.then(CommandManager.literal("has")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("tag", StringArgumentType.word()).executes(ctx -> {
						boolean has = mod.getWands().playerHasWand(
							EntityArgumentType.getPlayer(ctx, "player"),
							StringArgumentType.getString(ctx, "tag"));
						ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(has)), false);
						return has ? 1 : 0;
					}))))
			.then(CommandManager.literal("cooldown")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("tag", StringArgumentType.word()).executes(ctx -> {
						long r = mod.getWands().cooldownRemaining(
							EntityArgumentType.getPlayer(ctx, "player"),
							StringArgumentType.getString(ctx, "tag"));
						ctx.getSource().sendFeedback(() -> Text.literal("remaining=" + r), false);
						return (int) Math.min(r, Integer.MAX_VALUE);
					}))))
			.then(CommandManager.literal("enable").executes(ctx -> {
				mod.getWands().setEnabled(true);
				return 1;
			}))
			.then(CommandManager.literal("disable").executes(ctx -> {
				mod.getWands().setEnabled(false);
				return 1;
			})));
	}

	private static void registerHook(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("hook")
			.then(CommandManager.literal("bind")
				.then(CommandManager.argument("type", StringArgumentType.word())
					.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
						String type = StringArgumentType.getString(ctx, "type").toUpperCase();
						try {
							var h = io.runtoolkit.macroengine.systems.hook.HookSystems.Hook.valueOf(type);
							mod.getHooks().bindCommand(h, StringArgumentType.getString(ctx, "command"));
							ctx.getSource().sendFeedback(() -> Text.literal("[ME] hook " + type), true);
							return 1;
						} catch (Exception e) {
							ctx.getSource().sendError(Text.literal("types: SNEAK_START SNEAK_STOP SPRINT_START SPRINT_STOP ELYTRA_START ELYTRA_STOP FLY_START FLY_STOP"));
							return 0;
						}
					}))))
			.then(CommandManager.literal("unbind")
				.then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
					try {
						var h = io.runtoolkit.macroengine.systems.hook.HookSystems.Hook.valueOf(
							StringArgumentType.getString(ctx, "type").toUpperCase());
						mod.getHooks().unbind(h);
						return 1;
					} catch (Exception e) {
						return 0;
					}
				})))
			.then(CommandManager.literal("enable").executes(ctx -> {
				mod.getHooks().setEnabled(true);
				return 1;
			}))
			.then(CommandManager.literal("disable").executes(ctx -> {
				mod.getHooks().setEnabled(false);
				return 1;
			})));
	}



	private static void registerFreezeScoreboard(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("freeze")
			.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
				mod.getFreeze().freeze(EntityArgumentType.getPlayer(ctx, "player"));
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] frozen"), true);
				return 1;
			})));
		root.then(CommandManager.literal("unfreeze")
			.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
				mod.getFreeze().unfreeze(EntityArgumentType.getPlayer(ctx, "player"));
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] unfrozen"), true);
				return 1;
			})));
		root.then(CommandManager.literal("scoreboard")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("name", StringArgumentType.word())
					.then(CommandManager.argument("display", StringArgumentType.greedyString()).executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "name");
						mod.getScoreboard().ensureObjective(ctx.getSource().getServer(),
							name, StringArgumentType.getString(ctx, "display"));
						ctx.getSource().sendFeedback(() -> Text.literal("[ME] objective " + name), true);
						return 1;
					}))))
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("objective", StringArgumentType.word())
					.then(CommandManager.argument("holder", StringArgumentType.word())
						.then(CommandManager.argument("value", IntegerArgumentType.integer()).executes(ctx -> {
							String obj = StringArgumentType.getString(ctx, "objective");
							String holder = StringArgumentType.getString(ctx, "holder");
							int val = IntegerArgumentType.getInteger(ctx, "value");
							mod.getScoreboard().setScore(ctx.getSource().getServer(), obj, holder, val);
							ctx.getSource().sendFeedback(() -> Text.literal(
								"[ME] " + holder + " " + obj + "=" + val), true);
							return 1;
						})))))
			.then(CommandManager.literal("sidebar")
				.then(CommandManager.argument("objective", StringArgumentType.word()).executes(ctx -> {
					String obj = StringArgumentType.getString(ctx, "objective");
					mod.getScoreboard().setSidebar(ctx.getSource().getServer(), obj);
					ctx.getSource().sendFeedback(() -> Text.literal("[ME] sidebar=" + obj), true);
					return 1;
				}))));
		root.then(CommandManager.literal("cooldown")
			.then(CommandManager.literal("check")
				.then(CommandManager.argument("key", StringArgumentType.word()).executes(ctx -> {
					long r = mod.getCooldowns().remaining(StringArgumentType.getString(ctx, "key"));
					ctx.getSource().sendFeedback(() -> Text.literal("remaining=" + r), false);
					return (int) Math.min(r, Integer.MAX_VALUE);
				})))
			.then(CommandManager.literal("use")
				.then(CommandManager.argument("key", StringArgumentType.word())
					.then(CommandManager.argument("ticks", IntegerArgumentType.integer(0)).executes(ctx -> {
						boolean ok = mod.getCooldowns().tryUse(
							StringArgumentType.getString(ctx, "key"),
							IntegerArgumentType.getInteger(ctx, "ticks"));
						ctx.getSource().sendFeedback(() -> Text.literal(ok ? "ok" : "blocked"), false);
						return ok ? 1 : 0;
					})))));
	}


	private static net.minecraft.entity.Entity findLookedEntity(ServerPlayerEntity player, double range) {
		var start = player.getCameraPosVec(1.0f);
		var dir = player.getRotationVec(1.0f);
		var end = start.add(dir.x * range, dir.y * range, dir.z * range);
		var box = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
		var hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
			player, start, end, box,
			e -> !e.isSpectator() && e.canHit(),
			range * range);
		return hit == null ? null : hit.getEntity();
	}

	private static EventBus.Type parseType(String s) {
		try {
			return EventBus.Type.valueOf(s.toUpperCase());
		} catch (Exception e) {
			return null;
		}
	}

	private static String nullSafe(String s) {
		return s == null ? "(none)" : s;
	}

	private static int setEnabled(ServerCommandSource src, MacroEngineMod mod, String id, boolean on) {
		return mod.getTickEngine().find(id).map(c -> {
			c.enabled = on;
			src.sendFeedback(() -> Text.literal(id + " enabled=" + on), true);
			return 1;
		}).orElseGet(() -> {
			src.sendError(Text.literal("unknown: " + id));
			return 0;
		});
	}
}
