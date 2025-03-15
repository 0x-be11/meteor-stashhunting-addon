package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PickItemFromEntityC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class AutoLogPlus extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> logOnY = sgGeneral.add(new BoolSetting.Builder()
        .name("Log on Y")
        .description("Logs out if you are below a certain Y level.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> yLevel = sgGeneral.add(new DoubleSetting.Builder()
        .name("Auto Log out if below this Y")
        .defaultValue(256)
        .min(-128)
        .sliderRange(-128, 320)
        .visible(logOnY::get)
        .build()
    );

    private final Setting<Boolean> logArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("Log Armor")
        .description("Logs out if you have no armor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> armorPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("Auto Log out if armor is below this percent")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 100)
        .visible(logArmor::get)
        .build()
    );

    private final Setting<Boolean> logPosition = sgGeneral.add(new BoolSetting.Builder()
        .name("Log Position")
        .description("Logs out if you are within x blocks of this position. Y Position is not included")
        .defaultValue(false)
        .build()
    );

    private final Setting<BlockPos> position = sgGeneral.add(new BlockPosSetting.Builder()
        .name("Position")
        .description("The position to log out at. Y position is ignored.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(logPosition::get)
        .build()
    );

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("Distance")
        .description("The distance from the position to log out at.")
        .defaultValue(100)
        .sliderRange(0, 1000)
        .visible(logPosition::get)
        .build()
    );

    private final Setting<Boolean> illegalDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("Illegal Disconnect")
        .description("Disconnects from the server using the slot method.")
        .defaultValue(false)
        .build()
    );

    public AutoLogPlus()
    {
        super(Addon.CATEGORY, "auto-log-plus", "Provides some additional triggers to log out.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        // If in the 2b2t queue
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;
        if (logOnY.get() && mc.player.getY() < yLevel.get())
        {
            logOut("Player was at Y=" + mc.player.getY() + " which is below your limit of Y=" + yLevel.get());
            return;
        }
        if (logArmor.get())
        {
            for (int i = 0; i < 4; i++)
            {
                ItemStack armorPiece = mc.player.getInventory().getArmorStack(i);
                if (armorPiece.isDamageable())
                {
                    int max = armorPiece.getMaxDamage();
                    int current = armorPiece.getDamage();
                    double percentUndamaged = 100 - ((double) current / max) * 100;
                    if (percentUndamaged < armorPercent.get())
                    {
                        logOut("You had low armor");
                    }
                    return;
                }
            }
        }
        if (logPosition.get())
        {
//            info("log position check");
            double distanceToTarget = mc.player.getPos().multiply(1,0,1).distanceTo(position.get().toCenterPos().multiply(1,0,1));
//            info("distance to target: " + distanceToTarget);
            if (distanceToTarget < distance.get())
            {
//                info("would be logging out");
                logOut("Player was within " + distanceToTarget + " blocks of the target position.");
                return;
            }
        }
    }

    private void logOut(String reason)
    {
        if (mc.player == null) return;
        if (Modules.get().get(AutoReconnect.class).isActive())
        {
            Modules.get().get(AutoReconnect.class).toggle();
        }
        if (illegalDisconnect.get())
        {
            mc.player.networkHandler.sendPacket(new PickItemFromEntityC2SPacket(-1, true));
        }
        else
        {
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[AutoLogPlus] " + reason)));
        }
    }
}
