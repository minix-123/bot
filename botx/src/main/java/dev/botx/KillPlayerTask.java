package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/** Biegnie do gracza o podanym nicku i go bije aż zginie. */
public final class KillPlayerTask implements Task {
    private final String nick;
    private boolean seen;
    private int missing;

    public KillPlayerTask(String nick) {
        this.nick = nick;
    }

    @Override
    public String label() {
        return "Zabijam gracza " + nick;
    }

    @Override
    public boolean tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        Player t = null;
        for (Player o : mc.level.players()) {
            if (o != me && o.getScoreboardName().equalsIgnoreCase(nick)) {
                t = o;
                break;
            }
        }
        if (t == null) {
            if (seen) {
                Bot.msg("§e[Bot X] Gracz " + nick + " zniknął (zginął lub wyszedł poza zasięg).");
                return true;
            }
            if (++missing > 600) {
                Bot.stop("Nie widzę gracza " + nick + " (musi być w zasięgu renderowania) - zatrzymano.");
                return true;
            }
            return false;
        }
        seen = true;
        if (!t.isAlive() || t.isDeadOrDying()) {
            Bot.msg("§a[Bot X] Gracz " + nick + " zabity.");
            return true;
        }
        if (me.distanceTo(t) > 2.6f) {
            Bot.moveToward(mc, t.position(), false);
        }
        Combat.attack(mc, t);
        return false;
    }
}
