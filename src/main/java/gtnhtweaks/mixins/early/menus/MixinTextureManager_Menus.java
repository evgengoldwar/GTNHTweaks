package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import gtnhtweaks.modules.menus.MenuStyle;

/**
 * Records the textures an inventory background uses, see MenuStyle.beginInventoryBackground, and draws the inventory
 * texture of a resource pack for the inventory of Hodgepodge, see PackInventoryTexture.
 */
@Mixin(TextureManager.class)
public class MixinTextureManager_Menus {

    @ModifyVariable(method = "bindTexture", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation gtnhtweaks$recordTexture(ResourceLocation texture) {
        return MenuStyle.onBindTexture(texture);
    }
}
