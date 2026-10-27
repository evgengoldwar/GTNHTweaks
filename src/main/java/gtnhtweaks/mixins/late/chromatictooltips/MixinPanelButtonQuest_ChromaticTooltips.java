package gtnhtweaks.mixins.late.chromatictooltips;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import gtnhtweaks.modules.chromatictooltips.QuestIcons;

/** Remembers the icon of the quest whose tooltip is shown, to color the tooltip by it. */
@Mixin(value = PanelButtonQuest.class, remap = false)
public class MixinPanelButtonQuest_ChromaticTooltips {

    @Inject(method = "getTooltip(II)Ljava/util/List;", at = @At("RETURN"))
    private void gtnhtweaks$rememberQuestIcon(int mouseX, int mouseY, CallbackInfoReturnable<List<String>> cir) {
        QuestIcons.onQuestTooltip(this, cir.getReturnValue());
    }
}
