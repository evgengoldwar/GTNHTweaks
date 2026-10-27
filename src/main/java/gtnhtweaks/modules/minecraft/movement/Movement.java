package gtnhtweaks.modules.minecraft.movement;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import gtnhtweaks.modules.minecraft.MinecraftServerModule;

/**
 * Crouching and swimming of newer versions, the same on the server and on the client: the pose of a player follows
 * from its sneaking and sprinting, the water and the room around it, and sets the height of its box and its eyes.
 * <p>
 * The server allows the poses ({@link MinecraftServerModule}) and only for the players whose client has them too, so
 * both move the player in the same box; the client takes the poses only on a server that allowed them.
 */
public final class Movement {

    /** The players whose client answered the rules of the server. */
    static final Set<UUID> READY = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** What the server the client is on allows, nothing until it tells. */
    public static volatile boolean clientCrouch, clientSwim;

    private Movement() {}

    public static MovementState state(EntityPlayer player) {
        return ((MovementHolder) player).gtnhtweaks$movement();
    }

    /** Called on the server for every player, before its movement is taken. */
    public static void updateOnServer(EntityPlayer player) {
        boolean ready = MinecraftServerModule.INSTANCE.isEnabled() && READY.contains(player.getUniqueID());
        update(player, ready && MinecraftServerModule.crouch, ready && MinecraftServerModule.swim);
    }

    /** Called on the client for every player in the world, before it moves. */
    public static void updateOnClient(EntityPlayer player) {
        update(player, clientCrouch, clientSwim);
    }

    /** Forgets what the client was allowed, when it leaves the server. */
    public static void resetClient() {
        clientCrouch = false;
        clientSwim = false;
    }

    static void forget(UUID player) {
        READY.remove(player);
    }

    private static void update(EntityPlayer player, boolean crouch, boolean swim) {
        MovementState state = state(player);
        if (crouch || swim) state.pose = choose(player, state, crouch, swim);
        else {
            state.pose = Pose.STAND;
            state.swimming = false;
        }
        resize(player, state);
    }

    /** The pose as newer versions choose it: the swimming, then the room around the player. */
    private static Pose choose(EntityPlayer player, MovementState state, boolean crouch, boolean swim) {
        state.swimming = swim && swimming(player, state);
        // where not even the swimming pose fits, the pose stays as it is
        if (!fits(player, Pose.SWIM)) return state.pose;
        Pose wanted;
        if (player.isPlayerSleeping()) wanted = Pose.STAND;
        else if (state.swimming) wanted = Pose.SWIM;
        else if (crouch && player.isSneaking() && !player.capabilities.isFlying) wanted = Pose.CROUCH;
        else wanted = Pose.STAND;
        if (player.isRiding() || fits(player, wanted)) return wanted;
        // no room for the pose: crouches if that fits, else crawls
        if (crouch && fits(player, Pose.CROUCH)) return Pose.CROUCH;
        return swim ? Pose.SWIM : wanted;
    }

