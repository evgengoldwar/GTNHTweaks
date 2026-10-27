package gtnhtweaks.modules.loadingscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.loadingscreen.render.LoadingScreenRenderer;

/**
 * The end of the loading screen, drawn over the first frames of the main menu: the wall of blocks is finished, the text
 * fades out and the wall crumbles, uncovering the menu behind it.
 */
public class WallCollapse {

    private boolean soundPlayed, done;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !LoadingTimer.isFinished()) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean running;
        try {
            running = LoadingScreenRenderer.renderOutro(mc.displayWidth, mc.displayHeight);
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("The loading screen wall failed to crumble", t);
            running = false;
        }
        if (running && !soundPlayed && LoadingScreenRenderer.isCollapsing()) {
            soundPlayed = true;
            if (LoadingScreenModule.wallCollapseSound) playSound(mc);
        }
        done = !running;
    }

    private static void playSound(Minecraft mc) {
        try {
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("random.explode"), 0.7F));
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("dig.stone"), 0.8F));
        } catch (Throwable t) {
            GTNHTweaks.LOG.warn("Could not play the sound of the crumbling wall", t);
        }
    }
}
