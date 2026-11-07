package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Zbiera przedmiot kopiąc pasujące bloki, aż w ekwipunku będzie co najmniej `target` sztuk. */
public final class CollectTask implements Task {
    private final String name;
    private final Predicate<BlockState> block;
    private final Predicate<ItemStack> item;
    private final int target;
    private final int prefDepth;

    private final Set<BlockPos> bad = new HashSet<>();
    private BlockPos pos;
    private int age;
    private int scanTimer;
    private int pickupTimer;

    public CollectTask(String name, Predicate<BlockState> block, Predicate<ItemStack> item, int target, int prefDepth) {
        this.name = name;
        this.block = block;
        this.item = item;
        this.target = target;
        this.prefDepth = prefDepth;
    }

    @Override
    public String label() {
        return "Zbieram: " + name + " (" + Bot.count(item) + "/" + target + ")";
    }

    @Override
    public boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (Bot.count(item) >= target) return true;

        // zbieranie wyrzuconych przedmiotów po zniszczeniu bloku
        if (pickupTimer > 0) {
            pickupTimer--;
            ItemEntity ie = nearestItem(mc);
            if (ie != null) {
                Bot.moveToward(mc, ie.position(), false);
                return false;
            }
            pickupTimer = 0;
        }

        if (pos != null) {
            BlockState st = mc.level.getBlockState(pos);
            if (st.isAir() || !block.test(st)) {
                pos = null;
                pickupTimer = 30;
                return false;
            }
            if (++age > 400) {          // za długo - zapomnij o tym bloku
                bad.add(pos);
                pos = null;
                return false;
            }
        }

        if (pos == null) {
            if (--scanTimer <= 0) {
                scanTimer = 8;
                pos = Bot.scan(mc, block, 14, 14, bad);
                age = 0;
            }
            if (pos == null) {
                Bot.explore(mc, prefDepth);
                return false;
            }
        }

        Vec3 c = Vec3.atCenterOf(pos);
        double dist = p.getEyePosition().distanceTo(c);
        double hd = Math.hypot(c.x - p.getX(), c.z - p.getZ());

        if (dist <= 4.0) {
            if (!Bot.mine(mc, pos)) age += 5;
            return false;
        }
        if (hd < 1.6 && c.y < p.getY() - 2) {      // cel głęboko pod nami - kop w dół
            Bot.digDown(mc);
            return false;
        }
        if (hd < 1.6 && c.y > p.getY() + 3) {      // cel wysoko nad nami - pomiń
            bad.add(pos);
            pos = null;
            return false;
        }
        Bot.moveToward(mc, c, true);
        return false;
    }

    private static ItemEntity nearestItem(Minecraft mc) {
        LocalPlayer p = mc.player;
        List<ItemEntity> list = mc.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6.0));
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : list) {
            double d = p.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
