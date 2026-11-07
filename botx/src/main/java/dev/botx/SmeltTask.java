package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Predicate;

/** Przetapia surowe żelazo na sztabki w piecu (Bot.furnacePos). */
public final class SmeltTask extends ClickTask {
    private static final Predicate<ItemStack> RAW = s -> s.is(Items.RAW_IRON);
    private static final Predicate<ItemStack> COAL = s -> s.is(Items.COAL) || s.is(Items.CHARCOAL);
    private static final Predicate<ItemStack> LOGS = s -> s.is(ItemTags.LOGS_THAT_BURN);

    private final int want;
    private int phase;          // 0 wkładanie, 1 czekanie, 2 odbiór, 3 koniec
    private int n;
    private int waited;
    private int openWait;

    public SmeltTask(int want) {
        this.want = want;
    }

    @Override
    public String label() {
        return "Przetapiam żelazo w piecu (" + waited / 20 + " s)";
    }

    @Override
    public boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        AbstractContainerMenu menu = p.containerMenu;
        BlockPos fp = Bot.furnacePos;
        if (fp == null || !mc.level.getBlockState(fp).is(Blocks.FURNACE)) {
            Bot.stop("Brak pieca w pobliżu - zatrzymano.");
            return true;
        }
        if (!(menu instanceof FurnaceMenu)) {
            if (phase == 3) return true;
            if (++openWait > 400) {
                Bot.stop("Nie mogę otworzyć pieca - zatrzymano.");
                return true;
            }
            Bot.openContainer(mc, fp, FurnaceMenu.class);
            return false;
        }

        if (!steps.isEmpty()) {
            pump();
            return false;
        }
        if (failed) {
            p.closeContainer();
            Bot.stop("Błąd przy wkładaniu do pieca - zatrzymano.");
            return true;
        }

        switch (phase) {
            case 0 -> {
                n = Math.min(want, Bot.count(RAW));
                if (n <= 0) {
                    p.closeContainer();
                    return true;
                }
                addTransfer(mc, menu, RAW, 0, n);
                if (Bot.count(COAL) > 0) addTransfer(mc, menu, COAL, 1, (int) Math.ceil(n / 8.0));
                else addTransfer(mc, menu, LOGS, 1, n);
                phase = 1;
            }
            case 1 -> {
                waited++;
                if (waited % 10 == 0) {
                    int out = menu.getSlot(2).getItem().getCount();
                    if (out >= n || waited > 240 * n + 200) phase = 2;
                }
            }
            case 2 -> {
                Bot.click(mc, menu, 2, 0, ClickType.QUICK_MOVE);
                phase = 3;
            }
            default -> {
                p.closeContainer();
                return true;
            }
        }
        return false;
    }
}
