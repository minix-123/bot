package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Craftuje ręcznie: układa składniki w siatce crafting (kliknięciami) i zabiera wynik.
 * pattern: 4 znaki (siatka 2x2 w ekwipunku) lub 9 znaków (3x3 przy stole); '.' = puste pole.
 */
public final class CraftTask extends ClickTask {
    public static final Predicate<ItemStack> LOG = s -> s.is(ItemTags.LOGS_THAT_BURN);
    public static final Predicate<ItemStack> PLANK = s -> s.is(ItemTags.PLANKS);
    public static final Predicate<ItemStack> STICK = s -> s.is(Items.STICK);
    public static final Predicate<ItemStack> COBBLE = s -> s.is(ItemTags.STONE_TOOL_MATERIALS);
    public static final Predicate<ItemStack> FURNACE_STONE = s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS);

    private final String name;
    private final String pattern;
    private final Map<Character, Predicate<ItemStack>> legend;
    private final Predicate<ItemStack> result;
    private final int times;

    private int done;
    private int before;
    private int wait;
    private int openWait;
    private boolean built;

    public CraftTask(String name, String pattern, Map<Character, Predicate<ItemStack>> legend,
                     Predicate<ItemStack> result, int times) {
        this.name = name;
        this.pattern = pattern;
        this.legend = legend;
        this.result = result;
        this.times = times;
    }

    private boolean table() {
        return pattern.length() == 9;
    }

    @Override
    public String label() {
        return "Craftuję: " + name;
    }

    // ---------- gotowe przepisy ----------

    public static CraftTask planks(int logs) {
        return new CraftTask("deski", "L...", Map.of('L', LOG), PLANK, logs);
    }

    public static CraftTask sticks() {
        return new CraftTask("patyki", "P.P.", Map.of('P', PLANK), STICK, 1);
    }

    public static CraftTask craftingTable() {
        return new CraftTask("stół warsztatu", "PPPP", Map.of('P', PLANK), s -> s.is(Items.CRAFTING_TABLE), 1);
    }

    public static CraftTask furnace() {
        return new CraftTask("piec", "CCCC.CCCC", Map.of('C', FURNACE_STONE), s -> s.is(Items.FURNACE), 1);
    }

    /** tier: 1 drewno, 2 kamień, 3 żelazo, 4 diament. */
    public static CraftTask tool(boolean pick, int tier) {
        Predicate<ItemStack> mat = material(tier);
        Item out = pick ? pickaxe(tier) : sword(tier);
        String pat = pick ? "MMM.S..S." : ".M..M..S.";
        return new CraftTask((pick ? "kilof " : "miecz ") + tierName(tier), pat,
                Map.of('M', mat, 'S', STICK), s -> s.is(out), 1);
    }

    public static Predicate<ItemStack> material(int tier) {
        return switch (tier) {
            case 1 -> PLANK;
            case 2 -> COBBLE;
            case 3 -> s -> s.is(Items.IRON_INGOT);
            default -> s -> s.is(Items.DIAMOND);
        };
    }

    public static Item pickaxe(int tier) {
        return switch (tier) {
            case 1 -> Items.WOODEN_PICKAXE;
            case 2 -> Items.STONE_PICKAXE;
            case 3 -> Items.IRON_PICKAXE;
            default -> Items.DIAMOND_PICKAXE;
        };
    }

    public static Item sword(int tier) {
        return switch (tier) {
            case 1 -> Items.WOODEN_SWORD;
            case 2 -> Items.STONE_SWORD;
            case 3 -> Items.IRON_SWORD;
            default -> Items.DIAMOND_SWORD;
        };
    }

    public static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "drewniany";
            case 2 -> "kamienny";
            case 3 -> "żelazny";
            default -> "diamentowy";
        };
    }

    // ---------- logika ----------

    @Override
    public boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (done >= times) {
            if (table() && p.containerMenu != p.inventoryMenu) p.closeContainer();
            return true;
        }
        AbstractContainerMenu menu = p.containerMenu;
        if (table()) {
            BlockPos tp = Bot.tablePos;
            if (tp == null || !mc.level.getBlockState(tp).is(Blocks.CRAFTING_TABLE)) {
                Bot.stop("Brak stołu warsztatu w pobliżu - zatrzymano.");
                return true;
            }
            if (!(menu instanceof CraftingMenu)) {
                if (++openWait > 400) {
                    Bot.stop("Nie mogę otworzyć stołu warsztatu - zatrzymano.");
                    return true;
                }
                Bot.openContainer(mc, tp, CraftingMenu.class);
                return false;
            }
        } else if (menu != p.inventoryMenu) {
            p.closeContainer();
            return false;
        }

        if (!steps.isEmpty()) {
            pump();
            return false;
        }

        if (!built) {
            build(mc, menu);
            before = Bot.count(result);
            built = true;
            return false;
        }

        // kolejka pusta: sprawdź czy wynik się pojawił
        if (failed) {
            cleanup(mc, menu);
            Bot.stop("Nie udało się wytworzyć: " + name);
            return true;
        }
        if (++wait < 4) return false;
        if (Bot.count(result) > before) {
            done++;
            built = false;
            wait = 0;
            return false;
        }
        if (wait > 14) {
            failed = true;
        }
        return false;
    }

    private void build(Minecraft mc, AbstractContainerMenu menu) {
        wait = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '.') continue;
            addTransfer(mc, menu, legend.get(c), 1 + i, 1);
        }
        steps.add(() -> {
            if (failed) return;
            ItemStack r = menu.getSlot(0).getItem();
            if (r.isEmpty() || !result.test(r)) {
                failed = true;
                return;
            }
            Bot.click(mc, menu, 0, 0, ClickType.QUICK_MOVE);      // shift-klik na wyniku
        });
    }

    /** Oddaje składniki z siatki do ekwipunku. */
    private void cleanup(Minecraft mc, AbstractContainerMenu menu) {
        if (!menu.getCarried().isEmpty() && src >= 0) Bot.click(mc, menu, src, 0, ClickType.PICKUP);
        for (int i = 0; i < pattern.length(); i++) {
            if (menu.getSlot(1 + i).hasItem()) Bot.click(mc, menu, 1 + i, 0, ClickType.QUICK_MOVE);
        }
        if (table() && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
    }
}
