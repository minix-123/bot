package dev.botx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Główne menu moda (klawisz N). */
public class BotXScreen extends Screen {
    private static String lastNick = "";
    private EditBox nickBox;

    public BotXScreen() {
        super(Component.literal("Bot X"));
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 70;

        addRenderableWidget(Button.builder(Component.literal("Zdobądź przedmiot"),
                b -> this.minecraft.setScreen(new ItemPickScreen(this))).bounds(x, y, w, 20).build());

        nickBox = new EditBox(this.font, x, y + 36, w, 20, Component.literal("Nick"));
        nickBox.setMaxLength(16);
        nickBox.setHint(Component.literal("Nick gracza do zabicia"));
        nickBox.setValue(lastNick);
        addRenderableWidget(nickBox);

        addRenderableWidget(Button.builder(Component.literal("§cZabij gracza"), b -> {
            String nick = nickBox.getValue().trim();
            if (nick.isEmpty()) return;
            lastNick = nick;
            Bot.start(List.of(new KillPlayerTask(nick)), "Zabijam gracza " + nick);
            onClose();
        }).bounds(x, y + 60, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Przejdź Minecrafta (etap 1)"), b -> {
            Bot.start(Planner.beatGame(), "Przejdź Minecrafta");
            onClose();
        }).bounds(x, y + 90, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Wyłącz bota"), b -> {
            Bot.stop("Bot wyłączony.");
            onClose();
        }).bounds(x, y + 120, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Zamknij"), b -> onClose())
                .bounds(x, y + 148, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, "Bot X", this.width / 2, this.height / 2 - 100, 0xFFFFFFFF);
        String st = Bot.running ? "§aBot działa: " + Bot.status : "§7Bot wyłączony";
        g.drawCenteredString(this.font, st, this.width / 2, this.height / 2 - 88, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
