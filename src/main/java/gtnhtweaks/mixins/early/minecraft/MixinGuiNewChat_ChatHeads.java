package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ChatHeadLine;
import gtnhtweaks.modules.minecraft.client.ChatHeads;

/**
 * Heads of the players before their messages: a message with a writer is wrapped narrower, its lines are drawn moved
 * to the right, and its first line with the head.
 */
@Mixin(GuiNewChat.class)
public class MixinGuiNewChat_ChatHeads {

    /** The line being drawn. */
    @Unique
    private ChatLine gtnhtweaks$line;

    // ---- splitting a message into lines (func_146237_a: message, id, update counter, refreshing)

    /** Finds the writer, and the message to split: without the name of the writer when the names are hidden. */
    @ModifyVariable(method = "func_146237_a", at = @At("HEAD"), argsOnly = true)
    private IChatComponent gtnhtweaks$beginMessage(IChatComponent message) {
        return ChatHeads.beginMessage(message);
    }

    @Inject(method = "func_146237_a", at = @At("RETURN"))
    private void gtnhtweaks$endMessage(IChatComponent message, int id, int updateCounter, boolean refresh,
        CallbackInfo ci) {
        ChatHeads.endMessage();
    }

    /** The width the lines are wrapped to: the chat width divided by the chat scale, the first floor of the method. */
    @ModifyArg(
        method = "func_146237_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;floor_float(F)I", ordinal = 0))
    private float gtnhtweaks$wrapWidth(float width) {
        return ChatHeads.wrapWidth(width);
    }

    // ---- drawing

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void gtnhtweaks$beginFrame(int updateCounter, CallbackInfo ci) {
        ChatHeads.beginFrame();
        gtnhtweaks$line = null;
    }

    @Redirect(
        method = "drawChat",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/ChatLine;func_151461_a()Lnet/minecraft/util/IChatComponent;"))
    private IChatComponent gtnhtweaks$lineText(ChatLine line) {
        gtnhtweaks$line = line;
        return line.func_151461_a();
    }

    /** The text of a line, at y = -row * 9 - 8: the head drawn before it, the text moved after it. */
    @ModifyArg(
        method = "drawChat",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I"),
        index = 1)
    private int gtnhtweaks$drawLine(String text, int x, int y, int color) {
        ChatLine line = gtnhtweaks$line;
        gtnhtweaks$line = null;
        return ChatHeads.drawLine((ChatHeadLine) line, x, y, color, -(y + 8) / 9);
    }
}
