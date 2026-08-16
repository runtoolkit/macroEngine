package io.runtoolkit.macroengine.input;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.event.EventBus;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.CommandBlockExecutor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InputSystems {
	public static final String CBM_TAG = "macroengine_input";

	public record BookCapture(UUID player, String raw) {}
	public record CbmCapture(UUID entity, String command, Vec3d pos) {}
	public record DialogCapture(UUID player, String raw) {}

	private final List<Consumer<BookCapture>> bookListeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<CbmCapture>> cbmListeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<DialogCapture>> dialogListeners = new CopyOnWriteArrayList<>();

	private String lastBookRaw;
	private String lastCbmCommand;
	private String lastDialogRaw;

	public void onBook(Consumer<BookCapture> l) { bookListeners.add(l); }
	public void onCbm(Consumer<CbmCapture> l) { cbmListeners.add(l); }
	public void onDialog(Consumer<DialogCapture> l) { dialogListeners.add(l); }

	public String getLastBookRaw() { return lastBookRaw; }
	public String getLastCbmCommand() { return lastCbmCommand; }
	public String getLastDialogRaw() { return lastDialogRaw; }

	public CommandBlockMinecartEntity summonCbm(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		CommandBlockMinecartEntity cbm = new CommandBlockMinecartEntity(
			EntityType.COMMAND_BLOCK_MINECART, world);
		cbm.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);
		cbm.addCommandTag(CBM_TAG);
		cbm.setNoGravity(true);
		cbm.setCustomName(Text.literal("ME-Input"));
		cbm.setCustomNameVisible(true);
		world.spawnEntity(cbm);
		return cbm;
	}

	public void giveWritableBook(ServerPlayerEntity player) {
		player.giveItemStack(new ItemStack(Items.WRITABLE_BOOK));
	}

	public void tick(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (Entity entity : world.iterateEntities()) {
				if (!(entity instanceof CommandBlockMinecartEntity cbm)) continue;
				if (!entity.getCommandTags().contains(CBM_TAG)) continue;
				entity.setNoGravity(true);
				captureCbmIfNeeded(cbm);
			}
		}
	}

	private void captureCbmIfNeeded(CommandBlockMinecartEntity cbm) {
		CommandBlockExecutor exec;
		try {
			exec = cbm.getCommandExecutor();
		} catch (Throwable t) {
			return;
		}
		String command = exec.getCommand();
		if (command == null || command.isBlank()) return;

		lastCbmCommand = command;
		CbmCapture cap = new CbmCapture(cbm.getUuid(), command, cbm.getPos());
		MacroEngineMod.LOGGER.info("CBM capture: {}", truncate(command, 80));
		for (Consumer<CbmCapture> l : cbmListeners) {
			try { l.accept(cap); } catch (Exception e) {
				MacroEngineMod.LOGGER.error("cbm listener", e);
			}
		}
		MacroEngineMod.get().getEvents().fire(EventBus.Type.ON_CBM_INPUT, cap);
		exec.setCommand("");
	}

	public void submitDialog(ServerPlayerEntity player, String value) {
		if (value == null) value = "";
		lastDialogRaw = value;
		DialogCapture cap = new DialogCapture(player.getUuid(), value);
		for (Consumer<DialogCapture> l : dialogListeners) {
			try { l.accept(cap); } catch (Exception e) {
				MacroEngineMod.LOGGER.error("dialog listener", e);
			}
		}
		MacroEngineMod.get().getEvents().fire(EventBus.Type.ON_DIALOG, cap);
	}

	public void submitBook(ServerPlayerEntity player, String raw) {
		lastBookRaw = raw;
		BookCapture cap = new BookCapture(player.getUuid(), raw);
		for (Consumer<BookCapture> l : bookListeners) {
			try { l.accept(cap); } catch (Exception e) {
				MacroEngineMod.LOGGER.error("book listener", e);
			}
		}
	}

	private static String truncate(String s, int max) {
		if (s == null) return "";
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}
}
