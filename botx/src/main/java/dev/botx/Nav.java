package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Nawigacja: planuje trasę algorytmem A* po blokach i ją wykonuje.
 * Ruchy: iść, wskoczyć o 1 w górę, zeskoczyć (do 3 bloków), kopać w dół, budować słupek w górę.
 * Przeszkody (bloki) są rozkopywane, lawa i niebezpieczne bloki omijane.
 */
public final class Nav {
    public enum Move {TRAVERSE, ASCEND, DESCEND, DIG_DOWN, PILLAR}

    public record Step(BlockPos pos, Move move) {
    }

    /** Cel nawigacji. key rozróżnia cele (zmiana key = nowa trasa). */
    public abstract static class Goal {
        final String key;

        protected Goal(String key) {
            this.key = key;
        }

        abstract boolean reached(BlockPos feet);

        abstract double h(BlockPos feet);

        double penalty(BlockPos feet) {
            return 0;
        }
    }

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double WEIGHT = 1.4;
    private static final int MAX_NODES = 3000;

    public static boolean failed;
    private static Goal goal;
    private static List<Step> path;
    private static int idx;
    private static BlockPos pathStart;
    private static int stepTicks;
    private static int replanCd;
    private static int stepFails;
    private static int deviations;

    // ---------- cele ----------

    private static Vec3 eye(BlockPos f) {
        return new Vec3(f.getX() + 0.5, f.getY() + 1.62, f.getZ() + 0.5);
    }

    private static Vec3 body(BlockPos f) {
        return new Vec3(f.getX() + 0.5, f.getY() + 1.0, f.getZ() + 0.5);
    }

    /** Dojść na odległość r (od oczu) do punktu t - np. żeby kopać blok. */
    public static Goal reach(Vec3 t, double r) {
        return reach(t, r, "reach:" + BlockPos.containing(t) + ":" + r);
    }

    public static Goal reach(Vec3 t, double r, String key) {
        return new Goal(key) {
            boolean reached(BlockPos f) {
                return eye(f).distanceTo(t) <= r;
            }

            double h(BlockPos f) {
                return Math.max(0, eye(f).distanceTo(t) - r);
            }
        };
    }

    /** Podejść ciałem na odległość r do punktu t - np. podnieść przedmiot, dojść do gracza. */
    public static Goal touch(Vec3 t, double r) {
        return touch(t, r, "touch:" + BlockPos.containing(t) + ":" + r);
    }

    public static Goal touch(Vec3 t, double r, String key) {
        return new Goal(key) {
            boolean reached(BlockPos f) {
                return body(f).distanceTo(t) <= r;
            }

            double h(BlockPos f) {
                return Math.max(0, body(f).distanceTo(t) - r);
            }
        };
    }

    /** Zejść na wysokość y lub niżej. */
    public static Goal depth(int y) {
        return new Goal("depth:" + y) {
            boolean reached(BlockPos f) {
                return f.getY() <= y;
            }

            double h(BlockPos f) {
                return Math.max(0, f.getY() - y) * 3.0;
            }
        };
    }

    /** Wejść na wysokość y lub wyżej. */
    public static Goal up(int y) {
        return new Goal("up:" + y) {
            boolean reached(BlockPos f) {
                return f.getY() >= y;
            }

            double h(BlockPos f) {
                return Math.max(0, y - f.getY()) * 3.0;
            }
        };
    }

    /** Dojść w okolice punktu (x,z); keepY != MIN_VALUE trzyma wysokość blisko keepY (kopanie tuneli). */
    public static Goal xz(int tx, int tz, int tol, int keepY) {
        return new Goal("xz:" + tx + "," + tz + "," + keepY) {
            double hd(BlockPos f) {
                return Math.hypot(f.getX() - tx, f.getZ() - tz);
            }

            boolean reached(BlockPos f) {
                return hd(f) <= tol;
            }

            double h(BlockPos f) {
                return Math.max(0, hd(f) - tol);
            }

            double penalty(BlockPos f) {
                return keepY == Integer.MIN_VALUE ? 0 : Math.abs(f.getY() - keepY) * 0.5;
            }
        };
    }

