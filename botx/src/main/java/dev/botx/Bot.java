package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/** Rdzeń bota: kolejka zadań, ruch, kopanie, ekwipunek. */
public final class Bot {
    public static final int NO_DEPTH = Integer.MIN_VALUE;

    public static boolean running = false;
    public static String status = "Bot wyłączony";
    private static final ArrayDeque<Task> queue = new ArrayDeque<>();
    private static Task current;
    private static int statusTimer;
    private static final Random RNG = new Random();

    /** Ostatnio postawiony stół warsztatu / piec (do ponownego użycia). */
    public static BlockPos tablePos;
    public static BlockPos furnacePos;

    // ---------- start / stop ----------

    public static void start(List<Task> tasks, String label) {
        stop(null);
        queue.addAll(tasks);
        running = true;
        status = label;
        msg("§a[Bot X] Start: " + label);
    }

    public static void stop(String reason) {
        boolean was = running;
        running = false;
        queue.clear();
        current = null;
        Minecraft mc = Minecraft.getInstance();
        releaseKeys(mc);
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        status = "Bot wyłączony";
        if (reason != null && was) msg("§e[Bot X] " + reason);
    }

    public static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), false);
    }

    private static void releaseKeys(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
    }

    // ---------- główna pętla (co tick klienta) ----------

    public static void tick(Minecraft mc) {
        if (!running) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            stop("Rozłączono ze światem.");
            return;
        }
        if (!p.isAlive()) {
            stop("Bot zginął - zatrzymano.");
            return;
        }
        if (p.getHealth() < 5f) {
            stop("Mało zdrowia (<2.5 serca) - zatrzymano dla bezpieczeństwa.");
            return;
        }
        releaseKeys(mc);
        if (useCooldown > 0) useCooldown--;

        if (Combat.defend(mc)) return;      // potwór tuż obok - najpierw walka

        if (current == null) {
            current = queue.poll();
            if (current == null) {
                stop("Gotowe! Zadanie wykonane.");
                return;
            }
        }
        boolean done;
        try {
            done = current.tick(mc);
        } catch (RuntimeException e) {
            e.printStackTrace();
            stop("Błąd bota: " + e);
            return;
        }
        if (!running) return;
        if (done) current = null;
        else if (current != null && --statusTimer <= 0) {
            statusTimer = 10;
            status = current.label();
            p.displayClientMessage(Component.literal("§b[Bot X] " + status), true);
        }
    }

    /** Wstawia zadania na początek kolejki (używane przez zadania "leniwe"). */
    public static void pushFront(List<Task> tasks) {
        for (int i = tasks.size() - 1; i >= 0; i--) queue.addFirst(tasks.get(i));
    }

    // ---------- ekwipunek ----------

    public static int count(Predicate<ItemStack> pred) {
        Inventory inv = Minecraft.getInstance().player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && pred.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Numer slotu w danym menu, w którym leży pasujący przedmiot gracza (tylko ekwipunek gracza). */
    public static int findSlot(AbstractContainerMenu menu, Predicate<ItemStack> pred) {
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container == inv && s.getContainerSlot() < 36) {
                ItemStack st = s.getItem();
                if (!st.isEmpty() && pred.test(st)) return i;
            }
        }
        return -1;
    }

    public static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
    }

    /** Bierze przedmiot do ręki (przenosi na pasek jeśli trzeba). */
    public static boolean equip(Minecraft mc, Predicate<ItemStack> pred) {
        LocalPlayer p = mc.player;
        Inventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && pred.test(s)) {
                if (inv.getSelectedSlot() != i) inv.setSelectedSlot(i);
                return true;
            }
        }
        if (p.containerMenu != p.inventoryMenu) return false;
        int slot = findSlot(p.inventoryMenu, pred);
        if (slot < 0) return false;
        click(mc, p.inventoryMenu, slot, inv.getSelectedSlot(), ClickType.SWAP);
        return true;
    }

    public static int pickTier() {
        if (count(s -> s.is(Items.DIAMOND_PICKAXE) || s.is(Items.NETHERITE_PICKAXE)) > 0) return 4;
        if (count(s -> s.is(Items.IRON_PICKAXE)) > 0) return 3;
        if (count(s -> s.is(Items.STONE_PICKAXE)) > 0) return 2;
        if (count(s -> s.is(Items.WOODEN_PICKAXE)) > 0) return 1;
        return 0;
    }

    public static void equipBestPick(Minecraft mc) {
        int t = pickTier();
        if (t == 0) return;
        equip(mc, s -> s.is(switch (t) {
            case 4 -> Items.DIAMOND_PICKAXE;
            case 3 -> Items.IRON_PICKAXE;
            case 2 -> Items.STONE_PICKAXE;
            default -> Items.WOODEN_PICKAXE;
        }) || (t == 4 && s.is(Items.NETHERITE_PICKAXE)));
    }

    // ---------- świat ----------

    public static boolean solid(Minecraft mc, BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        return !s.getCollisionShape(mc.level, pos).isEmpty();
    }

    public static boolean lavaNear(ClientLevel lv, BlockPos pos) {
        if (lv.getFluidState(pos).is(FluidTags.LAVA)) return true;
        for (Direction d : Direction.values()) {
            if (lv.getFluidState(pos.relative(d)).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** Najbliższy blok spełniający warunek w promieniu (poziomo r, pionowo +-rv). */
    public static BlockPos scan(Minecraft mc, Predicate<BlockState> pred, int r, int rv, java.util.Set<BlockPos> bad) {
        BlockPos o = mc.player.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();
        for (int dy = -rv; dy <= rv; dy++) {
            int y = o.getY() + dy;
            if (y < minY || y > maxY) continue;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(o.getX() + dx, y, o.getZ() + dz);
                    BlockState st = mc.level.getBlockState(m);
                    if (st.isAir() || !pred.test(st)) continue;
                    double d = dx * dx + dz * dz + dy * dy * 1.5;
                    if (d < bestD && (bad == null || !bad.contains(m))) {
                        bestD = d;
                        best = m.immutable();
                    }
                }
            }
        }
        return best;
    }

    // ---------- celowanie, ruch, kopanie ----------

    public static void look(LocalPlayer p, Vec3 t) {
        Vec3 e = p.getEyePosition();
        double dx = t.x - e.x, dy = t.y - e.y, dz = t.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * 57.29577951308232) - 90f;
        float pitch = (float) -(Mth.atan2(dy, h) * 57.29577951308232);
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -90f, 90f));
        p.setYHeadRot(yaw);
    }

    /** Kopie blok, na który patrzy (pierwszy trafiony promieniem - też przeszkodę). */
    public static boolean mine(Minecraft mc, BlockPos pos) {
        LocalPlayer p = mc.player;
        ClientLevel lv = mc.level;
        BlockState st = lv.getBlockState(pos);
        if (st.isAir() || st.getDestroySpeed(lv, pos) < 0) return false;
        look(p, Vec3.atCenterOf(pos));
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(4.5));
        BlockHitResult h = lv.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (h.getType() != HitResult.Type.BLOCK) return false;
        BlockPos hp = h.getBlockPos();
        BlockState hs = lv.getBlockState(hp);
        if (hs.getDestroySpeed(lv, hp) < 0) return false;
        if (lavaNear(lv, hp)) {
            stop("Lawa przy kopanym bloku - zatrzymano.");
            return false;
        }
        if (hs.is(BlockTags.MINEABLE_WITH_PICKAXE)) equipBestPick(mc);
        mc.gameMode.continueDestroyBlock(hp, h.getDirection());
        p.swing(InteractionHand.MAIN_HAND);
        mc.options.keyUp.setDown(false);
        return true;
    }

    private static int stuckTicks;
    private static Vec3 lastPos = Vec3.ZERO;

    /** Idzie w stronę celu; gdy tunnel=true przekopuje się przez przeszkody. */
    public static void moveToward(Minecraft mc, Vec3 target, boolean tunnel) {
        LocalPlayer p = mc.player;
        if (tunnel) {
            double hd = Math.hypot(target.x - p.getX(), target.z - p.getZ());
            double dy = target.y - p.getY();
            if (hd < 2.5 && dy > 1.8 && pillarUp(mc)) return;     // cel nad nami - dobuduj się w górę
            if (hd < 2.5 && dy < -1.8) {                           // cel pod nami - kop w dół
                digDown(mc);
                return;
            }
        }
        look(p, target);
        mc.options.keyUp.setDown(true);
        mc.options.keySprint.setDown(true);
        if (p.position().distanceToSqr(lastPos) < 0.0009) stuckTicks++;
        else stuckTicks = 0;
        lastPos = p.position();
        if (p.isInWater()) mc.options.keyJump.setDown(true);
        if (p.horizontalCollision || stuckTicks > 10) obstacle(mc, tunnel);
    }

    private static void obstacle(Minecraft mc, boolean tunnel) {
        LocalPlayer p = mc.player;
        Direction d = p.getDirection();
        BlockPos feet = p.blockPosition().relative(d);
        BlockPos head = feet.above();
        boolean fs = solid(mc, feet), hs = solid(mc, head);
        if (!fs && !hs) {
            if (p.onGround()) mc.options.keyJump.setDown(true);
            return;
        }
        if (!hs && !solid(mc, head.above())) {          // jeden stopień - skacz
            if (p.onGround()) mc.options.keyJump.setDown(true);
            return;
        }
        if (tunnel) {
            if (hs) mine(mc, head);
            else mine(mc, feet);
        } else if (stuckTicks > 20) {                     // obejdź
            p.setYRot(p.getYRot() + (RNG.nextBoolean() ? 70 : -70));
            stuckTicks = 0;
        }
    }

    // ---------- eksploracja gdy nic nie widać ----------

    private static float exploreYaw = Float.NaN;
    private static int exploreTimer;

    public static void explore(Minecraft mc, int prefDepth) {
        LocalPlayer p = mc.player;
        if (Float.isNaN(exploreYaw)) exploreYaw = Math.round(p.getYRot() / 90f) * 90f;
        if (--exploreTimer <= 0) {
            exploreTimer = 120 + RNG.nextInt(120);
            exploreYaw += (RNG.nextBoolean() ? 1 : -1) * (prefDepth == NO_DEPTH ? 40 + RNG.nextInt(100) : 90);
        }
        if (prefDepth == NO_DEPTH) {
            p.setYRot(exploreYaw);
            p.setXRot(0);
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(true);
            if (p.horizontalCollision) obstacle(mc, true);
            return;
        }
        boolean down = p.getY() > prefDepth + 2;
        tunnelStep(mc, Direction.fromYRot(Math.round(exploreYaw / 90f) * 90f), down);
    }

    private static void tunnelStep(Minecraft mc, Direction d, boolean down) {
        LocalPlayer p = mc.player;
        BlockPos ahead = p.blockPosition().relative(d);
        BlockPos[] col = down
                ? new BlockPos[]{ahead.above(), ahead, ahead.below()}
                : new BlockPos[]{ahead.above(), ahead};
        BlockPos[] danger = {ahead.above(), ahead, ahead.below(), ahead.below(2)};
        for (BlockPos b : danger) {
            if (mc.level.getFluidState(b).is(FluidTags.LAVA)) {
                stop("Lawa na drodze - zatrzymano.");
                return;
            }
        }
        for (BlockPos b : col) {
            if (solid(mc, b)) {
                mine(mc, b);
                return;
            }
        }
        p.setYRot(d.toYRot());
        p.setXRot(0);
        mc.options.keyUp.setDown(true);
    }

    /** Kopie pod nogami (gdy cel jest głęboko pod botem). */
    public static void digDown(Minecraft mc) {
        BlockPos below = mc.player.blockPosition().below();
        if (solid(mc, below)) mine(mc, below);
    }

    // ---------- używanie bloków (stół, piec) ----------

    private static int useCooldown;

    public static boolean openContainer(Minecraft mc, BlockPos pos, Class<? extends AbstractContainerMenu> cls) {
        LocalPlayer p = mc.player;
        if (cls.isInstance(p.containerMenu)) return true;
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 eye = p.getEyePosition();
        if (eye.distanceTo(c) > 4.0) {
            moveToward(mc, c, true);          // idzie, kopie przeszkody, buduje w górę / kopie w dół
            return false;
        }
        look(p, c);
        BlockHitResult h = mc.level.clip(new ClipContext(eye, c, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        boolean hit = h.getType() == HitResult.Type.BLOCK;
        if (hit && !h.getBlockPos().equals(pos)) {
            mine(mc, h.getBlockPos());        // ściana/blok między botem a stołem lub piecem - zniszcz go
            return false;
        }
        if (useCooldown > 0) return false;
        useCooldown = 15;
        BlockHitResult use = hit ? h : new BlockHitResult(c, Direction.UP, pos, false);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, use);
        p.swing(InteractionHand.MAIN_HAND);
        return false;
    }

    // ---------- budowanie w górę (słupek) ----------

    private static final Item[] BUILD_BLOCKS = {
            Items.DIRT, Items.NETHERRACK, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF,
            Items.COBBLED_DEEPSLATE, Items.COBBLESTONE, Items.STONE
    };

    private static Item buildBlock() {
        for (Item it : BUILD_BLOCKS) if (count(s -> s.is(it)) > 0) return it;
        return null;
    }

    /**
     * Jeden tick budowania słupka: skacze i stawia blok pod sobą. Jeśli nad głową jest sufit - kopie go.
     * Zwraca false gdy bot nie ma czym budować.
     */
    public static boolean pillarUp(Minecraft mc) {
        LocalPlayer p = mc.player;
        Item blk = buildBlock();
        if (blk == null) return false;
        BlockPos head2 = p.blockPosition().above(2);
        if (solid(mc, head2)) {
            mine(mc, head2);
            return true;
        }
        if (!p.getMainHandItem().is(blk)) {
            equip(mc, s -> s.is(blk));
            return true;
        }
        p.setXRot(90f);
        BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
        if (!solid(mc, under) && solid(mc, under.below()) && p.getY() > under.getY() + 1.0) {
            Vec3 hv = new Vec3(under.getX() + 0.5, under.getY(), under.getZ() + 0.5);
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hv, Direction.UP, under.below(), false));
            p.swing(InteractionHand.MAIN_HAND);
        }
        if (p.onGround()) mc.options.keyJump.setDown(true);
        return true;
    }
}
