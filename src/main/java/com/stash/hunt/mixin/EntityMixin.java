package com.stash.hunt.mixin;

import com.stash.hunt.modules.ElytraFlyPlusPlus;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Entity.class)
public class EntityMixin
{
    @Shadow
    protected UUID uuid;

    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/entity/Entity;getPose()Lnet/minecraft/entity/EntityPose;", cancellable = true)
    private void getPose(CallbackInfoReturnable<EntityPose> cir)
    {
        if (Modules.get().get(ElytraFlyPlusPlus.class).enabled() && this.uuid == mc.player.getUuid())
        {
            cir.setReturnValue(EntityPose.STANDING);
        }
    }

    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/entity/Entity;isSprinting()Z", cancellable = true)
    private void isSprinting(CallbackInfoReturnable<Boolean> cir)
    {
        if (Modules.get().get(ElytraFlyPlusPlus.class).enabled() && this.uuid == mc.player.getUuid())
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(at = @At("RETURN"), method = "adjustMovementForCollisions", cancellable = true)
    private void adjustMovementForCollisions(Vec3d movement, CallbackInfoReturnable<Vec3d> cir)
    {
        if (mc.player != null && this.uuid == mc.player.getUuid() && Modules.get().get(ElytraFlyPlusPlus.class).enabled() &&
            (Boolean)Modules.get().get(ElytraFlyPlusPlus.class).settings.get("fake-head-collision").get())
        {
            Vec3d returnValue = cir.getReturnValue();
            if (Math.abs(returnValue.getY() - 0.42) < 0.1)
            {
                cir.setReturnValue(new Vec3d(returnValue.getX(), 0.2, returnValue.getZ()));
            }
        }
    }
}
