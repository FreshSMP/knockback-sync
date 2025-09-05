package me.caseload.knockbacksync.runnable;

import com.github.retrooper.packetevents.protocol.player.User;
import me.caseload.knockbacksync.Base;
import me.caseload.knockbacksync.manager.CombatManager;
import me.caseload.knockbacksync.manager.PlayerDataManager;
import me.caseload.knockbacksync.player.PlayerData;

public class PingRunnable implements Runnable {

    @Override
    public void run() {
        if (!Base.INSTANCE.getConfigManager().isToggled())
            return;

        for (User user : CombatManager.getPlayers()) {
            PlayerData playerData = PlayerDataManager.getPlayerData(user);
            if (playerData != null)
                playerData.sendPing(true);
        }
    }
}
