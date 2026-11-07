package dev.botx;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Supplier;

/** Zadanie "leniwe": dopiero w momencie wykonania sprawdza stan ekwipunku i dokłada kolejne zadania. */
public final class LazyTask implements Task {
    private final String label;
    private final Supplier<List<Task>> supplier;

    public LazyTask(String label, Supplier<List<Task>> supplier) {
        this.label = label;
        this.supplier = supplier;
    }

    @Override
    public boolean tick(Minecraft mc) {
        List<Task> next = supplier.get();
        if (next != null && !next.isEmpty()) Bot.pushFront(next);
        return true;
    }

    @Override
    public String label() {
        return label;
    }
}
