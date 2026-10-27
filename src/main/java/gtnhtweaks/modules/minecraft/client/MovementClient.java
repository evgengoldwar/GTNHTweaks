package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import gtnhtweaks.modules.minecraft.movement.Movement;
import gtnhtweaks.modules.minecraft.movement.MovementState;
import gtnhtweaks.modules.minecraft.movement.Pose;

/**
 * The client side of the crouching and swimming of newer versions: the poses of every player in the world, the
 * swimming of the own player, its lowered camera, and the players drawn lying in the water with the strokes of their
 * arms. Does nothing on a server that did not allow the poses.
 */
public final class MovementClient {

    private static final float PI = (float) Math.PI;
    /** Ticks a second press of the forward key starts sprinting, as on the ground. */
    private static final int DOUBLE_TAP_TICKS = 7;

    /** The partial tick of the player being drawn, for its model. */
    private static float renderPartialTicks;
    /** The arm of the own player is drawn in the first person view, with no pose of the body. */
    private static boolean firstPersonArm;
    private static boolean forwardWasDown;
    private static int tapTimer;

    private MovementClient() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MovementClient());
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Movement.resetClient();
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Movement.resetClient();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !event.player.worldObj.isRemote) return;
        EntityPlayer player = event.player;
        Movement.updateOnClient(player);
        MovementState state = Movement.state(player);
        state.prevSwim = state.swim;
        state.swim = state.pose == Pose.SWIM ? Math.min(1, state.swim + 0.09F) : Math.max(0, state.swim - 0.09F);
        if (player instanceof EntityPlayerSP && player == Minecraft.getMinecraft().thePlayer) {
            moveCamera((EntityPlayerSP) player, state);
            swim((EntityPlayerSP) player, state);
        }
    }

    /** The camera follows the eyes of the pose, smoothly as in newer versions. */
    private static void moveCamera(EntityPlayerSP player, MovementState state) {
        // vanilla lowers the camera of the sneaking own player a little by itself
        float dip = player.isSneaking() ? 0.08F : 0;
        float target = state.pose == Pose.STAND ? 0 : Math.max(0, Pose.STAND.eye - state.pose.eye - dip);
        state.prevDrop = state.drop;
        state.drop += (target - state.drop) * 0.5F;
        if (Math.abs(target - state.drop) < 0.001F) state.drop = target;
    }

    /**
     * Swimming of newer versions: less drag in the water, so faster, and up or down where the player looks. Before
     * the movement of vanilla in the tick, which then moves the player as usual.
     */
    private static void swim(EntityPlayerSP player, MovementState state) {
        if (!Movement.clientSwim || !player.isInWater() || player.capabilities.isFlying) return;
        // sneaking in water sinks, as in newer versions
        if (player.movementInput.sneak) player.motionY -= 0.04;
        if (!state.swimming) return;
        // vanilla keeps 0.8 of the speed in water each tick, swimming keeps 0.9
        player.motionX *= 0.9 / 0.8;
        player.motionZ *= 0.9 / 0.8;
        double look = player.getLookVec().yCoord;
        double rate = look < -0.2 ? 0.085 : 0.06;
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.boundingBox.minY + 0.9);
        int z = MathHelper.floor_double(player.posZ);
        boolean waterAbove = player.worldObj.getBlock(x, y, z)
            .getMaterial() == Material.water;
        // looking up at the surface it does not leave the water by itself
        if (look <= 0 || player.movementInput.jump || waterAbove) player.motionY += (look - player.motionY) * rate;
    }

    /** Double pressing the forward key starts sprinting, so swimming, also under water and not only on the ground. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (tapTimer > 0) tapTimer--;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !Movement.clientSwim) return;
        boolean down = mc.gameSettings.keyBindForward.getIsKeyPressed();
        if (down && !forwardWasDown
            && !player.isSprinting()
            && player.isInWater()
            && player.isInsideOfMaterial(Material.water)) {
            if (tapTimer > 0) player.setSprinting(true);
            else tapTimer = DOUBLE_TAP_TICKS;
        }
        forwardWasDown = down;
    }

    /** Around the drawing of the arm of the own player in the first person view. */
    public static void drawingFirstPersonArm(boolean drawing) {
        firstPersonArm = drawing;
    }

    /**
     * Whether the own player keeps sprinting where vanilla stops it. While swimming, newer versions stop the sprint
     * only out of the water, or when the player no longer swims forward off the ground: running into a block, a wall
     * or the shore keeps it swimming.
     */
    public static boolean keepSprinting(EntityPlayerSP player) {
        MovementState state = Movement.state(player);
        if (!state.swimming || !player.isInWater()) return false;
        boolean canSprint = player.getFoodStats()
            .getFoodLevel() > 6 || player.capabilities.allowFlying;
        boolean forward = player.movementInput.moveForward > 1.0E-5F;
        return player.onGround || player.movementInput.sneak || forward && canSprint;
    }

    /** How far the camera of the own player is lowered under the camera of vanilla. */
    public static float cameraDrop(Entity viewer, float partialTicks) {
        if (!(viewer instanceof EntityPlayerSP)) return 0;
        return Movement.state((EntityPlayer) viewer)
            .drop(partialTicks);
    }

    /**
     * Turns a swimming player to lie in the water, as newer versions do: called where the body is turned to the side
     * it faces, before the model is drawn.
     */
    public static void rotateBody(EntityLivingBase entity, float partialTicks) {
        renderPartialTicks = partialTicks;
        if (!(entity instanceof EntityPlayer)) return;
        MovementState state = Movement.state((EntityPlayer) entity);
        float swim = state.swim(partialTicks);
        if (swim <= 0) return;
        float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
        // in the water it lies along where it looks, on land flat
        float lying = entity.isInWater() ? -90 - pitch : -90;
        glRotatef(swim * lying, 1, 0, 0);
        if (state.pose == Pose.SWIM) glTranslatef(0, -1, 0.3F);
    }

    /** Before the model of a player is posed: the crouching pose bends it even without the sneak key. */
    public static void beforePose(ModelBiped model, Entity entity) {
        if (!(entity instanceof EntityPlayer)) return;
        if (firstPersonArm) {
            // the arm in the hand view is never bent by the crouching
            model.isSneak = false;
            return;
        }
        Pose pose = Movement.state((EntityPlayer) entity).pose;
        if (pose == Pose.CROUCH) model.isSneak = true;
        else if (pose == Pose.SWIM) model.isSneak = false;
    }

    /** After the model of a player is posed: the head and the strokes of the arms and legs of swimming. */
    public static void afterPose(ModelBiped model, float limbSwing, Entity entity) {
        if (firstPersonArm || !(entity instanceof EntityPlayer)) return;
        MovementState state = Movement.state((EntityPlayer) entity);
        float swim = state.swim(renderPartialTicks);
        if (swim <= 0) return;
        // looks ahead along the swimming or crawling, not at the ground
        if (state.pose == Pose.SWIM) model.bipedHead.rotateAngleX = turn(model.bipedHead.rotateAngleX, -PI / 4, swim);
        model.bipedHeadwear.rotateAngleX = model.bipedHead.rotateAngleX;

        // a stroke takes 26 steps of the walking animation: arms forward over the head, out to the sides and back
        float step = limbSwing % 26;
        // a hand that hits does its swing instead of the stroke
        float right = model.onGround > 0 ? 0 : swim;
        float leftZ, rightZ, armX;
        if (step < 14) {
            armX = 0;
            float spread = 1.8707964F * armAngle(step) / armAngle(14);
            leftZ = PI + spread;
            rightZ = PI - spread;
        } else if (step < 22) {
            float f = (step - 14) / 8;
            armX = PI / 2 * f;
            leftZ = 5.012389F - 1.8707964F * f;
            rightZ = 1.2707963F + 1.8707964F * f;
        } else {
            float f = (step - 22) / 4;
            armX = PI / 2 - PI / 2 * f;
            leftZ = PI;
            rightZ = PI;
        }
        model.bipedLeftArm.rotateAngleX = turn(model.bipedLeftArm.rotateAngleX, armX, swim);
        model.bipedRightArm.rotateAngleX = lerp(right, model.bipedRightArm.rotateAngleX, armX);
        model.bipedLeftArm.rotateAngleY = turn(model.bipedLeftArm.rotateAngleY, PI, swim);
        model.bipedRightArm.rotateAngleY = lerp(right, model.bipedRightArm.rotateAngleY, PI);
        model.bipedLeftArm.rotateAngleZ = turn(model.bipedLeftArm.rotateAngleZ, leftZ, swim);
        model.bipedRightArm.rotateAngleZ = lerp(right, model.bipedRightArm.rotateAngleZ, rightZ);
        // the legs kick
        model.bipedLeftLeg.rotateAngleX = lerp(
            swim,
            model.bipedLeftLeg.rotateAngleX,
            0.3F * MathHelper.cos(limbSwing * 0.33333334F + PI));
        model.bipedRightLeg.rotateAngleX = lerp(
            swim,
            model.bipedRightLeg.rotateAngleX,
            0.3F * MathHelper.cos(limbSwing * 0.33333334F));
    }

    private static float armAngle(float step) {
        return -65 * step + step * step;
    }

    private static float lerp(float amount, float from, float to) {
        return from + amount * (to - from);
    }

    /** Turns an angle toward another the short way round. */
    private static float turn(float from, float to, float amount) {
        float difference = (to - from) % (PI * 2);
        if (difference < -PI) difference += PI * 2;
        if (difference >= PI) difference -= PI * 2;
        return from + amount * difference;
    }
}
