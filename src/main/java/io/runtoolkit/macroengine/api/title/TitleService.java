package io.runtoolkit.macroengine.api.title;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class TitleService {
	public void title(ServerPlayerEntity player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
		if (subtitle != null && !subtitle.isEmpty()) {
			player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle)));
		}
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title == null ? "" : title)));
	}

	public void clear(ServerPlayerEntity player) {
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
	}
}
