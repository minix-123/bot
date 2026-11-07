package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Układa plany: z jakich zadań zbudować "zdobądź X" (drewno -> stół -> kilof -> kamień -> żelazo -> ...). */
public final class Planner {
    /** defAmount = 0 oznacza przedmiot bez wyboru ilości. */
    public record Target(String label, boolean block, int defAmount, IntFunction<List<Task>> plan) {
    }

    public static final List<Target> TARGETS = List.of(
            // ----- bloki -----
            new Target("Drewno", true, 8, n -> List.of(wood(n))),
            new Target("Kamień (cobblestone)", true, 16, n -> List.of(pick(1), stone(n))),
            new Target("Węgiel", true, 8, n -> List.of(pick(1),
                    ore("węgiel", BlockTags.COAL_ORES, Items.COAL, n, 50))),
            new Target("Ruda żelaza (surowe żelazo)", true, 6, n -> List.of(pick(2),
                    ore("żelazo", BlockTags.IRON_ORES, Items.RAW_IRON, n, 16))),
            new Target("Ruda złota (surowe złoto)", true, 4, n -> List.of(pick(3),
                    ore("złoto", BlockTags.GOLD_ORES, Items.RAW_GOLD, n, -16))),
            new Target("Diament", true, 3, n -> List.of(pick(3),
                    ore("diament", BlockTags.DIAMOND_ORES, Items.DIAMOND, n, -58))),
            new Target("Czerwony kamień", true, 8, n -> List.of(pick(3),
                    ore("redstone", BlockTags.REDSTONE_ORES, Items.REDSTONE, n, -58))),
            new Target("Lapis lazuli", true, 8, n -> List.of(pick(2),
                    ore("lapis", BlockTags.LAPIS_ORES, Items.LAPIS_LAZULI, n, 0))),
            // ----- przedmioty -----
            new Target("Stół warsztatu", false, 0, n -> List.of(planks(4), CraftTask.craftingTable())),
            new Target("Kilof drewniany", false, 0, n -> List.of(pick(1))),
            new Target("Kilof kamienny", false, 0, n -> List.of(pick(2))),
            new Target("Kilof żelazny", false, 0, n -> List.of(pick(3))),
            new Target("Kilof diamentowy", false, 0, n -> List.of(pick(4))),
            new Target("Miecz kamienny", false, 0, n -> sword(2)),
            new Target("Miecz żelazny", false, 0, n -> sword(3)),
            new Target("Miecz diamentowy", false, 0, n -> sword(4)),
            new Target("Sztabki żelaza", false, 3, Planner::ingots)
    );

    /** "Przejdź Minecrafta" - na razie etap 1: pełny zestaw diamentowy. */
    public static List<Task> beatGame() {
        List<Task> l = new ArrayList<>();
        l.add(pick(1));
        l.add(pick(2));
        l.add(pick(3));
        l.addAll(sword(3));
        l.add(pick(4));
        l.addAll(sword(4));
        l.add(lazy("Koniec etapu", () -> {
            Bot.msg("§6[Bot X] Etap 1 gotowy: masz diamentowy kilof i miecz. "
                    + "Nether i Smoka Endu bot jeszcze nie umie.");
            return List.of();
        }));
        return l;
    }

    // ---------- klocki planu ----------

    static Task lazy(String label, Supplier<List<Task>> s) {
        return new LazyTask(label, s);
    }

    static Task wood(int n) {
        return new CollectTask("drewno", st -> st.is(BlockTags.LOGS_THAT_BURN), CraftTask.LOG, n, Bot.NO_DEPTH);
    }

