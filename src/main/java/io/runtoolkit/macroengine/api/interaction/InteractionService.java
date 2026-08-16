package io.runtoolkit.macroengine.api.interaction;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class InteractionService {
	private final List<BiConsumer<ServerPlayerEntity, Entity>> useEntity = new ArrayList<>();
	private final List<BiConsumer<ServerPlayerEntity, Entity>> attackEntity = new ArrayList<>();
	private final List<BiConsumer<ServerPlayerEntity, BlockHitResult>> useBlock = new ArrayList<>();

	public void onUseEntity(BiConsumer<ServerPlayerEntity, Entity> h) { useEntity.add(h); }
	public void onAttackEntity(BiConsumer<ServerPlayerEntity, Entity> h) { attackEntity.add(h); }
	public void onUseBlock(BiConsumer<ServerPlayerEntity, BlockHitResult> h) { useBlock.add(h); }

	public void fireUseEntity(ServerPlayerEntity p, Entity e) { for (var h : useEntity) h.accept(p, e); }
	public void fireAttackEntity(ServerPlayerEntity p, Entity e) { for (var h : attackEntity) h.accept(p, e); }
	public void fireUseBlock(ServerPlayerEntity p, BlockHitResult hit) { for (var h : useBlock) h.accept(p, hit); }
}
