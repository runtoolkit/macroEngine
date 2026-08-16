package io.runtoolkit.macroengine.systems.uuid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class UuidService {
	private final Map<String, UUID> named = new ConcurrentHashMap<>();
	private final AtomicInteger seq = new AtomicInteger();

	public UUID getOrCreate(String key) {
		return named.computeIfAbsent(key, k -> UUID.randomUUID());
	}

	public int nextSeq() { return seq.incrementAndGet(); }
}