    /**
     * Swimming of newer versions: starts sprinting with the eyes under water and the feet in a water block, goes on
     * while sprinting in water. Not while flying or riding. Crawling in a low gap is the same pose but not swimming.
     */
    private static boolean swimming(EntityPlayer player, MovementState state) {
        if (player.capabilities.isFlying || player.isRiding() || !player.isSprinting()) return false;
        if (state.swimming) return player.isInWater();
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.boundingBox.minY);
        int z = MathHelper.floor_double(player.posZ);
        return eyesInWater(player) && player.worldObj.getBlock(x, y, z)
            .getMaterial() == Material.water;
    }

    /**
     * Whether the eyes of the pose are under the water surface. Counted from the bottom of the box, the same on the
     * server and on the client, whose players have their eyes at other heights in vanilla.
     */
    public static boolean eyesInWater(EntityPlayer player) {
        double eye = player.boundingBox.minY + state(player).pose.eye;
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(eye);
        int z = MathHelper.floor_double(player.posZ);
        return isWater(player.worldObj, x, y, z) && eye <= y + waterHeight(player.worldObj, x, y, z);
    }

    /**
     * Whether water touches the box of the player, as newer versions tell a player in water: anywhere in the box, not
     * only in its middle as vanilla checks it, which misses the water around the low box of the swimming pose.
     */
    public static boolean touchingWater(EntityPlayer player) {
        AxisAlignedBB box = player.boundingBox.contract(0.001, 0.001, 0.001);
        int minX = MathHelper.floor_double(box.minX), maxX = MathHelper.ceiling_double_int(box.maxX);
        int minY = MathHelper.floor_double(box.minY), maxY = MathHelper.ceiling_double_int(box.maxY);
        int minZ = MathHelper.floor_double(box.minZ), maxZ = MathHelper.ceiling_double_int(box.maxZ);
        World world = player.worldObj;
        for (int x = minX; x < maxX; x++) for (int y = minY; y < maxY; y++) for (int z = minZ; z < maxZ; z++) {
            if (isWater(world, x, y, z) && y + waterHeight(world, x, y, z) >= box.minY) return true;
        }
        return false;
    }

    private static boolean isWater(World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .getMaterial() == Material.water;
    }

    /**
     * Height of the water in a block, as newer versions take it: full under more water, else 8/9 for a source or
     * falling water and less for each step it flowed.
     */
    private static double waterHeight(World world, int x, int y, int z) {
        if (isWater(world, x, y + 1, z)) return 1;
        int meta = world.getBlockMetadata(x, y, z);
        int amount = meta >= 8 ? 8 : 8 - meta;
        return amount / 9.0;
    }

    /** Whether the box of the pose is free of blocks where the player is. */
    private static boolean fits(EntityPlayer player, Pose pose) {
        AxisAlignedBB box = player.boundingBox;
        AxisAlignedBB posed = AxisAlignedBB
            .getBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.minY + pose.height, box.maxZ)
            .contract(1.0E-7, 1.0E-7, 1.0E-7);
        return player.worldObj.getCollidingBoundingBoxes(player, posed)
            .isEmpty();
    }

    private static void resize(EntityPlayer player, MovementState state) {
        if (state.pose == Pose.STAND) {
            if (!state.resized) return;
            state.resized = false;
        } else state.resized = true;
        float height = state.pose.height;
        if (player.height == height) return;
        player.height = height;
        player.boundingBox.maxY = player.boundingBox.minY + height;
    }

    /** The own player on the client, set by the client side; null on a server. */
    public static EntityPlayer clientPlayer;
    /** How far the position of the own player is lowered to its eyes while a frame is drawn, see MovementClient. */
    public static float renderShift;

    /**
     * How much lower the eyes of the player are than vanilla has them, for the eye height of the game: through it the
     * server, the suffocation and the blocks the own player looks at all take the eyes of the pose.
     */
    public static float eyeDrop(EntityPlayer player) {
        Pose pose = state(player).pose;
        float drop = pose == Pose.STAND ? 0 : Pose.STAND.eye - pose.eye;
        if (player == clientPlayer) {
            // vanilla lowers the own sneaking player a little by itself, and a frame lowers it to its eyes
            if (pose != Pose.STAND && player.isSneaking()) drop -= SNEAK_DIP;
            drop -= renderShift;
        }
        return drop;
    }

    /** How far vanilla lowers the own sneaking player on the client. */
    public static final float SNEAK_DIP = 0.08F;

    /**
     * Whether the eyes of the player in its pose are in a full block, as the suffocation of vanilla checks them at the
     * height of standing eyes.
     */
    public static boolean eyesInBlock(EntityPlayer player) {
        float eye = (float) player.boundingBox.minY + state(player).pose.eye;
        for (int i = 0; i < 8; i++) {
            float dx = ((i & 1) - 0.5F) * player.width * 0.8F;
            float dy = ((i >> 1 & 1) - 0.5F) * 0.1F;
            float dz = ((i >> 2 & 1) - 0.5F) * player.width * 0.8F;
            int x = MathHelper.floor_double(player.posX + dx);
            int y = MathHelper.floor_float(eye + dy);
            int z = MathHelper.floor_double(player.posZ + dz);
            if (player.worldObj.getBlock(x, y, z)
                .isNormalCube()) return true;
        }
        return false;
    }
}
