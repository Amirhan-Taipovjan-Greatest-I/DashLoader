package dev.quantumfusion.dashloader;

import dev.quantumfusion.dashloader.api.APIHandler;
import dev.quantumfusion.dashloader.api.hook.LoadCacheHook;
import dev.quantumfusion.dashloader.api.hook.SaveCacheHook;
import dev.quantumfusion.dashloader.client.DashToast;
import dev.quantumfusion.dashloader.config.ConfigHandler;
import dev.quantumfusion.dashloader.data.DashIdentifier;
import dev.quantumfusion.dashloader.data.DashIdentifierInterface;
import dev.quantumfusion.dashloader.data.DashModelIdentifier;
import dev.quantumfusion.dashloader.data.MappingData;
import dev.quantumfusion.dashloader.data.blockstate.DashBlockState;
import dev.quantumfusion.dashloader.data.font.DashFont;
import dev.quantumfusion.dashloader.data.image.DashImage;
import dev.quantumfusion.dashloader.data.image.DashSprite;
import dev.quantumfusion.dashloader.data.model.DashModel;
import dev.quantumfusion.dashloader.data.model.components.DashBakedQuad;
import dev.quantumfusion.dashloader.data.model.predicates.DashPredicate;
import dev.quantumfusion.dashloader.data.registry.*;
import dev.quantumfusion.dashloader.fallback.model.DashMissingDashModel;
import dev.quantumfusion.dashloader.io.IOHandler;
import dev.quantumfusion.dashloader.registry.ChunkHolder;
import dev.quantumfusion.dashloader.registry.RegistryHandler;
import dev.quantumfusion.dashloader.registry.RegistryReader;
import dev.quantumfusion.dashloader.registry.RegistryWriter;
import dev.quantumfusion.dashloader.registry.factory.DashFactory;
import dev.quantumfusion.dashloader.thread.ThreadHandler;
import dev.quantumfusion.dashloader.util.TimeUtil;
import dev.quantumfusion.taski.builtin.StepTask;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


public class DashLoader {
	private static final String VERSION = FabricLoader.getInstance()
			.getModContainer("dashloader")
			.orElseThrow(() -> new IllegalStateException("DashLoader not found... apparently! WTF?"))
			.getMetadata()
			.getVersion()
			.getFriendlyString();
	public static final Logger LOG = LogManager.getLogger("DashLoader");
	public static final DashLoader DL = new DashLoader();

	private boolean shouldReload = true;
	private Status status = Status.NONE;
	private final DashMetadata metadata = new DashMetadata();
	private DashDataManager dataManager;

	// Handlers
	public final APIHandler api;
	public final RegistryHandler registry;
	public final ThreadHandler thread;
	public final ProgressHandler progress;
	public final ConfigHandler config;
	public final IOHandler io;
	public final ProfilerHandler profilerHandler = new ProfilerHandler();

	// Initializes the static singleton
	@SuppressWarnings("EmptyMethod")
	public static void bootstrap() {
	}

	private DashLoader() {
		LOG.info("Bootstrapping DashLoader " + VERSION + ".");

		this.api = new APIHandler();
		this.metadata.setModHash(FabricLoader.getInstance());
		this.io = new IOHandler(Path.of("./dashloader-cache/"));
		this.config = new ConfigHandler(FabricLoader.getInstance().getConfigDir().normalize().resolve("dashloader.json"));
		this.thread = new ThreadHandler();
		this.progress = new ProgressHandler();
		this.registry = new RegistryHandler();
	}

	public void initialize() {
		LOG.info("Initializing DashLoader " + VERSION + ".");
		try {
			this.api.initAPI();

			List<DashObjectClass<?, ?>> dashObjects = this.api.getDashObjects();
			this.config.reloadConfig();

			final FabricLoader instance = FabricLoader.getInstance();
			if (instance.isDevelopmentEnvironment()) {
				LOG.warn("DashLoader launched in dev.");
			}

			this.io.setCacheArea(this.metadata.modInfo);
			this.io.setSubCacheArea("bootstrap");
			this.io.addSerializer(RegistryData.class, dashObjects, DashBlockState.class, DashFont.class, DashSprite.class, DashPredicate.class);
			this.io.addSerializer(ImageData.class, dashObjects, DashImage.class);
			this.io.addSerializer(ModelData.class, dashObjects, DashModel.class);
			this.io.addSerializer(IdentifierData.class, dashObjects, DashIdentifierInterface.class);
			this.io.addSerializer(BakedQuadData.class, dashObjects, DashBakedQuad.class);
			this.io.addSerializer(MappingData.class, dashObjects);

			LOG.info("Created DashLoader with {}.", Thread.currentThread().getContextClassLoader().getClass().getSimpleName());
			LOG.info("Initialized DashLoader");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("mc exception bad");
		}
	}

	public DashDataManager getData() {
		final DashDataManager dataManager = this.dataManager;
		if (this.dataManager == null) {
			throw new NullPointerException("No dataManager active");
		}
		return dataManager;
	}

