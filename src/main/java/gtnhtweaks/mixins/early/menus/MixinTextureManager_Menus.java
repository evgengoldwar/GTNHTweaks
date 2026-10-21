package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/** Records the textures an inventory background uses, see MenuStyle.beginInventoryBackground. */
@Mixin(TextureManager.class)
public class MixinTextureManager_Menus {

    @Inject(method = "bindTexture", at = @At("HEAD"))
    private void gtnhtweaks$recordTexture(ResourceLocation texture, CallbackInfo ci) {
        MenuStyle.onBindTexture(texture);
    }
}
