package me.caseload.knockbacksync.event;

import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class Event {
    @Setter
    private static EventBus eventBus;
    @Setter
    private boolean cancelled = false;

    public void post() {
        if (eventBus != null) {
            eventBus.post(this);
        } else {
            throw new IllegalStateException("EventBus has not been set");
        }
    }
}