    static Task stone(int n) {
        return new CollectTask("kamień",
                st -> st.is(Blocks.STONE) || st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLESTONE),
                CraftTask.COBBLE, n, Bot.NO_DEPTH);
    }

    static Task ore(String name, net.minecraft.tags.TagKey<Block> tag, Item drop, int n, int depth) {
        Predicate<BlockState> b = st -> st.is(tag);
        return new CollectTask(name, b, s -> s.is(drop), n, depth);
    }

    /** Zapewnia, że w ekwipunku jest co najmniej n desek (ścina drzewa w razie potrzeby). */
    static Task planks(int n) {
        return lazy("Deski", () -> {
            int have = Bot.count(CraftTask.PLANK);
            if (have >= n) return List.of();
            int logsNeeded = (int) Math.ceil((n - have) / 4.0);
            List<Task> l = new ArrayList<>();
            if (Bot.count(CraftTask.LOG) < logsNeeded) l.add(wood(logsNeeded));
            l.add(CraftTask.planks(logsNeeded));
            return l;
        });
    }

    private static boolean haveStation(Block b, boolean table) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos cur = table ? Bot.tablePos : Bot.furnacePos;
        if (cur == null || !mc.level.getBlockState(cur).is(b)
                || cur.distSqr(mc.player.blockPosition()) > 400) {
            cur = Bot.scan(mc, st -> st.is(b), 8, 4, null);
        }
        if (table) Bot.tablePos = cur;
        else Bot.furnacePos = cur;
        return cur != null;
    }

    /** Zapewnia stół warsztatu w pobliżu (stawia nowy jeśli trzeba). */
    static Task table() {
        return lazy("Stół warsztatu", () -> {
            if (haveStation(Blocks.CRAFTING_TABLE, true)) return List.of();
            List<Task> l = new ArrayList<>();
            if (Bot.count(s -> s.is(Items.CRAFTING_TABLE)) == 0) {
                l.add(planks(4));
                l.add(CraftTask.craftingTable());
            }
            l.add(new PlaceTask(Items.CRAFTING_TABLE, "stół warsztatu", pos -> Bot.tablePos = pos));
            return l;
        });
    }

    /** Zapewnia piec w pobliżu. */
    static Task furnace() {
        return lazy("Piec", () -> {
            if (haveStation(Blocks.FURNACE, false)) return List.of();
            List<Task> l = new ArrayList<>();
            if (Bot.count(s -> s.is(Items.FURNACE)) == 0) {
                l.add(table());
                l.add(new CollectTask("kamień", st -> st.is(Blocks.STONE) || st.is(Blocks.DEEPSLATE)
                        || st.is(Blocks.COBBLESTONE), CraftTask.FURNACE_STONE, 8, Bot.NO_DEPTH));
                l.add(CraftTask.furnace());
            }
            l.add(new PlaceTask(Items.FURNACE, "piec", pos -> Bot.furnacePos = pos));
            return l;
        });
    }

    /** Zapewnia kilof o danym poziomie (1 drewno, 2 kamień, 3 żelazo, 4 diament). */
    static Task pick(int tier) {
        return lazy("Kilof " + CraftTask.tierName(tier), () ->
                Bot.pickTier() >= tier ? List.of() : tool(true, tier));
    }

    static List<Task> sword(int tier) {
        return List.of(lazy("Miecz " + CraftTask.tierName(tier), () ->
                Bot.count(s -> s.is(CraftTask.sword(tier))) > 0 ? List.of() : tool(false, tier)));
    }

    /** Pełna ścieżka zrobienia narzędzia: potrzebny kilof niższego poziomu, stół, materiały, patyki, craft. */
    static List<Task> tool(boolean pick, int tier) {
        List<Task> l = new ArrayList<>();
        if (tier >= 2) l.add(pick(tier - 1));
        l.add(table());
        l.add(lazy("Materiały", () -> {
            List<Task> m = new ArrayList<>();
            int needMat = pick ? 3 : 2;
            boolean sticks = Bot.count(CraftTask.STICK) < (pick ? 2 : 1);
            int planksNeeded = (tier == 1 ? needMat : 0) + (sticks ? 2 : 0);
            if (planksNeeded > 0) m.add(planks(planksNeeded));
            if (sticks) m.add(CraftTask.sticks());
            if (tier == 2) m.add(new CollectTask("kamień",
                    st -> st.is(Blocks.STONE) || st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLESTONE),
                    CraftTask.COBBLE, needMat, Bot.NO_DEPTH));
            if (tier == 3) m.addAll(ingots(needMat));
            if (tier == 4) {
                m.add(pick(3));
                m.add(ore("diament", BlockTags.DIAMOND_ORES, Items.DIAMOND, needMat, -58));
            }
            m.add(table());      // gdyby bot odszedł daleko od stołu
            m.add(CraftTask.tool(pick, tier));
            return m;
        }));
        return l;
    }

    /** Zapewnia n sztabek żelaza w ekwipunku (kopie rudę, stawia piec, przetapia). */
    static List<Task> ingots(int n) {
        return List.of(lazy("Żelazo", () -> {
            int have = Bot.count(s -> s.is(Items.IRON_INGOT));
            if (have >= n) return List.of();
            int need = n - have;
            List<Task> l = new ArrayList<>();
            l.add(pick(2));
            l.add(ore("żelazo", BlockTags.IRON_ORES, Items.RAW_IRON, need, 16));
            l.add(lazy("Paliwo", () -> {
                if (Bot.count(s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) > 0) return List.of();
                if (Bot.count(CraftTask.LOG) >= need) return List.of();
                return List.of(wood(need));
            }));
            l.add(furnace());
            l.add(new SmeltTask(need));
            return l;
        }));
    }
}
