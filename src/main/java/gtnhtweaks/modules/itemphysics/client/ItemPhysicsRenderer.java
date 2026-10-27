package gtnhtweaks.modules.itemphysics.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import gtnhtweaks.common.render.UprightWhenDropped;
import gtnhtweaks.modules.itemphysics.ItemPhysicsModule;

/**
 * Draws dropped items lying on the ground and tumbling in the air.
 * <p>
 * Every item is a shape that rests on the bottom of the entity's box: a flat item lies on its side, a block on a face,
 * a stack is a pile. Its tilt around its own axis is simulated per entity: in the air it turns with the speed of the
 * item, on landing it keeps a part of that turn and falls over onto the nearest side with a spring, so it rocks a bit
 * and settles. The shape is lifted by as much as its tilt lowers its lowest point, so it never goes into the floor, and
 * moved away from the walls it would go into.
 * <p>
 * Only the drawing changes: the entity moves, is picked up and syncs as in vanilla.
 */
public final class ItemPhysicsRenderer {

    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    private static final int BLOCK = 0;
    private static final int ITEM = 1;
    private static final int CUSTOM_3D = 2;
    private static final int CUSTOM_2D = 3;

    private static final float RAD = (float) Math.PI / 180F;

    /** Thickness of a flat item and the step between the items of a pile, in item units, as in vanilla. */
    private static final float THICKNESS = 0.0625F;
    private static final float PILE_STEP = THICKNESS + 0.021875F;
    /** How far the items of a pile lie from each other, in item units. */
    private static final float PILE_SPREAD = 0.12F;
    /** The items of a pile are turned by up to this many degrees from each other. */
    private static final float PILE_TURN = 25F;
    /**
     * Blocks of a pile are smaller than a lone block, so the pile stays low: two lie side by side and the third on top
     * of them, moved and turned a bit.
     */
    private static final float PILE_BLOCK_SIZE = 0.7F;
    /** Space between the two blocks on the ground, so they do not go into each other when turned. */
    private static final float PILE_BLOCK_GAP = 0.06F;
    /** How far the blocks of a pile lie from their places, in block units. */
    private static final float PILE_BLOCK_SPREAD = 0.08F;
    /** The blocks on the ground and the one on top are turned by up to this many degrees. */
    private static final float PILE_BLOCK_TURN = 6F;
    private static final float PILE_TOP_TURN = 30F;
    /**
     * A flat item of a mod renderer is drawn as the vanilla dropped items: from -0.25 to 0.75 high and about 0.04 thick
     * on both sides of its middle.
     */
    private static final float CUSTOM_LIFT = 0.045F;
    private static final float CUSTOM_CENTER = 0.25F;

    /** Degrees per tick of turning for each block per tick of speed, and at most. */
    private static final float SPIN_PER_SPEED = 90F;
    private static final float MAX_SPIN = 30F;
    private static final float WATER_SPIN = 2F;
    /** Part of the tumbling that also turns the item around the vertical, so it tumbles in 3D. */
    private static final float YAW_PART = 0.25F;
    /** Part of the turn kept on landing: the rest is lost in the hit. */
    private static final float LANDING_KEEP = 0.35F;
    /** Spring that lays the item down, per tick: a bit underdamped, so it rocks once and settles. */
    private static final float SPRING = 0.6F;
    private static final float DAMPING = 1.1F;
    private static final float SUBSTEP = 0.25F;
    /**
     * An item starts to tumble only this fast or this many ticks in the air: the server puts a lying item a hair above
     * the ground when it corrects its place, and it must not tumble for that little fall.
     */
    private static final float TUMBLE_SPEED = 0.15F;
    private static final float TUMBLE_AIR_TICKS = 6F;
    /** Part per tick of what is left of a place taken from the server that the drawn item moves on. */
    private static final float GLIDE_RATE = 0.3F;
    /**
     * How far, along each axis, the server may put an item lying on the client before the client takes that place.
     * Closer, the item stays where it fell on the client: the server picks it up from its own place anyway, and the
     * player picks up items in a box one block wider than the player.
     */
    private static final double KEEP_DISTANCE = 0.75;
    private static final double KEEP_HEIGHT = 0.6;
    /** Speeds sent by the server that an item lying on the client does not take: the small moves of its own landing. */
    private static final double KEEP_SPEED = 0.15;
    /** Farther moves are teleports: taken at once, without gliding. */
    private static final double TELEPORT_DISTANCE = 2;
    /**
     * Items lying on each other on the same ground have their faces at the same height and flicker, each drawn over the
     * other in turn. An item that comes to lie takes the lowest layer that no item lying where it would touch it has,
     * and is lifted by that many steps, so items that touch never share a height and the last one lies on top.
     */
    private static final float LAYER_STEP = 0.0015F;
    /** Items closer than this along both horizontal axes and in height can touch, in blocks. */
    private static final double LAYER_REACH = 0.75;
    private static final double LAYER_REACH_HEIGHT = 0.5;
    /** A lying item moved this far from where it took its layer takes one anew. */
    private static final double LAYER_MOVE = 0.05;
    /** Most copies of a pile: the layout is made for all of them, so a pile keeps its look when it grows. */
    private static final int MAX_COPIES = 5;

