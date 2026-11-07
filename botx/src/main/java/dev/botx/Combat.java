package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Walka: obrona przed potworami i atak na wskazany cel. */
public final class Combat {
    private static final Item[] SWORDS = {
            Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD
    };

    public static void equipWeapon(Minecraft mc) {
        for (Item it : SWORDS) {
            if (Bot.count(s -> s.is(it)) > 0) {
                Bot.equip(mc, s -> s.is(it));
                return;
            }
        }
    }

    /** Celuje w cel i bije go gdy jest w zasięgu. Zwraca true jeśli cel jest w zasięgu ataku. */
    public static boolean attack(Minecraft mc, LivingEntity target) {
        LocalPlayer p = mc.player;
        Bot.look(p, target.position().add(0, target.getBbHeight() * 0.6, 0));
        if (p.distanceTo(target) > 3.0f) return false;
        equipWeapon(mc);
        Bot.activity();
        if (p.getAttackStrengthScale(0.5f) >= 1.0f) {
            mc.gameMode.attack(p, target);
            p.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    /** Jeśli potwór jest tuż obok - walczy z nim zamiast wykonywać zadanie. Zwraca true gdy walczy. */
    public static boolean defend(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.containerMenu != p.inventoryMenu) return false;
        List<Monster> list = mc.level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(4.0), Monster::isAlive);
        Monster best = null;
        double bestD = Double.MAX_VALUE;
        for (Monster m : list) {
            double d = p.distanceToSqr(m);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best == null) return false;
        return attack(mc, best);
    }

    private Combat() {
    }
}