	public void reload(List<String> resourcePacks) {
		if (this.shouldReload) {
			this.metadata.setResourcePackHash(resourcePacks);
			this.io.setSubCacheArea(this.metadata.resourcePacks);
			LOG.info("Reloading DashLoader. [mod-hash: {}] [resource-hash: {}]", this.metadata.modInfo, this.metadata.resourcePacks);
			if (this.io.cacheExists()) {
				this.setStatus(Status.READ);
				this.loadDashCache();
			} else {
				this.setStatus(Status.WRITE);
			}

			LOG.info("Reloaded DashLoader");
			this.shouldReload = false;
		}
	}

	public void requestReload() {
		this.shouldReload = true;
	}

	public void resetDashLoader() {
		this.setStatus(Status.NONE);
	}

	public void saveDashCache() {
		this.api.callHook(SaveCacheHook.class, SaveCacheHook::saveCacheStart);
		DashToast.STATUS = DashToast.Status.CACHING;
		LOG.info("Starting DashLoader Caching");
		try {
			long start = System.currentTimeMillis();

			StepTask main = new StepTask("Creating DashCache", 3);
			ProgressHandler.TASK = main;

			this.api.callHook(SaveCacheHook.class, hook -> hook.saveCacheTask(main));
			this.progress.setCurrentTask("initializing");

			// missing model callback
			Map<Class<?>, DashFactory.FailCallback<?, ?>> callbacks = new HashMap<>();
			callbacks.put(DashModel.class, (DashFactory.FailCallback<BakedModel, DashModel>) (rraw, registry) -> {
				final DashDataManager.DashWriteContextData writeContextData = this.getData().getWriteContextData();
				if (writeContextData.missingModelsWrite.containsKey(rraw)) {
					return writeContextData.missingModelsWrite.get(rraw);
				}
				final DashMissingDashModel value = new DashMissingDashModel();
				writeContextData.missingModelsWrite.put(rraw, value);
				return value;
			});

			callbacks.put(DashIdentifierInterface.class, (DashFactory.FailCallback<Identifier, DashIdentifierInterface>) (rraw, registry) -> {
				if (rraw instanceof ModelIdentifier m) {
					return new DashModelIdentifier(m);
				} else {
					return new DashIdentifier(rraw);
				}
			});

			this.api.callHook(SaveCacheHook.class, hook -> hook.saveCacheRegistryInit(this.registry));
			RegistryWriter writer = this.registry.createWriter(callbacks, this.api.getDashObjects());
			this.api.callHook(SaveCacheHook.class, hook -> hook.saveCacheRegistryWriterInit(writer));

			// Reading minecraft assets
			StepTask readTask = new StepTask("Reading", 10);
			main.setSubTask(readTask);

			MappingData mappings = new MappingData();
			this.api.callHook(SaveCacheHook.class, hook -> hook.saveCacheMappingStart(writer, mappings));
			mappings.map(writer, readTask);
			this.api.callHook(SaveCacheHook.class, hook -> hook.saveCacheMappingEnd(writer, mappings));

			List<ChunkHolder> holders = new ArrayList<>();
			this.progress.setCurrentTask("export.image");
			readTask.run(() -> holders.add(new ImageData(writer)));
			this.progress.setCurrentTask("export.model");
			readTask.run(() -> holders.add(new ModelData(writer)));
			this.progress.setCurrentTask("export.registry");
			readTask.run(() -> holders.add(new RegistryData(writer)));
			this.progress.setCurrentTask("export.identifier");
			readTask.run(() -> holders.add(new IdentifierData(writer)));
			this.progress.setCurrentTask("export.quad");
			readTask.run(() -> holders.add(new BakedQuadData(writer)));


			this.api.callHook(SaveCacheHook.class, hook -> hook.saveCachePopulateHolders(writer, mappings, holders));
			main.next();

			this.progress.setCurrentTask("Serializing");

			// serialization
			main.run(new StepTask("Serializing", holders.size() + 2), (task) -> {
				holders.forEach(holder -> task.run(() -> this.io.save(holder, task::setSubTask)));
				task.run(() -> this.io.save(mappings, task::setSubTask));
				this.api.callHook(SaveCacheHook.class, hook -> hook.saveCacheSerialize(writer, mappings, holders));
			});

			String text = "Created cache in " + TimeUtil.getTimeStringFromStart(start);
			this.progress.setCurrentTask(text);
			LOG.info(text);
			DashToast.STATUS = DashToast.Status.DONE;
			this.api.callHook(SaveCacheHook.class, SaveCacheHook::saveCacheEnd);
		} catch (Throwable thr) {
			this.setStatus(Status.WRITE);
			LOG.error("Failed caching", thr);
			DashToast.STATUS = DashToast.Status.CRASHED;
			this.io.clearCache();
		}
	}

