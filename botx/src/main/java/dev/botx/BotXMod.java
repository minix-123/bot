package dev.botx;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public class BotXMod implements ClientModInitializer {
    public static KeyMapping OPEN_KEY;

    @Override
    public void onInitializeClient() {
        // UWAGA 1.21.11: ResourceLocation zmieniło nazwę na Identifier (tu i w ItemPickScreen).
        KeyMapping.Category category =
                KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("botx", "main"));

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.botx.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new BotXScreen());
                }
            }
            Bot.tick(mc);
        });
    }
}
