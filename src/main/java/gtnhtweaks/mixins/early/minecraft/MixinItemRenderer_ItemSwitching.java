package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.renderer.ItemRenderer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow
    private float equippedProgress;
    @Shadow
    private float prevEquippedProgress;

    // Per renderer, so Backhand's second ItemRenderer has its own animation and matrix scope.
    @Unique
    private final ItemSwitchMotion.Pose gtnhtweaks$switchPose = new ItemSwitchMotion.Pose();
    @Unique
    private final ItemSwitchMotion.Transition gtnhtweaks$switchTransition = new ItemSwitchMotion.Transition();
    @Unique
    private boolean gtnhtweaks$switchActive;
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

    @Inject(method = "updateEquippedItem", at = @At("RETURN"), require = 1)
    private void gtnhtweaks$trackSwitch(CallbackInfo ci) {
        if (gtnhtweaks$animateSwitch()) {
            gtnhtweaks$switchTransition.tick(prevEquippedProgress, equippedProgress);
        } else {
            gtnhtweaks$switchTransition.reset();
        }
    }

    /** Local 2 is the interpolated equip progress, immediately after the partialTicks argument. */
    @ModifyVariable(
        method = "renderItemInFirstPerson",
        at = @At(value = "STORE", ordinal = 0),
        index = 2,
        require = 1)
    private float gtnhtweaks$replaceEquip(float progress, float partialTicks) {
        gtnhtweaks$switchActive = false;
        if (!gtnhtweaks$animateSwitch()) return progress;
        gtnhtweaks$switchTransition
            .sample(progress, partialTicks, MinecraftModule.itemSwitchStrength, gtnhtweaks$switchPose);
        gtnhtweaks$switchActive = !gtnhtweaks$switchPose.isRest();
        // Suppress vanilla's vertical lowering in all three branches: item, empty hand and map.
        return 1F;
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
        gtnhtweaks$switchMatrix = gtnhtweaks$switchActive;
        if (!gtnhtweaks$switchMatrix) return;
        ItemSwitchMotion.Pose pose = gtnhtweaks$switchPose;
        GL11.glPushMatrix();
        GL11.glTranslatef(pose.x, pose.y, pose.z);
        // Turn around the grip at vanilla's held-item origin, not around the camera.
        GL11.glTranslatef(0.56F, -0.52F, -0.72F);
        GL11.glRotatef(pose.roll, 0F, 0F, 1F);
        GL11.glRotatef(pose.yaw, 0F, 1F, 0F);
        GL11.glRotatef(pose.pitch, 1F, 0F, 0F);
        GL11.glTranslatef(-0.56F, 0.52F, 0.72F);
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
