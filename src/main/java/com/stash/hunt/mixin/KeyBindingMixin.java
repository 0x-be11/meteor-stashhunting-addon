package com.stash.hunt.mixin;

import com.stash.hunt.modules.ElytraFlyPlusPlus;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {

    @Final
    @Shadow
    private String translationKey;

    @Unique
    Module elytraFlyPlusPlus = null;

    @Inject(at = @At("RETURN"), method = "isPressed", cancellable = true)
    public void isPressed(CallbackInfoReturnable<Boolean> cir)
    {
        // setting it beforehand caused a crash because meteor wasnt loaded yet
        elytraFlyPlusPlus = elytraFlyPlusPlus == null ? Modules.get().get(ElytraFlyPlusPlus.class) : elytraFlyPlusPlus;
        if (elytraFlyPlusPlus != null && elytraFlyPlusPlus.isActive() && Modules.get().get(ElytraFlyPlusPlus.class).enabled() && translationKey.equals("key.forward"))
        {
            cir.setReturnValue(true);
        }
    }
}
