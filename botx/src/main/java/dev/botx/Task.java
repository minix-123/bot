package dev.botx;

import net.minecraft.client.Minecraft;

/** Jedno zadanie bota. tick() zwraca true gdy zadanie jest skończone. */
public interface Task {
    boolean tick(Minecraft mc);

    String label();
}