	private void loadDashCache() {
		this.api.callHook(LoadCacheHook.class, LoadCacheHook::loadCacheStart);


		var start = System.currentTimeMillis();
		this.io.setSubCacheArea(this.metadata.resourcePacks);
		LOG.info("Starting DashLoader Deserialization");
		try {
			StepTask task = new StepTask("Loading DashCache", 3);
			this.api.callHook(LoadCacheHook.class, (hook) -> hook.loadCacheTask(task));
			ProgressHandler.TASK = task;

			AtomicReference<MappingData> mappingsReference = new AtomicReference<>();
			ChunkHolder[] registryDataObjects = new ChunkHolder[5];

			var tempStart = System.currentTimeMillis();
			// Deserialize / Decompress all registries and mappings.
			this.api.callHook(LoadCacheHook.class, LoadCacheHook::loadCacheDeserialization);
			this.thread.parallelRunnable(
					() -> registryDataObjects[0] = (this.io.load(RegistryData.class)),
					() -> registryDataObjects[1] = (this.io.load(ImageData.class)),
					() -> registryDataObjects[2] = (this.io.load(ModelData.class)),
					() -> registryDataObjects[3] = (this.io.load(IdentifierData.class)),
					() -> registryDataObjects[4] = (this.io.load(BakedQuadData.class)),
					() -> mappingsReference.set(this.io.load(MappingData.class))
			);
			this.profilerHandler.export_file_reading_time = System.currentTimeMillis() - tempStart;

			MappingData mappings = mappingsReference.get();
			assert mappings != null;

			// Initialize systems
			LOG.info("Creating Registry");
			final RegistryReader reader = this.registry.createReader(registryDataObjects);
			this.api.callHook(LoadCacheHook.class, (hook) -> hook.loadCacheRegistryInit(reader, this.dataManager, mappings));

			tempStart = System.currentTimeMillis();
			LOG.info("Exporting Mappings");
			task.run(() -> {
				reader.export(task::setSubTask);
				this.api.callHook(LoadCacheHook.class, (hook) -> hook.loadCacheExported(reader, this.dataManager, mappings));
			});

			this.profilerHandler.export_asset_exporting_time = System.currentTimeMillis() - tempStart;


			tempStart = System.currentTimeMillis();
			LOG.info("Loading Mappings");
			task.run(() -> {
				mappings.export(reader, this.dataManager, task::setSubTask);
				this.api.callHook(LoadCacheHook.class, (hook) -> hook.loadCacheMapped(reader, this.dataManager, mappings));
			});
			this.profilerHandler.export_asset_loading_time = System.currentTimeMillis() - tempStart;


			this.profilerHandler.export_time = System.currentTimeMillis() - start;
			LOG.info("Loaded DashLoader in {}ms", this.profilerHandler.export_time);
			this.api.callHook(LoadCacheHook.class, LoadCacheHook::loadCacheEnd);
		} catch (Exception e) {
			LOG.error("Summoned CrashLoader in {}", TimeUtil.getTimeStringFromStart(start), e);
			this.setStatus(Status.WRITE);
			this.io.clearCache();
		}
	}

	private void setStatus(Status status) {
		LOG.info("\u001B[46m\u001B[30m DashLoader Status change {}\n\u001B[0m", status);
		this.status = status;
		switch (status) {
			case NONE -> this.dataManager = null;
			case READ -> this.dataManager = new DashDataManager(new DashDataManager.DashReadContextData());
			case WRITE -> this.dataManager = new DashDataManager(new DashDataManager.DashWriteContextData());
		}
	}

	public boolean active() {
		return this.status != Status.NONE;
	}

	public boolean isWrite() {
		return this.status == Status.WRITE;
	}

	public boolean isRead() {
		return this.status == Status.READ;
	}

	public Status getStatus() {
		return this.status;
	}


	public enum Status {
		NONE,
		READ,
		WRITE,
	}

	public static class DashMetadata {
		public String modInfo;
		public String resourcePacks;

		public void setModHash(FabricLoader loader) {
			ArrayList<ModMetadata> versions = new ArrayList<>();
			for (ModContainer mod : loader.getAllMods()) {
				ModMetadata metadata = mod.getMetadata();
				versions.add(metadata);
			}

			versions.sort(Comparator.comparing(ModMetadata::getId));

			StringBuilder stringBuilder = new StringBuilder();
			for (int i = 0; i < versions.size(); i++) {
				ModMetadata metadata = versions.get(i);
				stringBuilder.append(i).append("$").append(metadata.getId()).append('&').append(metadata.getVersion().getFriendlyString());
			}

			this.modInfo = DigestUtils.md5Hex(stringBuilder.toString()).toUpperCase();
		}

		public void setResourcePackHash(List<String> resourcePacks) {
			StringBuilder stringBuilder = new StringBuilder();
			for (int i = 0; i < resourcePacks.size(); i++) {
				String resourcePack = resourcePacks.get(i);
				stringBuilder.append(i).append("$").append(resourcePack);
			}

			this.resourcePacks = DigestUtils.md5Hex(stringBuilder.toString()).toUpperCase();
		}
	}

}
