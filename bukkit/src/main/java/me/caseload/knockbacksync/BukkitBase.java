package me.caseload.knockbacksync;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import me.caseload.knockbacksync.event.KBSyncEventHandler;
import me.caseload.knockbacksync.event.events.ConfigReloadEvent;
import me.caseload.knockbacksync.listener.bukkit.BukkitPlayerDamageListener;
import me.caseload.knockbacksync.listener.bukkit.BukkitPlayerKnockbackListener;
import me.caseload.knockbacksync.manager.ConfigManager;
import me.caseload.knockbacksync.permission.PermissionChecker;
import me.caseload.knockbacksync.permission.PluginPermissionChecker;
import me.caseload.knockbacksync.scheduler.BukkitSchedulerAdapter;
import me.caseload.knockbacksync.scheduler.FoliaSchedulerAdapter;
import me.caseload.knockbacksync.sender.BukkitPlayerSelectorParser;
import me.caseload.knockbacksync.sender.BukkitSenderFactory;
import me.caseload.knockbacksync.sender.Sender;
import me.caseload.knockbacksync.stats.custom.BukkitStatsManager;
import me.caseload.knockbacksync.stats.custom.PluginJarHashProvider;
import me.caseload.knockbacksync.world.BukkitServer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.io.File;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

public class BukkitBase extends Base {

    private final JavaPlugin plugin;
    private final BukkitSenderFactory bukkitSenderFactory = new BukkitSenderFactory(this);
    private final PluginPermissionChecker permissionChecker = new PluginPermissionChecker();

    private int playerUpdateInterval;

    private final MethodHandle tickRateMethodHandle;

    public BukkitBase(JavaPlugin plugin) {
        this.plugin = plugin;
        super.configManager = new ConfigManager();
        super.playerSelectorParser = new BukkitPlayerSelectorParser<>();
        super.statsManager = new BukkitStatsManager(plugin);
        super.platformServer = new BukkitServer();
        super.pluginJarHashProvider = new PluginJarHashProvider(this.getClass().getProtectionDomain().getCodeSource().getLocation());
        this.playerUpdateInterval = this.getConfigManager().getConfigWrapper().getInt("entity_tick_intervals.player", 2);

        MethodHandle handle = null;
        try {
            Server craftServer = Bukkit.getServer();
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // Get handle for getServerTickManager
            MethodHandle getServerTickManager = lookup.findVirtual(craftServer.getClass(),
                    "getServerTickManager", MethodType.methodType(Object.class));

            Object serverTickManager = getServerTickManager.invoke(craftServer);

            // Get handle for getTickRate
            handle = lookup.findVirtual(serverTickManager.getClass(),
                    "getTickRate", MethodType.methodType(float.class));

        } catch (Throwable t) {
            // If anything fails, handle will remain null
        }
        this.tickRateMethodHandle = handle;
    }

    @Override
    public Logger getLogger() {
        return this.plugin.getLogger();
    }

    @Override
    public File getDataFolder() {
        return this.plugin.getDataFolder();
    }

    @Override
    public InputStream getResource(String filename) {
        return this.plugin.getResource(filename);
    }

