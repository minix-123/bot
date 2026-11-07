package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Stawia blok (stół warsztatu, piec) na ziemi obok bota i zapamiętuje jego pozycję. */
public final class PlaceTask implements Task {
    private final Item item;
    private final Consumer<BlockPos> onPlaced;
    private final String name;

    private BlockPos spot;
    private int tries;
    private int wait;
    private int equipTries;

    public PlaceTask(Item item, String name, Consumer<BlockPos> onPlaced) {
        this.item = item;
        this.name = name;
        this.onPlaced = onPlaced;
    }

    @Override
    public String label() {
        return "Stawiam: " + name;
    }

    @Override
    public boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.containerMenu != p.inventoryMenu) {
            p.closeContainer();
            return false;
        }

        if (spot != null) {                      // czekamy na potwierdzenie
            if (++wait < 4) return false;
            BlockState st = mc.level.getBlockState(spot);
            if (!st.isAir() && !st.canBeReplaced()) {
                onPlaced.accept(spot);
                return true;
            }
            spot = null;
            wait = 0;
            if (++tries > 5) {
                Bot.stop("Nie mogę postawić: " + name + " - zatrzymano.");
                return true;
            }
            return false;
        }

        if (Bot.count(s -> s.is(item)) == 0) {
            Bot.stop("Brak przedmiotu do postawienia: " + name + " - zatrzymano.");
            return true;
        }

        BlockPos cand = findSpot(mc);
        if (cand == null) {
            if (++tries > 60) {
                Bot.stop("Brak miejsca na " + name + " - zatrzymano.");
                return true;
            }
            p.setYRot(p.getYRot() + 45);        // obróć się i szukaj dalej
            return false;
        }

        if (!p.getMainHandItem().is(item)) {
            if (++equipTries > 20) {
                Bot.stop("Nie mogę wziąć do ręki: " + name + " - zatrzymano.");
                return true;
            }
            Bot.equip(mc, s -> s.is(item));
            return false;
        }

        Vec3 hit = new Vec3(cand.getX() + 0.5, cand.getY(), cand.getZ() + 0.5);
        Bot.look(p, hit);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, cand.below(), false));
        p.swing(InteractionHand.MAIN_HAND);
        spot = cand;
        wait = 0;
        return false;
    }

    private BlockPos findSpot(Minecraft mc) {
        BlockPos base = mc.player.blockPosition();
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] o : offs) {
            BlockPos c = base.offset(o[0], 0, o[1]);
            if (mc.level.getBlockState(c).isAir() && Bot.solid(mc, c.below()) && !Bot.solid(mc, c.above())) {
                return c;
            }
        }
        return null;
    }
}