    // ---------- API ----------

    public static void reset() {
        goal = null;
        path = null;
        idx = 0;
        failed = false;
        stepTicks = 0;
        replanCd = 0;
        stepFails = 0;
        deviations = 0;
    }

    /** Ustawia cel. Ten sam key = zachowaj trasę, inny = zaplanuj od nowa. */
    public static void goTo(Goal g) {
        if (goal == null || !goal.key.equals(g.key)) {
            reset();
        }
        goal = g;
    }

    /** Wymusza nowe planowanie (gdy cel się przesunął). */
    public static void invalidate() {
        path = null;
        replanCd = 0;
    }

    /** Jeden tick marszu. Zwraca true gdy cel osiągnięty. Przy niepowodzeniu ustawia Nav.failed. */
    public static boolean tick(Minecraft mc) {
        if (goal == null) return true;
        LocalPlayer p = mc.player;
        BlockPos feet = p.blockPosition();
        if (goal.reached(feet)) {
            path = null;
            return true;
        }
        if (failed) return false;
        if (path == null || idx >= path.size()) {
            if (replanCd > 0) {
                replanCd--;
                return false;
            }
            if (!plan(mc)) {
                failed = true;
                return false;
            }
        }
        follow(mc);
        return false;
    }

    private static boolean plan(Minecraft mc) {
        BlockPos start = mc.player.blockPosition();
        List<Step> r = search(mc.level, start, goal, Bot.buildBlockAvailable());
        replanCd = 3;
        if (r == null || r.isEmpty()) return false;
        path = r;
        idx = 0;
        pathStart = start;
        stepTicks = 0;
        return true;
    }

    // ---------- wykonywanie trasy ----------

    private static boolean onRoute(BlockPos feet, BlockPos prev, BlockPos cur) {
        if (feet.equals(prev) || feet.equals(cur)) return true;
        int lo = Math.min(prev.getY(), cur.getY());
        int hi = Math.max(prev.getY(), cur.getY());
        boolean colCur = feet.getX() == cur.getX() && feet.getZ() == cur.getZ()
                && feet.getY() >= lo && feet.getY() <= hi;
        boolean colPrev = feet.getX() == prev.getX() && feet.getZ() == prev.getZ()
                && feet.getY() >= lo && feet.getY() <= hi + 1;
        return colCur || colPrev;
    }

    /** Rozkopuje pierwszy pełny blok z listy. Zwraca true gdy bot jest zajęty kopaniem. */
    private static boolean clear(Minecraft mc, BlockPos... cells) {
        for (BlockPos c : cells) {
            if (Bot.solid(mc, c)) {
                if (!Bot.mine(mc, c)) stepTicks += 15;
                return true;
            }
        }
        return false;
    }

    private static void follow(Minecraft mc) {
        LocalPlayer p = mc.player;
        BlockPos feet = p.blockPosition();

        for (int j = idx; j < Math.min(path.size(), idx + 4); j++) {
            if (feet.equals(path.get(j).pos())) {
                idx = j + 1;
                stepTicks = 0;
            }
        }
        if (idx >= path.size()) return;

        Step cur = path.get(idx);
        BlockPos prev = idx > 0 ? path.get(idx - 1).pos() : pathStart;
        BlockPos t = cur.pos();

        if (!onRoute(feet, prev, t)) {            // zepchnięty z trasy - zaplanuj od nowa
            path = null;
            replanCd = 0;
            if (++deviations > 12) failed = true;
            return;
        }
        if (++stepTicks > 300) {                  // krok trwa za długo
            path = null;
            replanCd = 0;
            stepTicks = 0;
            if (++stepFails > 3) failed = true;
            return;
        }

        if (p.isInWater()) mc.options.keyJump.setDown(true);

        Vec3 center = Vec3.atBottomCenterOf(t);
        double hd = Math.hypot(center.x - p.getX(), center.z - p.getZ());
        Vec3 aim = new Vec3(center.x, p.getEyeY(), center.z);

        switch (cur.move()) {
            case TRAVERSE -> {
                if (clear(mc, t.above(), t)) return;
                walk(mc, aim, hd, true);
            }
            case DESCEND -> {
                BlockPos a = new BlockPos(t.getX(), prev.getY(), t.getZ());
                if (clear(mc, a.above(), a)) return;
                if (p.onGround() || hd > 0.3) walk(mc, aim, hd, false);
            }
            case ASCEND -> {
                if (clear(mc, prev.above(2), t.above(), t)) return;
                Bot.look(p, aim);
                mc.options.keyUp.setDown(true);
                if (p.onGround() && hd < 1.6) mc.options.keyJump.setDown(true);
            }
            case DIG_DOWN -> {
                if (hd > 0.35 && p.onGround()) {
                    walk(mc, aim, hd, false);
                } else if (Bot.solid(mc, t)) {
                    if (!Bot.mine(mc, t)) stepTicks += 15;
                }
            }
            case PILLAR -> {
                if (hd > 0.6 && p.onGround()) {
                    walk(mc, aim, hd, false);
                } else if (!Bot.pillarUp(mc)) {
                    failed = true;
                }
            }
        }
    }

