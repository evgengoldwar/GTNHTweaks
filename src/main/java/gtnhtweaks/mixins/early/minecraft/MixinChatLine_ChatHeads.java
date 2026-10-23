package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ChatHeadLine;
import gtnhtweaks.modules.minecraft.client.ChatHeads;

/** Lines of the chat know the writer of their message, or that it has none, set while GuiNewChat splits it. */
@Mixin(ChatLine.class)
public class MixinChatLine_ChatHeads implements ChatHeadLine {

    @Unique
    private String gtnhtweaks$writer;
    @Unique
    private int gtnhtweaks$kind;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtnhtweaks$made(int updateCounter, IChatComponent component, int id, CallbackInfo ci) {
        ChatHeads.lineMade(this);
    }

    @Override
    public void gtnhtweaks$setWriter(String writer, int kind) {
        gtnhtweaks$writer = writer;
        gtnhtweaks$kind = kind;
    }

    @Override
    public String gtnhtweaks$getWriter() {
        return gtnhtweaks$writer;
    }

    @Override
    public int gtnhtweaks$getKind() {
        return gtnhtweaks$kind;
    }
}
