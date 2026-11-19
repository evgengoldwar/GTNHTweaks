package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.renderer.ItemRenderer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.minecraft.client.ItemSwitchMotion;

/** Keeps the vanilla and Forge item renderers, including their swing, use and multipass rendering. */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer_ItemSwitching {

    // Per renderer, so Backhand's second ItemRenderer has its own animation and matrix scope.
    @Unique
    private float gtnhtweaks$switchAmount;
    @Unique
    private boolean gtnhtweaks$switchMatrix;

    @Unique
    private static boolean gtnhtweaks$animateSwitch() {
        return MinecraftModule.itemSwitching && MinecraftModule.itemSwitchStrength > 0;
    }

    @ModifyConstant(method = "updateEquippedItem", constant = @Constant(floatValue = 0.4F), require = 1)
    private float gtnhtweaks$equipSpeed(float vanilla) {
        return gtnhtweaks$animateSwitch() ? ItemSwitchMotion.step(MinecraftModule.itemSwitchSpeed) : vanilla;
    }

    /** Local 2 is the interpolated equip progress, immediately after the partialTicks argument. */
    @ModifyVariable(
        method = "renderItemInFirstPerson",
        at = @At(value = "STORE", ordinal = 0),
        index = 2,
        require = 1)
    private float gtnhtweaks$easeEquip(float progress) {
        if (!gtnhtweaks$animateSwitch()) {
            gtnhtweaks$switchAmount = 0F;
            return progress;
        }
        float eased = ItemSwitchMotion.ease(progress);
        gtnhtweaks$switchAmount = (1F - eased) * MinecraftModule.itemSwitchStrength / 100F;
        return eased;
    }

    /** After camera lag, before any map, item or empty-hand branch; no dependency on GL call redirects. */
    @Inject(
        method = "renderItemInFirstPerson",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;itemToRender:Lnet/minecraft/item/ItemStack;",
            ordinal = 0),
        require = 1)
    private void gtnhtweaks$beginSwitch(float partialTicks, CallbackInfo ci) {
        gtnhtweaks$switchMatrix = gtnhtweaks$switchAmount > 0F;
        if (!gtnhtweaks$switchMatrix) return;
        float amount = gtnhtweaks$switchAmount;
        GL11.glPushMatrix();
        GL11.glTranslatef(-0.18F * amount, -0.08F * amount, 0.06F * amount);
        GL11.glRotatef(-18F * amount, 0F, 0F, 1F);
        GL11.glRotatef(-12F * amount, 0F, 1F, 0F);
    }

    /** Restore before RETURN hooks such as Backhand render the other hand. */
    @Inject(
        method = "renderItemInFirstPerson",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderHelper;disableStandardItemLighting()V"),
        require = 1)
    private void gtnhtweaks$endSwitch(float partialTicks, CallbackInfo ci) {
        if (!gtnhtweaks$switchMatrix) return;
        GL11.glPopMatrix();
        gtnhtweaks$switchMatrix = false;
    }
}
