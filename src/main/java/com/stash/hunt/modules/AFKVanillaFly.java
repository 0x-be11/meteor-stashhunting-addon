package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import com.stash.hunt.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.item.Items;

public class AFKVanillaFly extends Module {
    private long lastRocketUse = 0;
    private boolean launched = false;
    private double yTarget = -1;
    private float targetPitch = 0;

    public AFKVanillaFly() {
        super(Addon.CATEGORY, "AFKVanillaFly", "Maintains a level Y-flight with fireworks and smooth pitch control.");
    }

    @Override
    public void onActivate() {
        launched = false;
        yTarget = -1;

        if (mc.player == null || !mc.player.isFallFlying()) {
            info("You must be flying before enabling AFKVanillaFly.");
        }
    }

    // this method is now then default logic, it did not need to be called in TrailFollower
    public void tickFlyLogic() {
        if (mc.player == null) return;

        double currentY = mc.player.getY();

        if (mc.player.isFallFlying()) {
            if (yTarget == -1 || !launched) {
                yTarget = currentY;
                launched = true;
            }

            double yDiff = currentY - yTarget;

            if (Math.abs(yDiff) > 10.0) {
                targetPitch = (float) (-Math.atan2(yDiff, 100) * (180 / Math.PI));
            } else if (yDiff > 2.0) {
                targetPitch = 10f;
            } else if (yDiff < -2.0) {
                targetPitch = -10f;
            } else {
                targetPitch = 0f;
            }

            float currentPitch = mc.player.getPitch();
            float pitchDiff = targetPitch - currentPitch;
            mc.player.setPitch(currentPitch + pitchDiff * 0.1f);

            if (System.currentTimeMillis() - lastRocketUse > 3000) {
                tryUseFirework();
            }
        } else {

            if (!launched) {
                mc.player.jump();
                launched = true;
            } else if (System.currentTimeMillis() - lastRocketUse > 1000) {
                tryUseFirework();
            }

            yTarget = -1;
        }
    }

    public void resetYLock() {
        yTarget = -1;
        launched = false;
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickFlyLogic();
    }


    private void tryUseFirework() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (inv.found()) {
                int hotbarSlot = findEmptyHotbarSlot();
                if (hotbarSlot != -1) {
                    InvUtils.move().from(inv.slot()).to(hotbarSlot);
                } else {
                    info("No empty hotbar slot available to move fireworks.");
                    return;
                }
            } else {
                info("No fireworks found in hotbar or inventory.");
                return;
            }
        }
        Utils.firework(mc, true);
        lastRocketUse = System.currentTimeMillis();
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }
}
