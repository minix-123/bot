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
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Zbiera przedmiot kopiąc pasujące bloki, aż w ekwipunku będzie co najmniej `target` sztuk.
 * Używa Nav (A*) do dojścia, sam szuka kolejnych bloków, zbiera drop, naprawia kilof gdy się zepsuje
 * i poddaje się (z komunikatem) zamiast kręcić się w kółko.
 */
public final class CollectTask implements Task {
    private static final Random RNG = new Random();

    private final String name;
    private final Predicate<BlockState> block;
    private final Predicate<ItemStack> item;
    private final int target;
    private final int prefDepth;
    private final int minTier;

    private final Set<BlockPos> bad = new HashSet<>();
    private BlockPos pos;
    private int age;
    private int scanTimer;
    private int pickupTimer;
    private int mineFails;
    private int lastCount = -1;
    private int noGain;
    private int skipDepth;

    private boolean legActive;
    private int legX, legZ;
    private int lastDx = 1, lastDz;

    public CollectTask(String name, Predicate<BlockState> block, Predicate<ItemStack> item,
                       int target, int prefDepth, int minTier) {
        this.name = name;
        this.block = block;
        this.item = item;
        this.target = target;
        this.prefDepth = prefDepth;
        this.minTier = minTier;
    }

    @Override
    public String label() {
        return "Zbieram: " + name + " (" + Bot.count(item) + "/" + target + ")";
    }

    @Override
    public void onStuck() {
        if (pos != null) bad.add(pos);
        pos = null;
        legActive = false;
        pickupTimer = 0;
    }

    @Override
    public boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        int have = Bot.count(item);
        if (have >= target) {
            Nav.reset();
            return true;
        }
        if (have != lastCount) {
            lastCount = have;
            noGain = 0;
        } else if (++noGain > 6000) {
            Bot.stop("Nie mogę zdobyć: " + name + " (5 minut bez postępu) - zatrzymano.");
            return true;
        }

        // kilof się zepsuł albo jeszcze go nie ma - zrób nowy i wróć do zbierania
        if (minTier > 0 && Bot.pickTier() < minTier) {
            Nav.reset();
            Bot.pushFront(List.of(Planner.pick(minTier), this));
            return true;
        }

        // 1) zbieranie wyrzuconych przedmiotów
        if (pickupTimer > 0) {
            pickupTimer--;
            ItemEntity ie = nearestItem(mc);
            if (ie != null) {
                Nav.goTo(Nav.touch(ie.position(), 1.0, "pickup"));
                Nav.tick(mc);
                if (Nav.failed) {
                    Nav.reset();
                    pickupTimer = 0;
                }
                return false;
            }
            pickupTimer = 0;
            Nav.reset();
        }

        // 2) aktualny cel
        if (pos != null) {
            BlockState st = mc.level.getBlockState(pos);
            if (st.isAir() || !block.test(st)) {
                pos = null;
                pickupTimer = 40;
                Nav.reset();
                return false;
            }
            if (++age > 700) {
                bad.add(pos);
                pos = null;
                Nav.reset();
                return false;
            }
        }

        // 3) szukanie celu
        if (pos == null) {
            if (--scanTimer <= 0) {
                scanTimer = 20;
                pos = Bot.scan(mc, block, 24, 20, bad);
                age = 0;
                mineFails = 0;
                if (pos != null) {
                    Nav.reset();
                    legActive = false;
                }
            }
            if (pos == null) {
                explore(mc);
                return false;
            }
        }

        // 4) kopanie / dojście
        Vec3 c = Vec3.atCenterOf(pos);
        double dist = p.getEyePosition().distanceTo(c);
        if (dist <= 3.8) {
            if (Bot.mine(mc, pos)) {
                mineFails = 0;
            } else if (++mineFails > 12) {
                bad.add(pos);
                pos = null;
                mineFails = 0;
            }
            return false;
        }
        Nav.goTo(Nav.reach(c, 3.5));
        Nav.tick(mc);
        if (Nav.failed) {
            bad.add(pos);
            pos = null;
            Nav.reset();
        }
        return false;
    }

    /** Nic nie widać: zejdź na właściwą wysokość, a potem chodź/kop w jednym kierunku. */
    private void explore(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (skipDepth > 0) skipDepth--;

        if (prefDepth != Bot.NO_DEPTH && skipDepth == 0 && p.getBlockY() > prefDepth + 3) {
            Nav.goTo(Nav.depth(prefDepth + 2));
            Nav.tick(mc);
            if (Nav.failed) {
                Nav.reset();
                skipDepth = 300;     // nie da się zejść - spróbuj najpierw poziomo
            }
            return;
        }

        if (!legActive) {
            int dx = lastDx, dz = lastDz;
            if (RNG.nextInt(10) < 4 || (dx == 0 && dz == 0)) {
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                int[] d = dirs[RNG.nextInt(4)];
                dx = d[0];
                dz = d[1];
            }
            lastDx = dx;
            lastDz = dz;
            legX = p.getBlockX() + dx * 28;
            legZ = p.getBlockZ() + dz * 28;
            legActive = true;
            Nav.reset();
        }
        int keepY = prefDepth == Bot.NO_DEPTH ? Integer.MIN_VALUE : prefDepth;
        Nav.goTo(Nav.xz(legX, legZ, 3, keepY));
        boolean arrived = Nav.tick(mc);
        if (arrived || Nav.failed) {
            legActive = false;
            Nav.reset();
        }
    }

    private ItemEntity nearestItem(Minecraft mc) {
        LocalPlayer p = mc.player;
        List<ItemEntity> list = mc.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8.0));
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : list) {
            if (!item.test(e.getItem())) continue;
            double d = p.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
