package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.function.Predicate;

/** Baza dla zadań klikających w okna (crafting, piec): jedno kliknięcie co 2 ticki, żeby serwer nadążył. */
abstract class ClickTask implements Task {
    protected final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    protected boolean failed;
    protected int src = -1;
    private int delay;

    /** Przenosi dokładnie `count` sztuk pasującego przedmiotu z ekwipunku gracza do slotu dst. */
    protected void addTransfer(Minecraft mc, AbstractContainerMenu menu, Predicate<ItemStack> pr, int dst, int count) {
        steps.add(() -> {
            if (failed) return;
            src = Bot.findSlot(menu, pr);
            if (src < 0) {
                failed = true;
                return;
            }
            Bot.click(mc, menu, src, 0, ClickType.PICKUP);          // weź cały stack
        });
        for (int i = 0; i < count; i++) {
            steps.add(() -> {
                if (failed || menu.getCarried().isEmpty()) return;
                Bot.click(mc, menu, dst, 1, ClickType.PICKUP);      // połóż jedną sztukę
            });
        }
        steps.add(() -> {
            if (src >= 0 && !menu.getCarried().isEmpty()) {
                Bot.click(mc, menu, src, 0, ClickType.PICKUP);      // odłóż resztę
            }
        });
    }

    /** Wykonuje jeden krok kolejki (z przerwą). */
    protected void pump() {
        if (delay > 0) {
            delay--;
            return;
        }
        Runnable r = steps.poll();
        if (r != null) {
            r.run();
            delay = 1;
        }
    }
}