    private static final Map<EntityItem, State> STATES = new WeakHashMap<>();
    private static final Random RANDOM = new Random();
    private static final List<AxisAlignedBB> BOXES = new ArrayList<>();
    private static RenderBlocks renderBlocks;
    private static double placeX, placeY, placeZ;

    private ItemPhysicsRenderer() {}

    /**
     * Draws the dropped item instead of vanilla.
     *
     * @return false when vanilla has to draw it: in an item frame or for an item not in the world, like the fake items
     *         mods draw on their blocks
     */
    public static boolean render(EntityItem entity, double x, double y, double z, float partialTicks,
        boolean withColor) {
        if (RenderItem.renderInFrame) return false;
        World world = entity.worldObj;
        if (world == null || world.getEntityByID(entity.getEntityId()) != entity) return false;
        ItemStack stack = entity.getEntityItem();
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == null) return false;

        IItemRenderer custom = MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.ENTITY);
        Block block = item instanceof ItemBlock ? Block.getBlockFromItem(item) : null;
        boolean block3D = block != null && RenderBlocks.renderItemIn3d(block.getRenderType());
        int kind;
        if (custom != null) {
            kind = block3D || custom.shouldUseRenderHelper(ItemRenderType.ENTITY, stack, ItemRendererHelper.BLOCK_3D)
                ? CUSTOM_3D
                : CUSTOM_2D;
        } else {
            kind = block3D && stack.getItemSpriteNumber() == 0 ? BLOCK : ITEM;
        }
        boolean solid = kind == BLOCK || kind == CUSTOM_3D;
        int copies = !ItemPhysicsModule.piles ? 1 : solid ? blockCopiesOf(stack.stackSize) : copiesOf(stack.stackSize);
        float scale = solid ? blockScale(block) : 0.5F;

        State state = STATES.get(entity);
        if (state == null) {
            state = new State(entity);
            STATES.put(entity, state);
        }
        if (state.kind != kind || state.copies != copies || state.scale != scale) {
            state.layout(entity, kind, copies, scale);
        }
        // a lone block lies on any face, a pile only right side up or upside down, a living thing only right side up
        float restStep = custom instanceof UprightWhenDropped ? 360F : solid && copies == 1 ? 90F : 180F;
        float dt = state.update(entity, partialTicks, restStep);
        state.glide(dt);
        x += state.glideX;
        y += state.glideY;
        z += state.glideZ;

        float yaw = entity.hoverStart / RAD + state.turn;
        float pitch = state.pitch;
        // lifted by as much as the tilt lowers the lowest point, so the shape stands on the bottom of the entity
        float lift = state.height / 2F * Math.abs(MathHelper.cos(pitch * RAD))
            + state.halfZ * Math.abs(MathHelper.sin(pitch * RAD));
        updateLayer(entity, state);
        double bottom = y - entity.yOffset + Math.max(0, state.layer) * LAYER_STEP;
        if (ItemPhysicsModule.keepOutOfBlocks) {
            state.updatePush(
                world,
                entity,
                x + RenderManager.renderPosX,
                bottom + RenderManager.renderPosY,
                z + RenderManager.renderPosZ,
                yaw,
                lift * 2F,
                dt);
        } else {
            state.pushX = 0;
            state.pushZ = 0;
        }

        TextureManager textures = Minecraft.getMinecraft()
            .getTextureManager();
        ResourceLocation atlas = atlasOf(stack);
        textures.bindTexture(atlas);
        TextureUtil.func_152777_a(false, false, 1.0F);
        GL11.glPushMatrix();
        GL11.glTranslated(x + state.pushX, bottom + lift, z + state.pushZ);
        GL11.glRotatef(yaw, 0F, 1F, 0F);
        GL11.glRotatef(pitch, 1F, 0F, 0F);
        // from the middle of the shape, which it turns around, to its bottom
        GL11.glTranslatef(0F, -state.height / 2F, 0F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(scale, scale, scale);
        if (solid) {
            drawBlocks(state, entity, stack, block, custom, textures);
        } else if (kind == ITEM) {
            drawItems(state, stack, textures, withColor);
        } else {
            drawCustomItems(state, entity, stack, custom, textures);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        textures.bindTexture(atlas);
        TextureUtil.func_147945_b();
        return true;
    }

    /**
     * Called when the server sends where a dropped item is, before the client moves it there.
     * <p>
     * The client moves a dropped item on its own and the server corrects it now and then, so an item that has just
     * landed jumps to another place, or is put a hair above the ground and falls again. An item lying on the client
     * keeps its place when the server puts it close by.
     *
     * @return true to keep the item where it is
     */
    public static boolean keepPlace(EntityItem entity, double x, double y, double z) {
        if (isLying(entity) && Math.abs(x - entity.posX) <= KEEP_DISTANCE
            && Math.abs(z - entity.posZ) <= KEEP_DISTANCE
            && Math.abs(y - entity.posY) <= KEEP_HEIGHT) {
            return true;
        }
        placeX = entity.posX;
        placeY = entity.posY;
        placeZ = entity.posZ;
        return false;
    }

    /**
     * Called after the item took a place sent by the server: the drawn item glides there instead of jumping. The
     * last place of the tick moves along, so the entity is not drawn moving there over a tick either.
     */
    public static void tookPlace(EntityItem entity) {
        State state = STATES.get(entity);
        if (state == null) return;
        double dx = entity.posX - placeX, dy = entity.posY - placeY, dz = entity.posZ - placeZ;
        if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            state.glideX = state.glideY = state.glideZ = 0;
            return;
        }
        entity.lastTickPosX += dx;
        entity.lastTickPosY += dy;
        entity.lastTickPosZ += dz;
        entity.prevPosX += dx;
        entity.prevPosY += dy;
        entity.prevPosZ += dz;
        state.glideX -= dx;
        state.glideY -= dy;
        state.glideZ -= dz;
    }

    /**
     * Called when the server sends the speed of a dropped item.
     *
     * @return true to keep an item lying on the client still, instead of the small moves of the landing on the server
     */
    public static boolean keepSpeed(EntityItem entity, double motionX, double motionY, double motionZ) {
        return isLying(entity) && motionX * motionX + motionY * motionY + motionZ * motionZ < KEEP_SPEED * KEEP_SPEED;
    }

    /** Gives an item that comes to lie, or is moved while lying, the lowest layer free among the items it can touch. */
    private static void updateLayer(EntityItem entity, State state) {
        // an item in the air keeps the layer it had, and takes one when it lands
        if (!isLying(entity)) return;
        if (state.layer >= 0 && Math.abs(entity.posX - state.layerX) < LAYER_MOVE
            && Math.abs(entity.posY - state.layerY) < LAYER_MOVE
            && Math.abs(entity.posZ - state.layerZ) < LAYER_MOVE) return;
        BitSet used = new BitSet();
        for (Map.Entry<EntityItem, State> entry : STATES.entrySet()) {
            EntityItem other = entry.getKey();
            int layer = entry.getValue().layer;
            if (other == entity || layer < 0 || other.isDead || other.worldObj != entity.worldObj || !isLying(other)) {
                continue;
            }
            if (Math.abs(other.posX - entity.posX) < LAYER_REACH && Math.abs(other.posZ - entity.posZ) < LAYER_REACH
                && Math.abs(other.posY - entity.posY) < LAYER_REACH_HEIGHT) used.set(layer);
        }
        state.layer = used.nextClearBit(0);
        state.layerX = entity.posX;
        state.layerY = entity.posY;
        state.layerZ = entity.posZ;
    }

    private static boolean isLying(EntityItem entity) {
        return entity.onGround && entity.motionX * entity.motionX + entity.motionZ * entity.motionZ < 1.0E-4;
    }

    /** Copies drawn for a stack, as in vanilla. */
    private static int copiesOf(int size) {
        return size > 40 ? 5 : size > 20 ? 4 : size > 5 ? 3 : size > 1 ? 2 : 1;
    }

    /** Blocks drawn for a stack: at most three, two side by side and one on top. */
    private static int blockCopiesOf(int size) {
        return size > 20 ? 3 : size > 1 ? 2 : 1;
    }

    /** Scale of a dropped block, as in vanilla: crossed squares and the like are drawn twice as big. */
    private static float blockScale(Block block) {
        int type = block != null ? block.getRenderType() : 1;
        return type == 1 || type == 19 || type == 12 || type == 2 ? 0.5F : 0.25F;
    }

    private static ResourceLocation atlasOf(ItemStack stack) {
        return stack.getItemSpriteNumber() == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture;
    }

    private static RenderBlocks renderBlocks() {
        if (renderBlocks == null) renderBlocks = new RenderBlocks();
        return renderBlocks;
    }

    /** Blocks, each standing on its bottom face; the unit cube of a block is centered on the origin. */
    private static void drawBlocks(State state, EntityItem entity, ItemStack stack, Block block, IItemRenderer custom,
        TextureManager textures) {
        boolean blend = block != null && block.getRenderBlockPass() > 0;
        if (blend) {
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        }
        for (int i = 0; i < state.copies; i++) {
            GL11.glPushMatrix();
            float size = state.sizes[i];
            GL11.glTranslatef(state.offX[i], state.offY[i] + size / 2F, state.offZ[i]);
            GL11.glRotatef(state.turns[i], 0F, 1F, 0F);
            GL11.glScalef(size, size, size);
            if (custom != null) {
                // a mod renderer can leave another texture bound
                textures.bindTexture(atlasOf(stack));
                custom.renderItem(ItemRenderType.ENTITY, stack, renderBlocks(), entity);
            } else {
                renderBlocks().renderBlockAsItem(block, stack.getItemDamage(), 1.0F);
            }
            GL11.glPopMatrix();
        }
        if (blend) GL11.glDisable(GL11.GL_BLEND);
    }

    /** Flat items with the thickness of vanilla, lying face up, one on top of the other. */
    private static void drawItems(State state, ItemStack stack, TextureManager textures, boolean withColor) {
        Item item = stack.getItem();
        boolean multiPass = item.requiresMultipleRenderPasses();
        int passes = multiPass ? item.getRenderPasses(stack.getItemDamage()) : 1;
        for (int i = 0; i < state.copies; i++) {
            GL11.glPushMatrix();
            GL11.glTranslatef(state.offX[i], state.offY[i] + THICKNESS, state.offZ[i]);
            GL11.glRotatef(state.turns[i], 0F, 1F, 0F);
            // the icon is drawn in x and y from 0 to 1, its front at z 0 and its back at -THICKNESS: laid face up
            GL11.glRotatef(-90F, 1F, 0F, 0F);
            GL11.glTranslatef(-0.5F, -0.5F, 0F);
            for (int pass = 0; pass < passes; pass++) {
                IIcon icon = multiPass ? item.getIcon(stack, pass) : stack.getIconIndex();
                if (icon == null) icon = missingIcon(stack, textures);
                float r = 1F, g = 1F, b = 1F;
                if (withColor) {
                    int color = item.getColorFromItemStack(stack, pass);
                    r = (color >> 16 & 255) / 255F;
                    g = (color >> 8 & 255) / 255F;
                    b = (color & 255) / 255F;
                }
                textures.bindTexture(atlasOf(stack));
                GL11.glColor4f(r, g, b, 1F);
                ItemRenderer.renderItemIn2D(
                    Tessellator.instance,
                    icon.getMaxU(),
                    icon.getMinV(),
                    icon.getMinU(),
                    icon.getMaxV(),
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    THICKNESS);
                if (stack.hasEffect(pass)) drawGlint(textures);
            }
            GL11.glPopMatrix();
        }
    }

    private static IIcon missingIcon(ItemStack stack, TextureManager textures) {
        ResourceLocation location = textures.getResourceLocation(stack.getItemSpriteNumber());
        return ((TextureMap) textures.getTexture(location)).getAtlasSprite("missingno");
    }

    /** The enchantment glint over the icon just drawn, as in vanilla. */
    private static void drawGlint(TextureManager textures) {
        GL11.glDepthFunc(GL11.GL_EQUAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        textures.bindTexture(GLINT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
        float brightness = 0.76F;
        GL11.glColor4f(0.5F * brightness, 0.25F * brightness, 0.8F * brightness, 1.0F);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        float glintScale = 0.125F;
        GL11.glScalef(glintScale, glintScale, glintScale);
        float shift = (float) (Minecraft.getSystemTime() % 3000L) / 3000.0F * 8.0F;
        GL11.glTranslatef(shift, 0.0F, 0.0F);
        GL11.glRotatef(-50.0F, 0.0F, 0.0F, 1.0F);
        ItemRenderer.renderItemIn2D(Tessellator.instance, 0.0F, 0.0F, 1.0F, 1.0F, 255, 255, THICKNESS);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glScalef(glintScale, glintScale, glintScale);
        shift = (float) (Minecraft.getSystemTime() % 4873L) / 4873.0F * 8.0F;
        GL11.glTranslatef(-shift, 0.0F, 0.0F);
        GL11.glRotatef(10.0F, 0.0F, 0.0F, 1.0F);
        ItemRenderer.renderItemIn2D(Tessellator.instance, 0.0F, 0.0F, 1.0F, 1.0F, 255, 255, THICKNESS);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /** Flat items of a mod renderer, like the GregTech items: laid face up like the vanilla ones. */
    private static void drawCustomItems(State state, EntityItem entity, ItemStack stack, IItemRenderer custom,
        TextureManager textures) {
        for (int i = 0; i < state.copies; i++) {
            GL11.glPushMatrix();
            GL11.glTranslatef(state.offX[i], state.offY[i] + CUSTOM_LIFT, state.offZ[i]);
            GL11.glRotatef(state.turns[i], 0F, 1F, 0F);
            GL11.glTranslatef(0F, 0F, CUSTOM_CENTER);
            GL11.glRotatef(-90F, 1F, 0F, 0F);
            textures.bindTexture(atlasOf(stack));
            custom.renderItem(ItemRenderType.ENTITY, stack, renderBlocks(), entity);
            GL11.glPopMatrix();
        }
    }

    /** The tilt of one dropped item and the layout of its pile. */
    private static final class State {

        int kind = -1;
        int copies;
        float scale;
        /** Places, turns and sizes of the copies, in item units, the bottom of the shape at y 0. */
        float[] offX, offY, offZ, turns, sizes;
        /** Height of the shape and how far it reaches along its own z, in blocks. */
        float height, halfZ;
        /** Half the width of a copy of size 1, in blocks. */
        float copyHalf;

        /** Tilt around the item's own x axis in degrees, a multiple of the rest step when lying. */
        float pitch;
        /** Speed of the tilt in degrees per tick. */
        float spin;
        /** Turn around the vertical gained while tumbling, in degrees. */
        float turn;
        /** Tilt the spring lays the item down to. */
        float target;
        final float spinSign;
        boolean tumbling;
        float airTicks;
        float time = Float.NaN;

        /** What is left to move of the last place taken from the server, in blocks. */
        double glideX, glideY, glideZ;

        double pushX, pushZ, targetPushX, targetPushZ;
        boolean pushSet;
        double keyX = Double.NaN, keyY, keyZ;
        /** Height layer among the items it can touch, -1 before it first lies; where it took it. */
        int layer = -1;
        double layerX, layerY, layerZ;
        float keyYaw, keyHeight;

        State(EntityItem entity) {
            spinSign = (Float.floatToIntBits(entity.hoverStart) & 1) == 0 ? 1F : -1F;
        }

        void layout(EntityItem entity, int kind, int copies, float scale) {
            this.kind = kind;
            this.copies = copies;
            this.scale = scale;
            offX = new float[MAX_COPIES];
            offY = new float[MAX_COPIES];
            offZ = new float[MAX_COPIES];
            turns = new float[MAX_COPIES];
            sizes = new float[MAX_COPIES];
            RANDOM.setSeed(Float.floatToIntBits(entity.hoverStart) * 31L);
            float localHeight;
            if (kind == BLOCK || kind == CUSTOM_3D) {
                // two side by side on the ground and the third on top of them, across both
                float side = PILE_BLOCK_SIZE / 2F + PILE_BLOCK_GAP;
                for (int i = 0; i < 2; i++) {
                    // moved only outwards, so the two never come closer than the gap
                    float apart = side + RANDOM.nextFloat() * PILE_BLOCK_SPREAD;
                    offX[i] = i == 0 ? -apart : apart;
                    offZ[i] = (RANDOM.nextFloat() * 2F - 1F) * PILE_BLOCK_SPREAD;
                    turns[i] = (RANDOM.nextFloat() * 2F - 1F) * PILE_BLOCK_TURN;
                }
                offX[2] = (RANDOM.nextFloat() * 2F - 1F) * PILE_BLOCK_SPREAD;
                offZ[2] = (RANDOM.nextFloat() * 2F - 1F) * PILE_BLOCK_SPREAD;
                // a hair above the blocks under it, so their faces do not flicker
                offY[2] = PILE_BLOCK_SIZE + 0.002F;
                turns[2] = (RANDOM.nextFloat() * 2F - 1F) * PILE_TOP_TURN;
                if (copies == 1) {
                    offX[0] = offZ[0] = turns[0] = 0F;
                    sizes[0] = 1F;
                    localHeight = 1F;
                } else {
                    Arrays.fill(sizes, PILE_BLOCK_SIZE);
                    localHeight = copies == 2 ? PILE_BLOCK_SIZE : offY[2] + PILE_BLOCK_SIZE;
                }
            } else {
                for (int i = 1; i < MAX_COPIES; i++) {
                    offX[i] = (RANDOM.nextFloat() * 2F - 1F) * PILE_SPREAD;
                    offZ[i] = (RANDOM.nextFloat() * 2F - 1F) * PILE_SPREAD;
                    offY[i] = i * PILE_STEP;
                    turns[i] = (RANDOM.nextFloat() * 2F - 1F) * PILE_TURN;
                }
                Arrays.fill(sizes, 1F);
                localHeight = (copies - 1) * PILE_STEP + (kind == ITEM ? THICKNESS : CUSTOM_LIFT * 2F);
            }
            float localHalfZ = 0;
            for (int i = 0; i < copies; i++) {
                float t = turns[i] * RAD;
                float reach = Math.abs(offZ[i])
                    + 0.5F * sizes[i] * (Math.abs(MathHelper.cos(t)) + Math.abs(MathHelper.sin(t)));
                localHalfZ = Math.max(localHalfZ, reach);
            }
            height = localHeight * scale;
            halfZ = localHalfZ * scale;
            copyHalf = 0.5F * scale;
            keyX = Double.NaN;
        }

        /**
         * Moves the tilt on to the time of this frame.
         *
         * @param restStep degrees between the sides the item can lie on: 180 for flat items and piles, 90 for a block,
         *                 360 for what stands only right side up
         * @return ticks since the last frame
         */
        float update(EntityItem entity, float partialTicks, float restStep) {
            float now = entity.age + partialTicks;
            float dt = Float.isNaN(time) ? 0F : now - time;
            time = now;
            if (dt < 0F || dt > 20F) dt = 0F;

            if (!ItemPhysicsModule.tumbleInAir) {
                pitch = 0F;
                spin = 0F;
                return dt;
            }
            if (entity.onGround) {
                airTicks = 0F;
                if (tumbling) {
                    tumbling = false;
                    spin *= LANDING_KEEP;
                    target = restAngle(restStep);
                }
                settle(dt);
            } else {
                airTicks += dt;
                boolean inWater = entity.isInWater();
                float speed = MathHelper.sqrt_double(
                    entity.motionX * entity.motionX + entity.motionY * entity.motionY
                        + entity.motionZ * entity.motionZ);
                if (!tumbling && (inWater || speed > TUMBLE_SPEED || airTicks > TUMBLE_AIR_TICKS)) tumbling = true;
                if (!tumbling) {
                    settle(dt);
                    return dt;
                }
                float wanted = inWater ? WATER_SPIN : Math.min(speed * SPIN_PER_SPEED, MAX_SPIN);
                wanted *= spinSign * ItemPhysicsModule.tumbleSpeed;
                spin += (wanted - spin) * (1F - (float) Math.exp(-dt * 0.15F));
                pitch += spin * dt;
                turn += spin * YAW_PART * dt;
                if (pitch > 360F || pitch < -360F) pitch %= 360F;
                if (turn > 360F || turn < -360F) turn %= 360F;
            }
            return dt;
        }

        /** The drawn item moves on to the last place taken from the server. */
        void glide(float dt) {
            double keep = Math.exp(-dt * GLIDE_RATE);
            glideX = clamp(glideX * keep, 1.5);
            glideY = clamp(glideY * keep, 1.5);
            glideZ = clamp(glideZ * keep, 1.5);
        }

        /** The side the item falls onto: the next one in the way it turns, unless it has barely passed one. */
        private float restAngle(float restStep) {
            float sides = pitch / restStep;
            float side;
            if (spin > 1F) side = (float) Math.ceil(sides - 0.2F);
            else if (spin < -1F) side = (float) Math.floor(sides + 0.2F);
            else side = Math.round(sides);
            return side * restStep;
        }

        private void settle(float dt) {
            if (pitch == target && spin == 0F) return;
            while (dt > 0F) {
                float step = Math.min(dt, SUBSTEP);
                spin += (SPRING * (target - pitch) - DAMPING * spin) * step;
                pitch += spin * step;
                dt -= step;
            }
            if (Math.abs(target - pitch) < 0.05F && Math.abs(spin) < 0.05F) {
                target %= 360F;
                pitch = target;
                spin = 0F;
            }
        }

        /**
         * Finds how far to move the item so it does not go into the blocks around it, and moves it there smoothly.
         *
         * @param x      the middle of the entity in the world
         * @param bottom the bottom of the entity in the world
         * @param z      the middle of the entity in the world
         */
        void updatePush(World world, EntityItem entity, double x, double bottom, double z, float yaw, float shapeHeight,
            float dt) {
            if (x != keyX || bottom != keyY || z != keyZ || yaw != keyYaw || shapeHeight != keyHeight) {
                keyX = x;
                keyY = bottom;
                keyZ = z;
                keyYaw = yaw;
                keyHeight = shapeHeight;
                findPush(world, entity, x, bottom, z, yaw, shapeHeight);
            }
            if (!pushSet) {
                pushX = targetPushX;
                pushZ = targetPushZ;
                pushSet = true;
            } else {
                double part = Math.min(1F, dt * 0.4F);
                pushX += (targetPushX - pushX) * part;
                pushZ += (targetPushZ - pushZ) * part;
            }
        }

        private void findPush(World world, EntityItem entity, double x, double bottom, double z, float yaw,
            float shapeHeight) {
            // what the copies cover around the middle, lying, turned by the yaw of the item
            float cos = MathHelper.cos(yaw * RAD);
            float sin = MathHelper.sin(yaw * RAD);
            double minX = 0, maxX = 0, minZ = 0, maxZ = 0;
            for (int i = 0; i < copies; i++) {
                float t = (yaw + turns[i]) * RAD;
                float reach = copyHalf * sizes[i] * (Math.abs(MathHelper.cos(t)) + Math.abs(MathHelper.sin(t)));
                float cx = (offX[i] * cos + offZ[i] * sin) * scale;
                float cz = (-offX[i] * sin + offZ[i] * cos) * scale;
                minX = Math.min(minX, cx - reach);
                maxX = Math.max(maxX, cx + reach);
                minZ = Math.min(minZ, cz - reach);
                maxZ = Math.max(maxZ, cz + reach);
            }
            AxisAlignedBB shape = AxisAlignedBB
                .getBoundingBox(x + minX, bottom + 0.01, z + minZ, x + maxX, bottom + shapeHeight, z + maxZ);

            BOXES.clear();
            int fromX = MathHelper.floor_double(shape.minX), toX = MathHelper.floor_double(shape.maxX);
            int fromY = MathHelper.floor_double(shape.minY), toY = MathHelper.floor_double(shape.maxY);
            int fromZ = MathHelper.floor_double(shape.minZ), toZ = MathHelper.floor_double(shape.maxZ);
            for (int bx = fromX; bx <= toX; bx++) {
                for (int by = fromY; by <= toY; by++) {
                    for (int bz = fromZ; bz <= toZ; bz++) {
                        Block block = world.getBlock(bx, by, bz);
                        if (block.getMaterial() == Material.air) continue;
                        try {
                            block.addCollisionBoxesToList(world, bx, by, bz, shape, BOXES, entity);
                        } catch (RuntimeException ignored) {
                            // a block of a mod that cannot give its boxes here: the item may go into it
                        }
                    }
                }
            }

            // each box pushes the item away from it along the axis it needs to be moved the least
            double towardMinusX = 0, towardPlusX = 0, towardMinusZ = 0, towardPlusZ = 0;
            for (AxisAlignedBB box : BOXES) {
                if (box == null || !box.intersectsWith(shape)) continue;
                double moveX = away(box.minX, box.maxX, x, shape.minX, shape.maxX);
                double moveZ = away(box.minZ, box.maxZ, z, shape.minZ, shape.maxZ);
                if (Double.isNaN(moveX) && Double.isNaN(moveZ)) continue; // the item is inside it: nowhere to go
                if (Double.isNaN(moveZ) || !Double.isNaN(moveX) && Math.abs(moveX) <= Math.abs(moveZ)) {
                    if (moveX < 0) towardMinusX = Math.min(towardMinusX, moveX);
                    else towardPlusX = Math.max(towardPlusX, moveX);
                } else {
                    if (moveZ < 0) towardMinusZ = Math.min(towardMinusZ, moveZ);
                    else towardPlusZ = Math.max(towardPlusZ, moveZ);
                }
            }
            BOXES.clear();
            // walls on both sides of a narrow gap: the item stays between them
            double limitX = (maxX - minX) / 2;
            double limitZ = (maxZ - minZ) / 2;
            targetPushX = clamp(towardMinusX + towardPlusX, limitX);
            targetPushZ = clamp(towardMinusZ + towardPlusZ, limitZ);
        }

        /**
         * How far to move the item along one axis to get out of a box: negative away from a box on the plus side,
         * positive from one on the minus side, NaN if the box is over the middle of the item on this axis.
         */
        private static double away(double boxMin, double boxMax, double middle, double shapeMin, double shapeMax) {
            if (boxMin >= middle) return boxMin - shapeMax;
            if (boxMax <= middle) return boxMax - shapeMin;
            return Double.NaN;
        }

        private static double clamp(double value, double limit) {
            return Math.max(-limit, Math.min(limit, value));
        }
    }
}
