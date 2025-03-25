package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class GrimFastFall extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> amountPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("Amount Per Tick")
        .description("Amount of packets to send per tick.")
        .defaultValue(3)
        .min(1)
        .sliderMax(20)
        .build()
    );


    public GrimFastFall() {
        super(Addon.CATEGORY, "grim-fast-fall", "Makes you fall very quickly and with mostly no fall damage.");
    }

    @Override
    public void onActivate()
    {
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        HitResult hitResult = mc.getCameraEntity().raycast(5, 0, false);
        for (int i = 0; i < amountPerTick.get(); i++) {
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, (BlockHitResult) hitResult, mc.player.currentScreenHandler.getRevision()));
        }
    }
}