    @Override
    public void load() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this.plugin));
        PacketEvents.getAPI().load();
    }

    @Override
    public void enable() {
        super.enable();
        super.eventBus.registerListeners(this);
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_5) && this.getPlatform() == Platform.BUKKIT) {
            scheduler.runTaskTimerAsynchronously(this::setUpdateIntervals, 1, 1);
        }
    }

    @Override
    public void initializeScheduler() {
        switch (getPlatform()) {
            case BUKKIT:
                super.scheduler = new BukkitSchedulerAdapter(this.plugin);
                break;
            case FOLIA:
                super.scheduler = new FoliaSchedulerAdapter(this.plugin);
                break;
        }
    }

    @Override
    protected void registerPlatformListeners() {
        registerPluginListeners(
                new BukkitPlayerDamageListener(),
                new BukkitPlayerKnockbackListener()
        );
    }

    @Override
    protected void registerCommands() {
        super.commandManager = new LegacyPaperCommandManager<>(
                this.plugin,
                ExecutionCoordinator.simpleCoordinator(),
                bukkitSenderFactory
        );
        if (commandManager instanceof LegacyPaperCommandManager) {
            LegacyPaperCommandManager<Sender> legacyPaperCommandManager = (LegacyPaperCommandManager<Sender>) commandManager;
            if (legacyPaperCommandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
                legacyPaperCommandManager.registerBrigadier();
            } else if (commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                legacyPaperCommandManager.registerAsynchronousCompletions();
            }
        }
        super.registerCommands();
    }

    @Override
    protected String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public void saveDefaultConfig() {
        this.plugin.saveDefaultConfig();
    }

    @Override
    public PermissionChecker getPermissionChecker() {
        return permissionChecker;
    }

    @Override
    public float getTickRate() {
        if (tickRateMethodHandle != null) {
            try {
                return (float) tickRateMethodHandle.invoke();
            } catch (Throwable t) {
                return 20.0f;
            }
        }
        return 20.0f;
    }

    public URL getJarURL() {
        try {
            String path = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            File pluginJarFile = new File(path);

            return Paths.get(getDataFolder().getParentFile().getAbsolutePath(), pluginJarFile.getName()).toUri().toURL();
        } catch (Exception e) {
            LOGGER.severe("Couldn't find plugin file: " + e.getMessage());
            return null;
        }
    }

    private void registerPluginListeners(Listener... listeners) {
        PluginManager pluginManager = this.plugin.getServer().getPluginManager();
        for (Listener listener : listeners)
            pluginManager.registerEvents(listener, this.plugin);
    }

    public BukkitSenderFactory getSenderFactory() {
        return this.bukkitSenderFactory;
    }

    public void setUpdateIntervals() {
        try {
            for (World world : Bukkit.getWorlds()) {
                Method getWorldHandleMethod = world.getClass().getMethod("getHandle");
                Object serverLevel = getWorldHandleMethod.invoke(world);

                // Get ChunkMap
                Method getChunkSource = serverLevel.getClass().getMethod("getChunkSource");
                Object chunkSource = getChunkSource.invoke(serverLevel);
                Field chunkMapField = chunkSource.getClass().getDeclaredField("chunkMap");
                chunkMapField.setAccessible(true);
                Object chunkMap = chunkMapField.get(chunkSource);

                // Get entityMap from ChunkMap
                Field entityMapField = chunkMap.getClass().getDeclaredField("entityMap");
                entityMapField.setAccessible(true);
                Map<Integer, ?> entityMap = (Map<Integer, ?>) entityMapField.get(chunkMap);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Object trackedEntity = entityMap.get(player.getEntityId());
                    if (trackedEntity == null)
                        continue;

                    Field serverEntityField = trackedEntity.getClass().getDeclaredField("serverEntity");
                    serverEntityField.setAccessible(true);
                    Object serverEntity = serverEntityField.get(trackedEntity);

                    Field updateIntervalField = serverEntity.getClass().getDeclaredField("updateInterval");
                    updateIntervalField.setAccessible(true);
                    updateIntervalField.set(serverEntity, playerUpdateInterval);
                }
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException("Unable to use reflection to modify updateIntervals" + e);
        }
    }

    @KBSyncEventHandler
    public void onConfigReload(ConfigReloadEvent event) {
        playerUpdateInterval = event.getConfigManager().getConfigWrapper().getInt("entity_tick_intervals.player", 2);
    }

    public void restartServer() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
    }

    public boolean hasRestartScript() {
        File spigotFile = new File("spigot.yml");
        if (!spigotFile.exists())
            return false;

        FileConfiguration spigotConfig = YamlConfiguration.loadConfiguration(spigotFile);
        String scriptValue = spigotConfig.getString("settings.restart-script");
        if (scriptValue == null || scriptValue.isEmpty())
            return false;

        return Paths.get(scriptValue).toAbsolutePath().normalize().toFile().exists();
    }
}
