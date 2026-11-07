package dev.botx;

import net.minecraft.client.Minecraft;

/** Jedno zadanie bota. tick() zwraca true gdy zadanie jest skończone. */
public interface Task {
    boolean tick(Minecraft mc);

    String label();

    /** Wywoływane gdy bot długo stoi w miejscu bez postępu - zadanie powinno zmienić podejście. */
    default void onStuck() {
    }
}
