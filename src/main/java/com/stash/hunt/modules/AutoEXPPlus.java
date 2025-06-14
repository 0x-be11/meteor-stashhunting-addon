/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Hand;

public class AutoEXPPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which items to repair.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> replenish = sgGeneral.add(new BoolSetting.Builder()
        .name("replenish")
        .description("Automatically replenishes exp into a selected hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> slot = sgGeneral.add(new IntSetting.Builder()
        .name("exp-slot")
        .description("The slot to replenish exp into.")
        .visible(replenish::get)
        .defaultValue(6)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> minThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("min-threshold")
        .description("The minimum durability percentage that an item needs to fall to, to be repaired.")
        .defaultValue(30)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> maxThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("max-threshold")
        .description("The maximum durability percentage to repair items to.")
        .defaultValue(80)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> ignoreElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-elytra")
        .description("Ignore elytra when repairing.")
        .defaultValue(false)
        .build()
    );

    private int repairingI;

    public AutoEXPPlus() {
        super(Addon.CATEGORY, "auto-exp-plus", "Automatically repairs your armor and tools in pvp.");
    }

    @Override
    public void onActivate() {
        repairingI = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (repairingI == -1) {
            if (mode.get() != Mode.Hands) {
                for (int i = 0; i < 4/*mc.player.getInventory().armor.size()*/; i++) {
                    EquipmentSlot slot = null;
                    if (i == 0) { slot = EquipmentSlot.HEAD; }
                    else if (i == 1) { slot = EquipmentSlot.CHEST; }
                    else if (i == 2) { slot = EquipmentSlot.LEGS; }
                    else if (i == 3) { slot = EquipmentSlot.FEET; }
                    //if (ignoreElytra.get() && mc.player.getInventory().armor.get(i).getItem() == Items.ELYTRA) continue;
                    if (ignoreElytra.get() && mc.player.getEquippedStack(slot).getItem() == Items.ELYTRA) continue;
                    //if (needsRepair(mc.player.getInventory().armor.get(i), minThreshold.get())) {
                    if (needsRepair(mc.player.getEquippedStack(slot), minThreshold.get())) {
                        repairingI = SlotUtils.ARMOR_START + i;
                        break;
                    }
                }
            }

            if (mode.get() != Mode.Armor && repairingI == -1) {
                for (Hand hand : Hand.values()) {
                    if (needsRepair(mc.player.getStackInHand(hand), minThreshold.get())) {
                        repairingI = hand == Hand.MAIN_HAND ? mc.player.getInventory().getSelectedSlot() : SlotUtils.OFFHAND;
                        break;
                    }
                }
            }
        }

        if (repairingI != -1) {
            if (!needsRepair(mc.player.getInventory().getStack(repairingI), maxThreshold.get())) {
                repairingI = -1;
                return;
            }

            FindItemResult exp = InvUtils.find(Items.EXPERIENCE_BOTTLE);

            if (exp.found()) {
                if (!exp.isHotbar() && !exp.isOffhand()) {
                    if (!replenish.get()) return;
                    InvUtils.move().from(exp.slot()).toHotbar(slot.get() - 1);
                }

                Rotations.rotate(mc.player.getYaw(), 90, () -> {
                    if (exp.getHand() != null) {
                        mc.interactionManager.interactItem(mc.player, exp.getHand());
                    }
                    else {
                        InvUtils.swap(exp.slot(), true);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        InvUtils.swapBack();
                    }
                });
            }
        }
    }

    private boolean needsRepair(ItemStack itemStack, double threshold) {
        if (itemStack.isEmpty() || !Utils.hasEnchantments(itemStack, Enchantments.MENDING)) return false;
        return (itemStack.getMaxDamage() - itemStack.getDamage()) / (double) itemStack.getMaxDamage() * 100 <= threshold;
    }

    public enum Mode {
        Armor,
        Hands,
        Both
    }
}