    private static void walk(Minecraft mc, Vec3 aim, double hd, boolean sprint) {
        LocalPlayer p = mc.player;
        Bot.look(p, aim);
        mc.options.keyUp.setDown(true);
        if (sprint && hd > 1.0) mc.options.keySprint.setDown(true);
        if (p.horizontalCollision && p.onGround()) mc.options.keyJump.setDown(true);
    }

    // ---------- A* ----------

    private interface Sink {
        void add(BlockPos pos, Move mv, double cost);
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final Move move;
        final double g;
        final double f;

        Node(BlockPos pos, Node parent, Move move, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.move = move;
            this.g = g;
            this.f = f;
        }
    }

    /** Wiedza o świecie z cache'm kosztów w czasie jednego planowania. */
    private static final class Ctx {
        final ClientLevel lv;
        final HashMap<Long, Double> cache = new HashMap<>();

        Ctx(ClientLevel lv) {
            this.lv = lv;
        }

        boolean lavaAdj(BlockPos p) {
            if (lv.getFluidState(p).is(FluidTags.LAVA)) return true;
            for (Direction d : Direction.values()) {
                if (lv.getFluidState(p.relative(d)).is(FluidTags.LAVA)) return true;
            }
            return false;
        }

        boolean water(BlockPos p) {
            return lv.getFluidState(p).is(FluidTags.WATER);
        }

        /** 0 = wolne przejście, >0 = trzeba rozkopać (koszt), INF = nie da się / niebezpieczne. */
        double cell(BlockPos p) {
            Long key = p.asLong();
            Double c = cache.get(key);
            if (c != null) return c;
            double v = compute(p);
            cache.put(key, v);
            return v;
        }

        private double compute(BlockPos p) {
            if (lv.isOutsideBuildHeight(p) || !lv.hasChunkAt(p)) return INF;
            if (lavaAdj(p)) return INF;
            BlockState st = lv.getBlockState(p);
            if (dangerous(st)) return INF;
            FluidState fs = st.getFluidState();
            if (st.getCollisionShape(lv, p).isEmpty()) {
                return fs.isEmpty() ? 0 : 1.5;
            }
            float hard = st.getDestroySpeed(lv, p);
            if (hard < 0 || hard > 30 || st.hasBlockEntity() || !fs.isEmpty()) return INF;
            double cost = 2 + hard * 3;
            if (lv.getBlockState(p.above()).getBlock() instanceof FallingBlock) cost += 3;
            for (Direction d : Direction.values()) {
                if (!lv.getFluidState(p.relative(d)).isEmpty()) cost += 6;     // grozi zalaniem
            }
            return cost;
        }

        /** Czy można stanąć na tym bloku. */
        boolean floor(BlockPos p) {
            if (lv.isOutsideBuildHeight(p) || !lv.hasChunkAt(p)) return false;
            BlockState st = lv.getBlockState(p);
            return !st.getCollisionShape(lv, p).isEmpty() && !dangerous(st) && st.getFluidState().isEmpty();
        }
    }

