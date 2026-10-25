package gtnhtweaks.modules.extrautilities.client;

import java.nio.DoubleBuffer;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.extrautilities.ExtraUtilitiesModule;

/**
 * Draws the Healing Axe of Extra Utilities as a little living {@link CompanionModel companion}, chosen in the config:
 * in the hands, in the inventory and on the ground. Held in the off hand of Backhand it is drawn the same way, as
 * Backhand draws the off hand item with the renderer of the item; the one there lives out of step with the one in the
 * main hand. The lantern hangs from the hand, the others stand on it.
 * <p>
 * Where it is switched off in the config, the renderer the axe had before (if any) draws it.
 */
public final class HealingAxeRenderer implements IItemRenderer {

    private static final String AXE = "com.rwtema.extrautils.item.ItemHealingAxe";
    private static final float FIRST_PERSON_LIFT = 0.55F;
    private static final float FIRST_PERSON_SCALE = 0.85F;
    /** Size of the companion on the hand, in the space of the held item before its scale by 0.4. */
    private static final float ON_HAND_SCALE = 0.9F;
    /** How deep the hand goes into its bottom, in its sizes. */
    private static final float ON_HAND_SINK = 0.04F;
    /** The lantern hangs a little smaller, from a little above the middle of the held block in third person. */
    private static final float LANTERN_SCALE = 0.8F, LANTERN_HELD_Y = 0.2F;
    /**
     * Size of the lantern on the hand, and where it hangs from the hand, in the eye space: a little toward the middle
     * of the screen and toward the eye, and so much higher than the hand that it is seen whole, beside the top of the
     * arm.
     */
    private static final float LANTERN_ON_HAND_SCALE = 0.3F, LANTERN_INWARD = 0.04F, LANTERN_UP = 0.2F,
        LANTERN_FORWARD = 0.1F;

    /** The plane the arm is cut by, set in the space of the companion. */
    private static final DoubleBuffer BOTTOM = BufferUtils.createDoubleBuffer(4);

    /** The light what glows was drawn in, before its full brightness. */
    private static float brightnessX, brightnessY;

    static {
        Shapes.fullBright = () -> {
            brightnessX = OpenGlHelper.lastBrightnessX;
            brightnessY = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        };
        Shapes.usualLight = () -> OpenGlHelper
            .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
    }

    /** Renderers the axe had before, by the render type they handled. */
    private final Map<ItemRenderType, IItemRenderer> previous = new EnumMap<>(ItemRenderType.class);

