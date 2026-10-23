package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL20;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import gtnhtweaks.common.render.BlockOutline;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Draws the outline of the looked at block with the settings of the minecraft module, in place of the vanilla one, and
 * opens the outline screen with its key.
 */
public class OutlineClient {

    private final KeyBinding key = new KeyBinding(
        "key.gtnhtweaks.outline",
        Keyboard.KEY_O,
        "key.categories.gtnhtweaks");

    public static void register() {
        OutlineClient client = new OutlineClient();
        ClientRegistry.registerKeyBinding(client.key);
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance()
            .bus()
            .register(client);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!key.isPressed()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) mc.displayGuiScreen(new OutlineScreen(null));
    }

    /** Last, so the GregTech tool grid and other mods drawing their own outline come first. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        if (event.isCanceled() || !MinecraftModule.outlineActive()
            || event.subID != 0
            || event.target == null
            || event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        EntityPlayer player = event.player;
        World world = player.worldObj;
        MovingObjectPosition target = event.target;
        Block block = world.getBlock(target.blockX, target.blockY, target.blockZ);
        if (block.getMaterial() == Material.air) return;
        event.setCanceled(true);

        double t = event.partialTicks;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * t;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * t;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * t;
        block.setBlockBoundsBasedOnState(world, target.blockX, target.blockY, target.blockZ);
        AxisAlignedBB box = block.getSelectedBoundingBoxFromPool(world, target.blockX, target.blockY, target.blockZ);
        if (box == null) return;
        box = box.expand(0.002, 0.002, 0.002)
            .getOffsetBoundingBox(-x, -y, -z);

        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_LINE_BIT | GL_DEPTH_BUFFER_BIT);
        int program = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        try {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_LIGHTING);
            glDisable(GL_ALPHA_TEST);
            glDisable(GL_CULL_FACE);
            BlockOutline.draw(box);
        } finally {
            GL20.glUseProgram(program);
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }
}
