package gtnhtweaks.modules.bees.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.model.EyeSpace;
import gtnhtweaks.modules.bees.BeesModule;

/**
 * Draws the drones, princesses, queens and larvae of Forestry as 3D {@link BeeModel bees}: in the hands, in the
 * inventory, in the slots of apiaries and in NEI, and on the ground. The bees of every mod adding bees to Forestry are
 * its items too, with their own species, so they are all drawn so.
 * <p>
 * Where it is switched off in the config, or for a stack with no genes, the renderer the item had before (if any)
 * draws it, else it is drawn as usual.
 */
public final class BeeRenderer implements IItemRenderer {

    private static final float FIRST_PERSON_LIFT = 0.45F;
    private static final float FIRST_PERSON_SCALE = 0.85F;

    /** Nanoseconds within which the next drawing of the same dropped stack is another copy of it, not a new frame. */
    private static final long SAME_FRAME = 2_000_000L;

    /** The dropped stack drawn last, when, and how many copies of it were drawn: Forge draws up to 4 for a stack. */
    private static Entity lastDropped;
    private static long lastDroppedTime;
    private static int copy;

    /** The facing of the bees in the slots, and when it was read. */
    private static float[] slotFacing;
    private static long slotFacingTime;

    /** Renderers the item had before, by the render type they handled. */
    private final Map<ItemRenderType, IItemRenderer> previous = new EnumMap<>(ItemRenderType.class);

    private BeeRenderer() {}

    /**
     * Registers the renderer on the first client tick, once every mod has registered its own renderers, so it is not
     * replaced by one registered later.
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
        if (BeeManager.beeRoot == null) {
            GTNHTweaks.LOG.warn("The bees of Forestry were not found, they are drawn as usual");
            return;
        }
        int found = 0;
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            ItemStack stack = new ItemStack(item);
            EnumBeeType type;
            try {
                type = BeeManager.beeRoot.getType(stack);
            } catch (RuntimeException e) {
                continue;
            }
            if (type == null || type == EnumBeeType.NONE) continue;
            BeeRenderer bees = new BeeRenderer();
            for (ItemRenderType renderType : ItemRenderType.values()) {
                IItemRenderer renderer = MinecraftForgeClient.getItemRenderer(stack, renderType);
                if (renderer != null) bees.previous.put(renderType, renderer);
            }
            MinecraftForgeClient.registerItemRenderer(item, bees);
            found++;
        }
        if (found == 0) GTNHTweaks.LOG.warn("No bee items of Forestry were found, the bees are drawn as usual");
    }

    private static boolean drawsBee(ItemRenderType type, ItemStack item) {
        boolean on;
        switch (type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                on = BeesModule.inHand;
                break;
            case INVENTORY:
                on = BeesModule.inInventory;
                break;
            case ENTITY:
                on = BeesModule.onGround;
                break;
            default:
                on = false;
                break;
        }
        return on && Bee.of(item) != null;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (drawsBee(type, item)) return true;
        IItemRenderer renderer = previous.get(type);
        return renderer != null && renderer.handleRenderType(item, type);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        if (!drawsBee(type, item)) {
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
        Bee bee = drawsBee(type, item) ? Bee.of(item) : null;
        if (bee == null) {
            IItemRenderer renderer = previous.get(type);
            if (renderer != null) renderer.renderItem(type, item, data);
            return;
        }
        Entity entity = data.length > 1 && data[1] instanceof Entity ? (Entity) data[1] : null;
        double time = Minecraft.getSystemTime() % 100_000_000L / 1000.0;
        if (BeesModule.moods) bee.feel(entity);
        else bee.calm();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        // drawn as closed boxes, so faces turned away are hidden anyway; Backhand mirrors the off hand
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_BLEND);
        switch (type) {
            case EQUIPPED:
                // the block helper puts the corner of the block in the hand: back to its center
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                break;
            case EQUIPPED_FIRST_PERSON:
                // held like a block it is below the screen: lift it so all of it is seen
                GL11.glTranslatef(0.5F, 0.5F + FIRST_PERSON_LIFT, 0.5F);
                GL11.glScalef(FIRST_PERSON_SCALE, FIRST_PERSON_SCALE, FIRST_PERSON_SCALE);
                break;
            case ENTITY:
                // dropped items are drawn at half the size of a block and centered on the ground: up from it
                GL11.glTranslatef(0, 0.3F, 0);
                break;
            default:
                break;
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        float[] facing = facing(type);
        // in the hands the view bobs and sways: turning to it at once, the bee jittered
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            facing = bee.smoothFacing(facing[0], facing[1]);
        }
        BeeModel.render(
            bee,
            time,
            seed(type, item, entity),
            facing[0],
            facing[1],
            BeesModule.accessories,
            BeesModule.carryProduct);
        // the items texture back for what the item renderer draws next
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * The yaw and pitch that turn the face to the viewer. In the slots they are the same for every slot, read from the
     * GL now and then rather than for each bee of a page of NEI: reading the GL stalls it.
     */
    private static float[] facing(ItemRenderType type) {
        if (type != ItemRenderType.INVENTORY) {
            return EyeSpace.current()
                .facing(true);
        }
        long now = Minecraft.getSystemTime();
        if (slotFacing == null || now - slotFacingTime > 500) {
            slotFacing = EyeSpace.current()
                .facing(false);
            slotFacingTime = now;
        }
        return slotFacing;
    }

    /**
     * The bees in the hands, on the ground and in every slot live each their own life. The copies Forge draws for a
     * dropped stack too, so it is a little swarm.
     */
    private static int seed(ItemRenderType type, ItemStack item, Entity entity) {
        if (type == ItemRenderType.INVENTORY || entity == null) return System.identityHashCode(item);
        int seed = entity.getEntityId() * 31 + System.identityHashCode(item);
        if (type == ItemRenderType.ENTITY) {
            long now = System.nanoTime();
            copy = entity == lastDropped && now - lastDroppedTime < SAME_FRAME ? copy + 1 : 0;
            lastDropped = entity;
            lastDroppedTime = now;
            seed += copy * 977;
        }
        return seed;
    }
}