    private HealingAxeRenderer() {}

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
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            if (!AXE.equals(
                item.getClass()
                    .getName()))
                continue;
            HealingAxeRenderer companion = new HealingAxeRenderer();
            ItemStack stack = new ItemStack(item);
            for (ItemRenderType type : ItemRenderType.values()) {
                IItemRenderer renderer = MinecraftForgeClient.getItemRenderer(stack, type);
                if (renderer != null) companion.previous.put(type, renderer);
            }
            MinecraftForgeClient.registerItemRenderer(item, companion);
            return;
        }
        GTNHTweaks.LOG.warn("The Healing Axe of Extra Utilities was not found, it is drawn as usual");
    }

    private static boolean drawsCompanion(ItemRenderType type) {
        switch (type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                return ExtraUtilitiesModule.inHand;
            case INVENTORY:
                return ExtraUtilitiesModule.inInventory;
            case ENTITY:
                return ExtraUtilitiesModule.onGround;
            default:
                return false;
        }
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (drawsCompanion(type)) return true;
        IItemRenderer renderer = previous.get(type);
        return renderer != null && renderer.handleRenderType(item, type);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        if (!drawsCompanion(type)) {
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
        if (!drawsCompanion(type)) {
            IItemRenderer renderer = previous.get(type);
            if (renderer != null) renderer.renderItem(type, item, data);
            return;
        }
        Entity entity = data.length > 1 && data[1] instanceof Entity ? (Entity) data[1] : null;
        boolean lantern = ExtraUtilitiesModule.companion == ExtraUtilitiesModule.Companion.LANTERN;
        boolean arm = type == ItemRenderType.EQUIPPED_FIRST_PERSON && ExtraUtilitiesModule.holdingArm
            && entity instanceof AbstractClientPlayer
            && !entity.isInvisible();
        boolean offhand = entity instanceof EntityPlayer && Backhand.isOffhand((EntityPlayer) entity, item);
        double time = Minecraft.getSystemTime() % 100_000_000L / 1000.0;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
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
                if (lantern) {
                    GL11.glTranslatef(0, LANTERN_HELD_Y, 0);
                    GL11.glScalef(LANTERN_SCALE, LANTERN_SCALE, LANTERN_SCALE);
                    LanternSwing.hang(entity, offhand, time);
                }
                break;
            case EQUIPPED_FIRST_PERSON:
                if (arm && lantern) {
                    GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                    GL11.glScalef(2.5F, 2.5F, 2.5F);
                    holdLantern((AbstractClientPlayer) entity, offhand, time);
                } else if (arm) {
                    // back to the space of the held item before its scale by 0.4, where vanilla places the bare arm,
                    // and the companion on its hand
                    GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                    GL11.glScalef(2.5F, 2.5F, 2.5F);
                    AbstractClientPlayer player = (AbstractClientPlayer) entity;
                    float[] hand = HoldingArm.hand(player);
                    GL11.glPushMatrix();
                    GL11.glTranslatef(hand[0], hand[1], hand[2]);
                    GL11.glScalef(ON_HAND_SCALE, ON_HAND_SCALE, ON_HAND_SCALE);
                    GL11.glTranslatef(0, 0.5F - ON_HAND_SINK, 0);
                    // the arm goes up to the bottom of the companion and no further, not through it: cut above
                    // its bottom, y = -0.5
                    BOTTOM.clear();
                    BOTTOM.put(new double[] { 0, -1, 0, -0.5 })
                        .flip();
                    GL11.glClipPlane(GL11.GL_CLIP_PLANE0, BOTTOM);
                    GL11.glPopMatrix();
                    GL11.glEnable(GL11.GL_CLIP_PLANE0);
                    HoldingArm.render(player);
                    GL11.glDisable(GL11.GL_CLIP_PLANE0);
                    // the items texture back for what the item renderer draws next
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(TextureMap.locationItemsTexture);
                    GL11.glTranslatef(hand[0], hand[1], hand[2]);
                    GL11.glScalef(ON_HAND_SCALE, ON_HAND_SCALE, ON_HAND_SCALE);
                    GL11.glTranslatef(0, 0.5F - ON_HAND_SINK, 0);
                } else {
                    // held like a block it is below the screen: lift it so all of it is seen
                    GL11.glTranslatef(0.5F, 0.5F + FIRST_PERSON_LIFT, 0.5F);
                    GL11.glScalef(FIRST_PERSON_SCALE, FIRST_PERSON_SCALE, FIRST_PERSON_SCALE);
                    if (lantern) {
                        // hanging by its handle from where the top of the item is
                        GL11.glTranslatef(0, 0.5F, 0);
                        LanternSwing.hang(entity, offhand, time);
                    }
                }
                break;
            case ENTITY:
                // dropped items are drawn at half the size of a block and centered on the ground: stand on it
                GL11.glTranslatef(0, 0.3F, 0);
                break;
            default:
                break;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        float[] facing = EyeSpace.current()
            .facing(type != ItemRenderType.INVENTORY);
        model().render(
            time,
            seed(type, item, entity, offhand),
            facing[0],
            facing[1],
            ExtraUtilitiesModule.moods ? Moods.at(entity) : Mood.NEUTRAL);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * The bare arm of vanilla, just where vanilla has it, so it is the size of the other, empty hand, and the lantern
     * hanging above its hand, beside the top of the arm. Where the arm is in front of the lantern it hides it. Leaves
     * the space of the lantern.
     */
    private static void holdLantern(AbstractClientPlayer player, boolean offhand, double time) {
        EyeSpace eye = EyeSpace.current();
        float[] hand = HoldingArm.hand(player);
        // toward the middle of the screen (to the right in the left hand, to the left in the right hand), up and
        // toward the eye
        float[] at = eye.direction(eye.mirrored() ? LANTERN_INWARD : -LANTERN_INWARD, LANTERN_UP, LANTERN_FORWARD);

        HoldingArm.render(player);
        // the items texture back for what the item renderer draws next
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);

        GL11.glTranslatef(hand[0] + at[0], hand[1] + at[1], hand[2] + at[2]);
        GL11.glScalef(LANTERN_ON_HAND_SCALE, LANTERN_ON_HAND_SCALE, LANTERN_ON_HAND_SCALE);
        LanternSwing.hang(player, offhand, time);
        GL11.glTranslatef(0, ON_HAND_SINK, 0);
    }

    private static CompanionModel model() {
        switch (ExtraUtilitiesModule.companion) {
            case AQUARIUM:
                return AquariumModel.INSTANCE;
            case SLIME:
                return SlimeJarModel.INSTANCE;
            case LANTERN:
                return LanternModel.INSTANCE;
            case PEASHOOTER:
                return PeashooterModel.INSTANCE;
            case SUNFLOWER:
                return SunflowerModel.INSTANCE;
            default:
                return PottedPlantModel.INSTANCE;
        }
    }

    /** The companions in both hands, on the ground and of every player live each their own life. */
    private static int seed(ItemRenderType type, ItemStack item, Entity entity, boolean offhand) {
        if (type == ItemRenderType.INVENTORY) return System.identityHashCode(item);
        if (entity == null) return 0;
        return entity.getEntityId() * 31 + (offhand ? 7 : 0);
    }
}
