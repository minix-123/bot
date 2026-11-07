package dev.botx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Wybór bloku lub przedmiotu do zdobycia + zielony przycisk START. */
public class ItemPickScreen extends Screen {
    private final Screen parent;
    private static boolean blocksTab = true;
    private static Planner.Target selected;
    private static int amount;

    private int startX, startY, startW, startH;

    public ItemPickScreen(Screen parent) {
        super(Component.literal("Zdobądź przedmiot"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // zakładki
        addRenderableWidget(Button.builder(Component.literal(blocksTab ? "§e[ Bloki ]" : "Bloki"), b -> {
            blocksTab = true;
            rebuildWidgets();
        }).bounds(cx - 150, 28, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal(!blocksTab ? "§e[ Przedmioty ]" : "Przedmioty"), b -> {
            blocksTab = false;
            rebuildWidgets();
        }).bounds(cx + 2, 28, 148, 20).build());

        // lista celów (2 kolumny)
        List<Planner.Target> list = new ArrayList<>();
        for (Planner.Target t : Planner.TARGETS) if (t.block() == blocksTab) list.add(t);
        for (int i = 0; i < list.size(); i++) {
            Planner.Target t = list.get(i);
            int col = i % 2, row = i / 2;
            boolean sel = t == selected;
            addRenderableWidget(Button.builder(Component.literal(sel ? "§a> " + t.label() : t.label()), b -> {
                selected = t;
                amount = t.defAmount();
                rebuildWidgets();
            }).bounds(cx - 150 + col * 152, 56 + row * 22, 148, 20).build());
        }

        // ilość
        int by = this.height - 62;
        if (selected != null && selected.defAmount() > 0) {
            addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                amount = Math.max(1, amount - 1);
                rebuildWidgets();
            }).bounds(cx - 60, by, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                amount = Math.min(64, amount + 1);
                rebuildWidgets();
            }).bounds(cx + 40, by, 20, 20).build());
        }

        // zielony START
        startW = 150;
        startH = 20;
        startX = cx - startW / 2;
        startY = this.height - 36;
        Button start = Button.builder(Component.literal("§a§l> START"), b -> {
            if (selected == null) return;
            Bot.start(selected.plan().apply(amount), "Zdobywam: " + selected.label());
            this.minecraft.setScreen(null);
        }).bounds(startX, startY, startW, startH).build();
        start.active = selected != null;
        addRenderableWidget(start);

        addRenderableWidget(Button.builder(Component.literal("Wstecz"), b -> this.minecraft.setScreen(parent))
                .bounds(10, this.height - 30, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // zielona ramka pod przyciskiem START (rysowana przed widgetami)
        if (selected != null) {
            g.fill(startX - 2, startY - 2, startX + startW + 2, startY + startH + 2, 0xFF2ECC40);
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, "Zdobądź przedmiot", this.width / 2, 12, 0xFFFFFFFF);
        if (selected != null && selected.defAmount() > 0) {
            g.drawCenteredString(this.font, "Ilość: " + amount, this.width / 2, this.height - 56, 0xFFFFFFFF);
        } else if (selected == null) {
            g.drawCenteredString(this.font, "Wybierz blok lub przedmiot", this.width / 2, this.height - 56, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
