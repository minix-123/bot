package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Goni gracza o podanym nicku (A* gdy jest za przeszkodą) i bije go aż zginie. */
public final class KillPlayerTask implements Task {
    private final String nick;
    private boolean seen;
    private int missing;
    private int chase;

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
            Nav.reset();
            return true;
        }

        float dist = me.distanceTo(t);
        if (dist > 2.6f) {
            boolean los = mc.level.clip(new ClipContext(me.getEyePosition(), t.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me)).getType() == HitResult.Type.MISS;
            if (los && dist < 14 && Math.abs(t.getY() - me.getY()) < 2.0) {
                Nav.reset();
                Bot.moveToward(mc, t.position(), false);      // prosty pościg gdy nic nie przeszkadza
            } else {
                Nav.goTo(Nav.touch(t.position(), 2.0, "kill"));
                if (++chase % 12 == 0) Nav.invalidate();      // cel się rusza - odśwież trasę
                Nav.tick(mc);
                if (Nav.failed) {
                    Nav.reset();
                    Bot.moveToward(mc, t.position(), true);
                }
            }
        }
        Combat.attack(mc, t);
        Bot.activity();
        return false;
    }
}
