package dev.quantumfusion.dashloader.api.hook;

import dev.quantumfusion.dashloader.data.MappingData;
import dev.quantumfusion.dashloader.registry.ChunkHolder;
import dev.quantumfusion.dashloader.registry.RegistryFactory;
import dev.quantumfusion.dashloader.registry.RegistryWriter;
import dev.quantumfusion.taski.builtin.StepTask;

import java.util.List;

public interface SaveCacheHook {
	default void saveCacheStart() {
	}

	default void saveCacheTask(StepTask task) {
	}

	default void saveCacheRegistryInit(RegistryFactory handler) {
	}

	default void saveCacheRegistryWriterInit(RegistryWriter writer) {
	}

	default void saveCacheMappingStart(RegistryWriter writer, MappingData data) {
	}

	default void saveCacheMappingEnd(RegistryWriter writer, MappingData data) {
	}

	default void saveCachePopulateHolders(RegistryWriter writer, MappingData data, List<ChunkHolder> holders) {
	}

	default void saveCacheSerialize(RegistryWriter writer, MappingData data, List<ChunkHolder> holders) {
	}

	default void saveCacheEnd() {
	}
}
