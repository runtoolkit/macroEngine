package io.runtoolkit.macroengine.mixin;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class BookUpdateMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onBookUpdate", at = @At("TAIL"))
	private void macroengine$onBookUpdate(BookUpdateC2SPacket packet, CallbackInfo ci) {
		try {
			List<String> pages = packet.pages();
			Optional<String> title = packet.title();
			StringBuilder sb = new StringBuilder();
			title.ifPresent(t -> sb.append(t).append('\n'));
			for (int i = 0; i < pages.size(); i++) {
				if (i > 0) sb.append('\n');
				sb.append(pages.get(i));
			}
			String raw = sb.toString();
			if (MacroEngineMod.get() != null) {
				MacroEngineMod.get().getInput().submitBook(player, raw);
			}
		} catch (Throwable t) {
			// packet shape may differ slightly — ignore
		}
	}
}
