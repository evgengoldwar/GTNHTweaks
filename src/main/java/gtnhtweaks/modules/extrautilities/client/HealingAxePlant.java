package gtnhtweaks.modules.extrautilities.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.extrautilities.ExtraUtilitiesModule;

/**
 * Draws the Healing Axe of Extra Utilities as a {@link PottedPlantModel pot with a living plant}: in the hands, in
 * the inventory and on the ground. Held in the off hand of Backhand it is drawn the same way, as Backhand draws the off
 * hand item with the renderer of the item; the plant there lives out of step with the one in the main hand.
 * <p>
 * Where the plant is switched off in the config, the renderer the axe had before (if any) draws it.
 */
public final class HealingAxePlant implements IItemRenderer {

    private static final String AXE = "com.rwtema.extrautils.item.ItemHealingAxe";

    /** Renderers the axe had before, by the render type they handled. */
    private final Map<ItemRenderType, IItemRenderer> previous = new EnumMap<>(ItemRenderType.class);

    private HealingAxePlant() {}

    /**
     * Registers the renderer on the first client tick, once every mod has registered its own renderers, so the plant
     * is not replaced by one registered later.
     */
    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new FirstTick());
    }

    public static final class FirstTick {

        private boolean done;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (done) return;
            done = true;
            registerNow();
        }
    }

    private static void registerNow() {
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            if (!AXE.equals(
                item.getClass()
                    .getName()))
                continue;
            HealingAxePlant plant = new HealingAxePlant();
            ItemStack stack = new ItemStack(item);
            for (ItemRenderType type : ItemRenderType.values()) {
                IItemRenderer renderer = MinecraftForgeClient.getItemRenderer(stack, type);
                if (renderer != null) plant.previous.put(type, renderer);
            }
            MinecraftForgeClient.registerItemRenderer(item, plant);
            return;
        }
        GTNHTweaks.LOG.warn("The Healing Axe of Extra Utilities was not found, it is drawn as usual");
    }

    private static boolean drawsPlant(ItemRenderType type) {
        switch (type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                return ExtraUtilitiesModule.plantInHand;
            case INVENTORY:
                return ExtraUtilitiesModule.plantInInventory;
            case ENTITY:
                return ExtraUtilitiesModule.plantOnGround;
            default:
                return false;
        }
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (drawsPlant(type)) return true;
        IItemRenderer renderer = previous.get(type);
        return renderer != null && renderer.handleRenderType(item, type);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        if (!drawsPlant(type)) {
            IItemRenderer renderer = previous.get(type);
            return renderer != null && renderer.shouldUseRenderHelper(type, item, helper);
        }
        // held, dropped and in the inventory like a block
        switch (helper) {
            case ENTITY_ROTATION:
            case ENTITY_BOBBING:
            case EQUIPPED_BLOCK:
            case BLOCK_3D:
            case INVENTORY_BLOCK:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (!drawsPlant(type)) {
            IItemRenderer renderer = previous.get(type);
            if (renderer != null) renderer.renderItem(type, item, data);
            return;
        }
        Entity entity = data.length > 1 && data[1] instanceof Entity ? (Entity) data[1] : null;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        switch (type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                // the block helper puts the corner of the block in the hand: back to its center
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                break;
            case ENTITY:
                // dropped items are drawn at half the size of a block and centered on the ground: stand on it
                GL11.glTranslatef(0, 0.3F, 0);
                break;
            default:
                break;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // drawn as closed boxes, so faces turned away are hidden anyway; Backhand mirrors the off hand
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_BLEND);
        PottedPlantModel.render(Minecraft.getSystemTime() % 100_000_000L / 1000.0, seed(type, item, entity));
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** The plants in both hands, on the ground and of every player live each their own life. */
    private static int seed(ItemRenderType type, ItemStack item, Entity entity) {
        if (type == ItemRenderType.INVENTORY) return System.identityHashCode(item);
        if (entity == null) return 0;
        int seed = entity.getEntityId() * 31;
        if (entity instanceof EntityPlayer && Backhand.isOffhand((EntityPlayer) entity, item)) seed += 7;
        return seed;
    }
}
