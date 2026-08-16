package io.runtoolkit.macroengine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.input.InputValidator;
import io.runtoolkit.macroengine.tick.TickChannel;
import io.runtoolkit.macroengine.tick.TickEngine;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class MacroCommands {
	private MacroCommands() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, MacroEngineMod mod) {
		dispatcher.register(CommandManager.literal("macroengine")
			.requires(s -> s.hasPermissionLevel(2))
			.then(CommandManager.literal("status").executes(ctx -> {
				ctx.getSource().sendFeedback(() -> Text.literal(mod.getTickEngine().statusText()), false);
				return 1;
			}))
			.then(CommandManager.literal("pause").executes(ctx -> {
				mod.getTickEngine().pause();
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] paused"), true);
				return 1;
			}))
			.then(CommandManager.literal("resume").executes(ctx -> {
				mod.getTickEngine().resume();
				mod.getConfig().tickPaused = false;
				ctx.getSource().sendFeedback(() -> Text.literal("[ME] resumed"), true);
				return 1;
			}))
			.then(CommandManager.literal("version").executes(ctx -> {
				ctx.getSource().sendFeedback(() -> Text.literal(
					"MacroEngine v" + MacroEngineMod.VERSION
						+ " loaded=" + mod.isLoaded()
						+ " fibers=" + mod.getFibers().size()
						+ " queue=" + mod.getTaskQueue().size()), false);
				return 1;
			}))
			.then(CommandManager.literal("channel")
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
						})))))
			.then(CommandManager.literal("dialog")
				.then(CommandManager.literal("open").executes(ctx -> {
					ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
					mod.getDialogs().open(p, (pl, text) ->
						pl.sendMessage(Text.literal("[ME] dialog → " + text), false));
					return 1;
				}))
				.then(CommandManager.literal("submit")
					.then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
						boolean ok = mod.getDialogs().submit(p, StringArgumentType.getString(ctx, "text"));
						ctx.getSource().sendFeedback(() -> Text.literal(ok ? "[ME] submitted" : "[ME] captured"), false);
						return 1;
					}))))
			.then(CommandManager.literal("input")
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
						})))))
			.then(CommandManager.literal("perm")
				.then(CommandManager.literal("grant")
					.then(CommandManager.argument("player", EntityArgumentType.player())
						.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
							ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
							String perm = StringArgumentType.getString(ctx, "perm");
							mod.getPerms().grant(t.getUuid(), perm);
							ctx.getSource().sendFeedback(() -> Text.literal("granted " + perm), true);
							return 1;
						}))))
				.then(CommandManager.literal("revoke")
					.then(CommandManager.argument("player", EntityArgumentType.player())
						.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
							ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
							mod.getPerms().revoke(t.getUuid(), StringArgumentType.getString(ctx, "perm"));
							ctx.getSource().sendFeedback(() -> Text.literal("revoked"), true);
							return 1;
						}))))
				.then(CommandManager.literal("check")
					.then(CommandManager.argument("player", EntityArgumentType.player())
						.then(CommandManager.argument("perm", StringArgumentType.word()).executes(ctx -> {
							ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
							boolean has = mod.getPerms().has(t, StringArgumentType.getString(ctx, "perm"));
							ctx.getSource().sendFeedback(() -> Text.literal(String.valueOf(has)), false);
							return has ? 1 : 0;
						}))))
				.then(CommandManager.literal("admin")
					.then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
						ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
						mod.getPerms().setAdmin(t.getUuid(), true);
						ctx.getSource().sendFeedback(() -> Text.literal("admin set"), true);
						return 1;
					}))))
			.then(CommandManager.literal("wand")
				.then(CommandManager.literal("list").executes(ctx -> {
					mod.getWands().list().forEach((id, a) ->
						ctx.getSource().sendFeedback(() -> Text.literal(id + " cd=" + a.cooldownTicks()), false));
					return 1;
				}))
				.then(CommandManager.literal("register")
					.then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
						String id = StringArgumentType.getString(ctx, "id");
						mod.getWands().register(id, 10, p ->
							p.sendMessage(Text.literal("[ME] wand:" + id), false));
						ctx.getSource().sendFeedback(() -> Text.literal("wand: " + id), true);
						return 1;
					}))))
		);
	}

	private static String nullSafe(String s) { return s == null ? "(none)" : s; }

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
