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
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

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
							ctx.getSource().sendError(Text.literal("unknown: " + id));
							return 0;
						});
					})))));
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
					.then(CommandManager.argument("uuid", UuidArgumentType.uuid())
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							UUID u = UuidArgumentType.getUuid(ctx, "uuid");
							mod.getInteractions().bindUse(
								StringArgumentType.getString(ctx, "id"), u,
								StringArgumentType.getString(ctx, "command"));
							ctx.getSource().sendFeedback(() -> Text.literal("[ME] bind_use " + u), true);
							return 1;
						})))))
			.then(CommandManager.literal("bind_attack")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("uuid", UuidArgumentType.uuid())
						.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
							UUID u = UuidArgumentType.getUuid(ctx, "uuid");
							mod.getInteractions().bindAttack(
								StringArgumentType.getString(ctx, "id"), u,
								StringArgumentType.getString(ctx, "command"));
							return 1;
						})))))
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
				mod.getItems().renameHeld(ctx.getSource().getPlayerOrThrow(),
					StringArgumentType.getString(ctx, "name"));
				return 1;
			})));
	}

	private static void registerBossbar(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("bossbar")
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("title", StringArgumentType.greedyString()).executes(ctx -> {
						mod.getBossbars().add(StringArgumentType.getString(ctx, "id"),
							StringArgumentType.getString(ctx, "title"),
							BossBar.Color.BLUE, BossBar.Style.PROGRESS);
						return 1;
					}))))
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("value", IntegerArgumentType.integer(0))
						.then(CommandManager.argument("max", IntegerArgumentType.integer(1)).executes(ctx -> {
							mod.getBossbars().setValue(StringArgumentType.getString(ctx, "id"),
								IntegerArgumentType.getInteger(ctx, "value"),
								IntegerArgumentType.getInteger(ctx, "max"));
							return 1;
						})))))
			.then(CommandManager.literal("players")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
						mod.getBossbars().setPlayers(StringArgumentType.getString(ctx, "id"),
							EntityArgumentType.getPlayer(ctx, "player"), true);
						return 1;
					}))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
					mod.getBossbars().remove(StringArgumentType.getString(ctx, "id"));
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
				return 1;
			}))
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
			.then(CommandManager.literal("submit")
				.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
					mod.getDialogs().submit(ctx.getSource().getPlayerOrThrow(),
						StringArgumentType.getString(ctx, "text"));
					return 1;
				}))));
	}

	private static void registerPerm(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("perm")
			.then(CommandManager.literal("grant")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
						mod.getPerms().grant(EntityArgumentType.getPlayer(ctx, "player").getUuid(),
							StringArgumentType.getString(ctx, "perm"));
						return 1;
					}))))
			.then(CommandManager.literal("revoke")
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
						mod.getPerms().revoke(EntityArgumentType.getPlayer(ctx, "player").getUuid(),
							StringArgumentType.getString(ctx, "perm"));
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
			.then(CommandManager.literal("admin")
				.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
					mod.getPerms().setAdmin(EntityArgumentType.getPlayer(ctx, "player").getUuid(), true);
					return 1;
				}))));
	}

	private static void registerWand(LiteralArgumentBuilder<ServerCommandSource> root, MacroEngineMod mod) {
		root.then(CommandManager.literal("wand")
			.then(CommandManager.literal("list").executes(ctx -> {
				mod.getWands().list().forEach((id, a) ->
					ctx.getSource().sendFeedback(() -> Text.literal(id + " cd=" + a.cooldownTicks()), false));
				return 1;
			}))
			.then(CommandManager.literal("register")
				.then(CommandManager.argument("id", StringArgumentType.word())
					.then(CommandManager.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
						mod.getWands().registerCommand(
							StringArgumentType.getString(ctx, "id"), 10,
							StringArgumentType.getString(ctx, "command"));
						return 1;
					})))));
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
						mod.getScoreboard().ensureObjective(ctx.getSource().getServer(),
							StringArgumentType.getString(ctx, "name"),
							StringArgumentType.getString(ctx, "display"));
						return 1;
					}))))
			.then(CommandManager.literal("set")
				.then(CommandManager.argument("objective", StringArgumentType.word())
					.then(CommandManager.argument("holder", StringArgumentType.word())
						.then(CommandManager.argument("value", IntegerArgumentType.integer()).executes(ctx -> {
							mod.getScoreboard().setScore(ctx.getSource().getServer(),
								StringArgumentType.getString(ctx, "objective"),
								StringArgumentType.getString(ctx, "holder"),
								IntegerArgumentType.getInteger(ctx, "value"));
							return 1;
						})))))
			.then(CommandManager.literal("sidebar")
				.then(CommandManager.argument("objective", StringArgumentType.word()).executes(ctx -> {
					mod.getScoreboard().setSidebar(ctx.getSource().getServer(),
						StringArgumentType.getString(ctx, "objective"));
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
