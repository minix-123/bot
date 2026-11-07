package dev.botx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Przetrwanie: jedzenie, polowanie na jedzenie, oddychanie, porzucanie śmieci. */
public final class Survival {
    private static final Item[] JUNK = {
            Items.GRAVEL, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.FLINT,
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO
    };

    private static int huntCooldown;
    private static int huntTicks;
    private static int pickupTimer;
    private static int equipTries;
    private static int junkTimer;
    private static LivingEntity huntTarget;
    private static int chase;

    public static void reset() {
        huntCooldown = 0;
        huntTicks = 0;
        pickupTimer = 0;
        equipTries = 0;
        huntTarget = null;
    }

    static boolean edible(ItemStack s) {
        return s.has(DataComponents.FOOD)
                && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE) && !s.is(Items.POISONOUS_POTATO)
                && !s.is(Items.PUFFERFISH) && !s.is(Items.CHORUS_FRUIT)
                && !s.is(Items.GOLDEN_APPLE) && !s.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private static boolean isFoodAnimal(LivingEntity e) {
        EntityType<?> t = e.getType();
        return t == EntityType.COW || t == EntityType.PIG || t == EntityType.SHEEP || t == EntityType.CHICKEN;
    }

    /** Zwraca true gdy przejęło sterowanie w tym ticku (bot nie wykonuje wtedy zadania). */
    public static boolean tick(Minecraft mc) {
        LocalPlayer p = mc.player;

        // tonięcie - wynurz się
        if (p.isEyeInFluid(FluidTags.WATER) && p.getAirSupply() < p.getMaxAirSupply() / 3) {
            mc.options.keyJump.setDown(true);
            Bot.activity();
            return true;
        }
        if (p.containerMenu != p.inventoryMenu) return false;

        if (huntCooldown > 0) huntCooldown--;
        if (--junkTimer <= 0) {
            junkTimer = 100;
            cleanup(mc);
        }

        // jedzenie
        int food = p.getFoodData().getFoodLevel();
        if (p.isUsingItem() && edible(p.getUseItem())) {
            mc.options.keyUse.setDown(true);
            Bot.activity();
            return true;
        }
        if (food <= 14 && Bot.count(Survival::edible) > 0) {
            if (!edible(p.getMainHandItem())) {
                if (++equipTries <= 20) {
                    Bot.equip(mc, Survival::edible);
                    return true;
                }
            } else {
                equipTries = 0;
                mc.options.keyUse.setDown(true);
                Bot.activity();
                return true;
            }
        }

        // zbieranie mięsa po upolowanym zwierzęciu
        if (pickupTimer > 0) {
            pickupTimer--;
            ItemEntity ie = nearestItem(mc);
            if (ie != null) {
                Nav.goTo(Nav.touch(ie.position(), 1.0, "meat"));
                Nav.tick(mc);
                if (Nav.failed) {
                    Nav.reset();
                    pickupTimer = 0;
                }
                Bot.activity();
                return true;
            }
            pickupTimer = 0;
        }

        // głód i brak jedzenia - poluj
        if (food <= 12 && huntCooldown <= 0 && Bot.count(Survival::edible) == 0) {
            return hunt(mc);
        }
        huntTarget = null;
        return false;
    }

    private static boolean hunt(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (huntTarget != null && !huntTarget.isAlive()) {
            huntTarget = null;
            pickupTimer = 60;
            Nav.reset();
            return true;
        }
        if (huntTarget == null) {
            List<LivingEntity> list = mc.level.getEntitiesOfClass(LivingEntity.class,
                    p.getBoundingBox().inflate(24.0), e -> e.isAlive() && isFoodAnimal(e));
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : list) {
                double d = p.distanceToSqr(e);
                if (d < bd) {
                    bd = d;
                    huntTarget = e;
                }
            }
            if (huntTarget == null) {
                huntCooldown = 400;          // nic w pobliżu - spróbuj później
                return false;
            }
            huntTicks = 0;
            Bot.msg("§e[Bot X] Głodny - poluję na jedzenie.");
        }
        if (++huntTicks > 1500) {
            huntTarget = null;
            huntCooldown = 6000;
            Nav.reset();
            return false;
        }
        if (p.distanceTo(huntTarget) > 2.8f) {
            Nav.goTo(Nav.touch(huntTarget.position(), 2.0, "hunt"));
            if (++chase % 12 == 0) Nav.invalidate();
            Nav.tick(mc);
            if (Nav.failed) {
                Nav.reset();
                huntTarget = null;
                huntCooldown = 1200;
                return false;
            }
        }
        Combat.attack(mc, huntTarget);
        Bot.activity();
        return true;
    }

    private static ItemEntity nearestItem(Minecraft mc) {
        LocalPlayer p = mc.player;
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8.0))) {
            double d = p.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean isJunk(ItemStack st) {
        for (Item it : JUNK) if (st.is(it)) return true;
        return st.is(Items.DIRT) && Bot.count(s -> s.is(Items.DIRT)) > 64;
    }

    /** Gdy kończy się miejsce w ekwipunku - wyrzuca śmieci. */
    private static void cleanup(Minecraft mc) {
        LocalPlayer p = mc.player;
        Inventory inv = p.getInventory();
        int empty = 0;
        for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) empty++;
        if (empty > 3) return;
        AbstractContainerMenu m = p.inventoryMenu;
        for (int i = 0; i < m.slots.size(); i++) {
            Slot s = m.slots.get(i);
            if (s.container != inv || s.getContainerSlot() >= 36) continue;
            ItemStack st = s.getItem();
            if (!st.isEmpty() && isJunk(st)) {
                Bot.click(mc, m, i, 1, ClickType.THROW);
                return;
            }
        }
    }

    private Survival() {
    }
}
