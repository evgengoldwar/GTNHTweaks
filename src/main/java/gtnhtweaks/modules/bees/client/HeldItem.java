package gtnhtweaks.modules.bees.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

/** The item a bee carries, its comb or its specialty, drawn as a small item with thickness, like a dropped item. */
final class HeldItem {

    private HeldItem() {}

    /**
     * Draws the item one unit wide, centered on the origin, facing +z. Leaves GL_TEXTURE_2D disabled and the items
     * texture bound, as the bee is drawn.
     */
    static void draw(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null) return;
        TextureManager textures = Minecraft.getMinecraft()
            .getTextureManager();
        textures.bindTexture(textures.getResourceLocation(stack.getItemSpriteNumber()));
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(stack.getItemDamage()) : 1;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glPushMatrix();
        GL11.glTranslatef(-0.5F, -0.5F, 0.03F);
        for (int pass = 0; pass < passes; pass++) {
            IIcon icon = item.getIcon(stack, pass);
            if (icon == null) continue;
            int rgb = item.getColorFromItemStack(stack, pass);
            GL11.glColor4f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, 1);
            ItemRenderer.renderItemIn2D(
                Tessellator.instance,
                icon.getMaxU(),
                icon.getMinV(),
                icon.getMinU(),
                icon.getMaxV(),
                icon.getIconWidth(),
                icon.getIconHeight(),
                0.0625F);
        }
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        textures.bindTexture(TextureMap.locationItemsTexture);
    }
}
