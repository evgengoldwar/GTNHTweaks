package gtnhtweaks.mixins.late.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.nei.NEIModule;

/**
 * Smooth movement in the crafting tree of the NEI bookmarks.
 * <p>
 * Dragging: NEI gets the mouse in whole GUI pixels, so the tree follows the mouse in steps of the GUI scale. The
 * exact mouse position is read from the mouse instead.
 * <p>
 * Wheel: NEI moves the camera at once. The new position becomes a target and the drawn camera slides to it, one step
 * per frame from {@code clampToBounds}, which NEI calls before drawing the tree. The point under the mouse stays in
 * place while zooming. Any other change of the camera, like dragging or jumping to a search result, ends the slide.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.bookmark.tree.Camera", remap = false)
public class MixinCamera_NEI {

    @Shadow
    public int widgetX;
    @Shadow
    public int widgetY;
    @Shadow
    public int widgetW;
    @Shadow
    public int widgetH;
    @Shadow
    public float scrollX;
    @Shadow
    public float scrollY;
    @Shadow
    public float zoomLevel;
    @Shadow
    private float lastDragX;
    @Shadow
    private float lastDragY;
    @Shadow
    private boolean dragging;

    @Unique
    private boolean gtnhtweaks$sliding;
    @Unique
    private float gtnhtweaks$targetX, gtnhtweaks$targetY, gtnhtweaks$targetZoom;
    /** Point of the widget that stays in place while sliding, the mouse of the last zoom. */
    @Unique
    private float gtnhtweaks$anchorX, gtnhtweaks$anchorY;
    /** Camera as drawn by the last frame, to notice when something else moves it. */
    @Unique
    private float gtnhtweaks$shownX, gtnhtweaks$shownY, gtnhtweaks$shownZoom;
    @Unique
    private float gtnhtweaks$fromX, gtnhtweaks$fromY, gtnhtweaks$fromZoom;
    @Unique
    private long gtnhtweaks$lastFrame;

    @Inject(method = "startDrag", at = @At("RETURN"))
    private void gtnhtweaks$startDrag(int mouseX, int mouseY, CallbackInfo ci) {
        gtnhtweaks$sliding = false;
        if (!NEIModule.preciseDrag) return;
        float[] mouse = gtnhtweaks$preciseMouse();
        if (mouse == null) return;
        lastDragX = mouse[0];
        lastDragY = mouse[1];
    }

    @Inject(method = "drag", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drag(int mouseX, int mouseY, CallbackInfo ci) {
        if (!NEIModule.preciseDrag || !dragging) return;
        float[] mouse = gtnhtweaks$preciseMouse();
        if (mouse == null) return;
        scrollX -= (mouse[0] - lastDragX) / zoomLevel;
        scrollY -= (mouse[1] - lastDragY) / zoomLevel;
        lastDragX = mouse[0];
        lastDragY = mouse[1];
        ci.cancel();
    }

    /** Lets NEI compute the new position from the target of the running slide, not from the drawn camera. */
    @Inject(method = "scroll", at = @At("HEAD"))
    private void gtnhtweaks$scrollStart(int mouseX, int mouseY, int direction, boolean shiftKey, boolean ctrlKey,
        CallbackInfo ci) {
        gtnhtweaks$fromX = scrollX;
        gtnhtweaks$fromY = scrollY;
        gtnhtweaks$fromZoom = zoomLevel;
        if (gtnhtweaks$sliding && gtnhtweaks$isShown()) {
            scrollX = gtnhtweaks$targetX;
            scrollY = gtnhtweaks$targetY;
            zoomLevel = gtnhtweaks$targetZoom;
        }
    }

    @Inject(method = "scroll", at = @At("RETURN"))
    private void gtnhtweaks$scrollEnd(int mouseX, int mouseY, int direction, boolean shiftKey, boolean ctrlKey,
        CallbackInfo ci) {
        if (!NEIModule.smoothScroll || dragging) {
            gtnhtweaks$sliding = false;
            return;
        }
        gtnhtweaks$targetX = scrollX;
        gtnhtweaks$targetY = scrollY;
        gtnhtweaks$targetZoom = zoomLevel;
        if (ctrlKey && !shiftKey) {
            gtnhtweaks$anchorX = mouseX - widgetX;
            gtnhtweaks$anchorY = mouseY - widgetY;
        } else if (!gtnhtweaks$sliding) {
            gtnhtweaks$anchorX = widgetW / 2F;
            gtnhtweaks$anchorY = widgetH / 2F;
        }
        scrollX = gtnhtweaks$fromX;
        scrollY = gtnhtweaks$fromY;
        zoomLevel = gtnhtweaks$fromZoom;
        if (!gtnhtweaks$sliding) gtnhtweaks$lastFrame = System.nanoTime();
        gtnhtweaks$sliding = true;
        gtnhtweaks$remember();
    }

    @Inject(method = "clampToBounds", at = @At("HEAD"))
    private void gtnhtweaks$slide(int treeWidth, int treeHeight, CallbackInfo ci) {
        if (!gtnhtweaks$sliding) return;
        if (dragging || !gtnhtweaks$isShown()) {
            gtnhtweaks$sliding = false;
            return;
        }
        long now = System.nanoTime();
        float dt = (now - gtnhtweaks$lastFrame) / 1_000_000F;
        gtnhtweaks$lastFrame = now;

        // the target obeys the same bounds as the camera
        gtnhtweaks$targetX = gtnhtweaks$clamp(gtnhtweaks$targetX, -widgetW / gtnhtweaks$targetZoom + 4, treeWidth - 4);
        gtnhtweaks$targetY = gtnhtweaks$clamp(gtnhtweaks$targetY, -widgetH / gtnhtweaks$targetZoom + 4, treeHeight - 4);

        // covers about 95% of the way in the configured time, whatever the frame rate
        float t = 1F - (float) Math.exp(-3F * dt / Math.max(1, NEIModule.scrollTimeMs));
        // the world point under the anchor moves straight to its target, the zoom changes evenly in scale
        float worldX = scrollX + gtnhtweaks$anchorX / zoomLevel;
        float worldY = scrollY + gtnhtweaks$anchorY / zoomLevel;
        float targetWorldX = gtnhtweaks$targetX + gtnhtweaks$anchorX / gtnhtweaks$targetZoom;
        float targetWorldY = gtnhtweaks$targetY + gtnhtweaks$anchorY / gtnhtweaks$targetZoom;
        zoomLevel = (float) (zoomLevel * Math.pow(gtnhtweaks$targetZoom / zoomLevel, t));
        scrollX = worldX + (targetWorldX - worldX) * t - gtnhtweaks$anchorX / zoomLevel;
        scrollY = worldY + (targetWorldY - worldY) * t - gtnhtweaks$anchorY / zoomLevel;

        if (Math.abs(zoomLevel / gtnhtweaks$targetZoom - 1) < 0.001F
            && Math.abs((scrollX - gtnhtweaks$targetX) * zoomLevel) < 0.05F
            && Math.abs((scrollY - gtnhtweaks$targetY) * zoomLevel) < 0.05F) {
            scrollX = gtnhtweaks$targetX;
            scrollY = gtnhtweaks$targetY;
            zoomLevel = gtnhtweaks$targetZoom;
            gtnhtweaks$sliding = false;
        }
    }

    @Inject(method = "clampToBounds", at = @At("RETURN"))
    private void gtnhtweaks$afterClamp(int treeWidth, int treeHeight, CallbackInfo ci) {
        if (gtnhtweaks$sliding) gtnhtweaks$remember();
    }

    @Unique
    private void gtnhtweaks$remember() {
        gtnhtweaks$shownX = scrollX;
        gtnhtweaks$shownY = scrollY;
        gtnhtweaks$shownZoom = zoomLevel;
    }

    /** False when something else than the slide moved the camera since the last frame. */
    @Unique
    private boolean gtnhtweaks$isShown() {
        return scrollX == gtnhtweaks$shownX && scrollY == gtnhtweaks$shownY && zoomLevel == gtnhtweaks$shownZoom;
    }

    /** Mouse position in GUI coordinates with the fraction kept, the way NEI computes it for hovering. */
    @Unique
    private static float[] gtnhtweaks$preciseMouse() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) return null;
        return new float[] { Mouse.getX() * (float) screen.width / mc.displayWidth,
            screen.height - Mouse.getY() * (float) screen.height / mc.displayHeight };
    }

    @Unique
    private static float gtnhtweaks$clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
