package io.runtoolkit.macroengine.input;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InputSystems {
	public static final String CBM_TAG = "macroengine_input";

	public record BookCapture(UUID player, String raw) {}
	public record CbmCapture(UUID entity, String command) {}
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

	public void tick(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (Entity entity : world.iterateEntities()) {
				if (!(entity instanceof CommandBlockMinecartEntity)) continue;
				if (!entity.getCommandTags().contains(CBM_TAG)) continue;
				entity.setNoGravity(true);
			}
		}
	}

	public void submitDialog(ServerPlayerEntity player, String value) {
		if (value == null) value = "";
		lastDialogRaw = value;
		DialogCapture cap = new DialogCapture(player.getUuid(), value);
		MacroEngineMod.LOGGER.info("dialog capture {}: {}", player.getName().getString(), value.length() > 80 ? value.substring(0, 80) + "…" : value);
		for (Consumer<DialogCapture> l : dialogListeners) {
			try { l.accept(cap); } catch (Exception e) { MacroEngineMod.LOGGER.error("dialog listener", e); }
		}
	}

	public void submitBook(ServerPlayerEntity player, String raw) {
		lastBookRaw = raw;
		BookCapture cap = new BookCapture(player.getUuid(), raw);
		for (Consumer<BookCapture> l : bookListeners) {
			try { l.accept(cap); } catch (Exception e) { MacroEngineMod.LOGGER.error("book listener", e); }
		}
	}

	public void submitCbm(UUID entityId, String command) {
		lastCbmCommand = command;
		CbmCapture cap = new CbmCapture(entityId, command);
		for (Consumer<CbmCapture> l : cbmListeners) {
			try { l.accept(cap); } catch (Exception e) { MacroEngineMod.LOGGER.error("cbm listener", e); }
		}
	}
}
