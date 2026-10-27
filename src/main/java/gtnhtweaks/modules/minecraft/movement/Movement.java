package gtnhtweaks.modules.minecraft.movement;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

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
        state.pose = crouch || swim ? choose(player, state, crouch, swim) : Pose.STAND;
        resize(player, state);
    }

    private static Pose choose(EntityPlayer player, MovementState state, boolean crouch, boolean swim) {
        if (player.isDead || player.isPlayerSleeping() || player.isRiding()) return Pose.STAND;
        Pose wanted = Pose.STAND;
        if (!player.capabilities.isFlying) {
            // starts with the eyes under water, goes on while sprinting in water
            if (swim && player.isSprinting()
                && player.isInWater()
                && (state.pose == Pose.SWIM || player.isInsideOfMaterial(Material.water))) wanted = Pose.SWIM;
            else if (crouch && player.isSneaking()) wanted = Pose.CROUCH;
        }
        if (wanted == Pose.SWIM || fits(player, wanted)) return wanted;
        // no room to stand up: stays low
        if (crouch && fits(player, Pose.CROUCH)) return Pose.CROUCH;
        if (swim) return Pose.SWIM;
        return wanted;
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

    /** How much lower the eyes of the player are than standing, for the eye height of the game. */
    public static float eyeDrop(EntityPlayer player) {
        Pose pose = state(player).pose;
        return pose == Pose.STAND ? 0 : Pose.STAND.eye - pose.eye;
    }

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
