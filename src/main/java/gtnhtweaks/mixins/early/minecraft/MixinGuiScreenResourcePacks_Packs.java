package gtnhtweaks.mixins.early.minecraft;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.PackScreenHooks;
import gtnhtweaks.modules.minecraft.client.ResourcePackScreen;

/** The resource pack screen: dragging the packs with the mouse, the presets and the quick Done. */
@Mixin(GuiScreenResourcePacks.class)
public abstract class MixinGuiScreenResourcePacks_Packs extends GuiScreen implements PackScreenHooks {

    @Inject(method = "initGui", at = @At("TAIL"), require = 0)
    private void gtnhtweaks$initGui(CallbackInfo ci) {
        ResourcePackScreen.init(this, buttonList);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$mouseClicked(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (ResourcePackScreen.mouseClicked(this, mouseX, mouseY, button)) ci.cancel();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$actionPerformed(GuiButton button, CallbackInfo ci) {
        if (ResourcePackScreen.actionPerformed(this, button)) ci.cancel();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), require = 0)
    private void gtnhtweaks$drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        List<String> tooltip = ResourcePackScreen.draw(this, mouseX, mouseY);
        if (tooltip != null) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    /** Not overridden by the screen: added for the name of a preset being edited and the keys of the presets. */
    @Override
    protected void keyTyped(char c, int code) {
        if (!ResourcePackScreen.keyTyped(this, c, code)) super.keyTyped(c, code);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ResourcePackScreen.update(this);
    }

    @Override
    public void gtnhtweaks$applyPacks() {
        for (Object element : buttonList) {
            GuiButton button = (GuiButton) element;
            // the vanilla Done button
            if (button.id == 1) {
                actionPerformed(button);
                return;
            }
        }
    }
}
