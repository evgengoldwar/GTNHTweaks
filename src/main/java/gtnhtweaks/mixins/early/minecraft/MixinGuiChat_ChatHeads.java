package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import gtnhtweaks.modules.minecraft.client.ChatHeads;

/** The text under the mouse, for hover and click, found where it is drawn: after the head. */
@Mixin(GuiChat.class)
public class MixinGuiChat_ChatHeads {

    @ModifyArg(
        method = { "drawScreen", "mouseClicked" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiNewChat;func_146236_a(II)Lnet/minecraft/util/IChatComponent;"),
        index = 0,
        require = 0)
    private int gtnhtweaks$mouseX(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        float chatScale = mc.gameSettings.chatScale;
        // as GuiNewChat finds the row: 27 GUI pixels above the bottom, rows of the font height
        int row = (int) Math.floor((mouseY / scale - 27) / chatScale) / mc.fontRenderer.FONT_HEIGHT;
        if (!ChatHeads.shiftedRow(row)) return mouseX;
        return mouseX - Math.round(ChatHeads.SPACE * chatScale * scale);
    }
}
