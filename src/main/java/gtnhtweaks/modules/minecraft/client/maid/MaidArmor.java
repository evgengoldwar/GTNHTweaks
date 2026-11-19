package gtnhtweaks.modules.minecraft.client.maid;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import gtnhtweaks.modules.minecraft.MinecraftModule;

/** Client-only appearance lookup. Never writes to an inventory, item stack, or NBT. */
public final class MaidArmor {

    private static final MaidArmorModel[][] MODELS = new MaidArmorModel[2][4];
    private static final ResourceLocation[] TEXTURES = {
        new ResourceLocation("gtnhtweaks", "textures/model/maid/armor_combatmaid.png"),
        new ResourceLocation("gtnhtweaks", "textures/model/maid/armor_cosmeticmaid.png") };

    private MaidArmor() {}

    public static boolean matches(ItemStack stack, int slot) {
        return MinecraftModule.maidArmor && slot >= 0
            && slot < 4
            && stack != null
            && stack.getItem() instanceof ItemArmor
            && stack.hasDisplayName()
            && "Maid".equalsIgnoreCase(
                EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName())
                    .trim());
    }

    public static ResourceLocation texture() {
        return TEXTURES[MinecraftModule.maidStyle.ordinal()];
    }

    public static ModelBiped model(int slot, ModelBiped player) {
        int style = MinecraftModule.maidStyle.ordinal();
        MaidArmorModel model = MODELS[style][slot];
        if (model == null) {
            model = style == 0 ? new ModelArmorCombatMaid(slot) : new ModelArmorCosmeticMaid(slot);
            MODELS[style][slot] = model;
        }
        // Copy every transient pose flag: cached models must not retain the previous player's pose.
        model.onGround = player.onGround;
        model.isRiding = player.isRiding;
        model.isChild = player.isChild;
        model.isSneak = player.isSneak;
        model.heldItemLeft = player.heldItemLeft;
        model.heldItemRight = player.heldItemRight;
        model.aimedBow = player.aimedBow;
        model.usePlayerPose(player);
        return model;
    }
}