    private static boolean dangerous(BlockState st) {
        return st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE) || st.is(Blocks.CACTUS) || st.is(Blocks.COBWEB)
                || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.CAMPFIRE)
                || st.is(Blocks.SOUL_CAMPFIRE) || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.WITHER_ROSE)
                || st.is(Blocks.TNT) || st.is(Blocks.LAVA);
    }

    private static final Direction[] H = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private static void neighbours(Ctx c, BlockPos f, boolean canBuild, Sink out) {
        for (Direction d : H) {
            BlockPos a = f.relative(d);
            BlockPos b = a.above();
            double ca = c.cell(a);
            double cb = c.cell(b);
            if (ca < INF && cb < INF) {
                if (c.floor(a.below()) || c.water(a)) {
                    out.add(a, Move.TRAVERSE, 1 + ca + cb);
                } else if (ca == 0 && cb == 0) {                       // krawędź - zeskok do 3 bloków
                    for (int k = 1; k <= 3; k++) {
                        BlockPos l = a.below(k);
                        if (c.cell(l) != 0) break;
                        if (c.floor(l.below())) {
                            out.add(l, Move.DESCEND, 1.5 + 0.8 * k);
                            break;
                        }
                    }
                }
            }
            if (c.floor(a)) {                                          // wskoczenie o 1 w górę
                double ch = c.cell(f.above(2));
                double ta = c.cell(a.above());
                double tb = c.cell(a.above(2));
                if (ch < INF && ta < INF && tb < INF) out.add(a.above(), Move.ASCEND, 2 + ch + ta + tb);
            }
        }
        BlockPos below = f.below();
        double cbl = c.cell(below);
        if (cbl > 0 && cbl < INF && c.floor(f.below(2))) {             // kopanie w dół
            out.add(below, Move.DIG_DOWN, 3 + cbl);
        }
        if (canBuild) {                                                // słupek w górę
            double ct = c.cell(f.above(2));
            if (ct < INF) out.add(f.above(), Move.PILLAR, 5 + ct);
        }
    }

    private static List<Step> search(ClientLevel lv, BlockPos start, Goal g, boolean canBuild) {
        Ctx c = new Ctx(lv);
        HashMap<Long, Node> best = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble((Node n) -> n.f));
        Node s = new Node(start, null, null, 0, WEIGHT * g.h(start));
        best.put(start.asLong(), s);
        open.add(s);
        Node bestNode = s;
        double bestH = g.h(start);
        Node found = null;
        int expanded = 0;
        long deadline = System.nanoTime() + 40_000_000L;

        while (!open.isEmpty() && expanded < MAX_NODES) {
            Node n = open.poll();
            if (best.get(n.pos.asLong()) != n) continue;
            expanded++;
            if ((expanded & 63) == 0 && System.nanoTime() > deadline) break;
            if (g.reached(n.pos)) {
                found = n;
                break;
            }
            double h = g.h(n.pos);
            if (h < bestH - 1e-6) {
                bestH = h;
                bestNode = n;
            }
            neighbours(c, n.pos, canBuild, (pos, mv, cost) -> {
                if (Math.abs(pos.getX() - start.getX()) > 90 || Math.abs(pos.getZ() - start.getZ()) > 90) return;
                double ng = n.g + cost + g.penalty(pos);
                Node old = best.get(pos.asLong());
                if (old == null || ng < old.g - 1e-9) {
                    Node nn = new Node(pos, n, mv, ng, ng + WEIGHT * g.h(pos));
                    best.put(pos.asLong(), nn);
                    open.add(nn);
                }
            });
        }
        Node end = found != null ? found : bestNode;
        if (end == s) return null;
        LinkedList<Step> steps = new LinkedList<>();
        for (Node n = end; n != s; n = n.parent) steps.addFirst(new Step(n.pos, n.move));
        return new ArrayList<>(steps);
    }

    private Nav() {
    }
}
