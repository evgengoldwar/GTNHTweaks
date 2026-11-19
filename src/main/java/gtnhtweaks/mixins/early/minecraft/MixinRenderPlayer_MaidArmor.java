package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.client.maid.MaidArmor;

/** Replaces one worn armor render pass; the real stack and its gameplay hooks are never replaced. */
@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer_MaidArmor {

    @Shadow
    public ModelBiped modelBipedMain;

    @Shadow
    protected abstract void setRenderPassModel(ModelBase model);

    @Inject(
        method = "shouldRenderPass(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void gtnhtweaks$maidArmor(AbstractClientPlayer player, int slot, float partialTicks,
        CallbackInfoReturnable<Integer> cir) {
        if (slot < 0 || slot > 3) return;
        ItemStack stack = player.inventory.armorItemInSlot(3 - slot);
        if (!MaidArmor.matches(stack, slot)) return;
        Minecraft.getMinecraft().getTextureManager()
            .bindTexture(MaidArmor.texture());
        setRenderPassModel(MaidArmor.model(slot, modelBipedMain));
        GL11.glColor4f(1F, 1F, 1F, 1F);
        // No leather tint/overlay on the outfit. Keep the enchantment glint of the original piece.
        cir.setReturnValue(stack.isItemEnchanted() ? 15 : 1);
    }
}
