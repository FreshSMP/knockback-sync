package me.caseload.knockbacksync.entity;

import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.ConfigWrapper;
import me.caseload.knockbacksync.event.KBSyncEventHandler;
import me.caseload.knockbacksync.event.events.ConfigReloadEvent;
import net.minecraft.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntityTickManager {
    private static final Map<EntityType<?>, Integer> customTickIntervals = new HashMap<>();

    static {
        updateTickIntervals(Base.INSTANCE.getConfigManager().getConfigWrapper());
    }

    @KBSyncEventHandler
    public static void updateTickIntervals(ConfigReloadEvent event) {
        ConfigWrapper configWrapper = event.getConfigManager().getConfigWrapper();
        updateTickIntervals(configWrapper);
    }

    private static void updateTickIntervals(ConfigWrapper configWrapper) {
        customTickIntervals.clear();
        for (String entityKey : configWrapper.getKeys("entity_tick_intervals")) {
            try {
                Optional<EntityType<?>> entityType = EntityType.get(entityKey.toLowerCase());
                if (entityType.isPresent()) {
                    int interval = configWrapper.getInt("entity_tick_intervals." + entityKey, entityType.get().getTrackTickInterval());
                    customTickIntervals.put(entityType.get(), interval);
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid entity type in config: " + entityKey);
            }
        }
    }

    public static int getCustomUpdateInterval(EntityType<?> entityType) {
        return customTickIntervals.getOrDefault(entityType, entityType.getTrackTickInterval());
    }
}
