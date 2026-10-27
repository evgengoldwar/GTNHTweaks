package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.opengl.GL20;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * The background of the screens opened in a world: the dark gradient of vanilla with a darkness of its own, or the
 * world blurred behind the screen, as in newer versions.
 * <p>
 * The blur takes the picture of the game as it is when the screen starts drawing, the world and the HUD, and blurs it
 * at half the resolution in two passes of a gaussian blur, across and down. It is made once per frame: a screen that
 * draws its background again in the same frame gets the same picture, not a blur of itself.
 */
public final class ScreenBackground {

    /** Vanilla gradient: 0xC0101010 at the top, 0xD0101010 at the bottom. */
    private static final int VANILLA_TOP_ALPHA = 0xC0;
    private static final int VANILLA_BOTTOM_ALPHA = 0xD0;
    private static final int SHADE = 0x101010;
    /** The largest blur radius in samples, the bound of the loop of the shader. */
    private static final int MAX_RADIUS = 20;

    private static final String VERTEX = "#version 120\n" + "varying vec2 uv;\n"
        + "void main() {\n"
        + "    gl_Position = ftransform();\n"
        + "    uv = gl_MultiTexCoord0.xy;\n"
        + "}\n";
    private static final String FRAGMENT = "#version 120\n" + "uniform sampler2D source;\n"
        + "uniform vec2 step;\n"
        + "uniform float radius;\n"
        + "varying vec2 uv;\n"
        + "void main() {\n"
        + "    float sigma = max(radius / 2.0, 0.5);\n"
        + "    vec3 sum = vec3(0.0);\n"
        + "    float total = 0.0;\n"
        + "    for (int i = -"
        + MAX_RADIUS
        + "; i <= "
        + MAX_RADIUS
        + "; i++) {\n"
        + "        float x = float(i);\n"
        + "        if (abs(x) > radius) continue;\n"
        + "        float w = exp(-x * x / (2.0 * sigma * sigma));\n"
        + "        sum += texture2D(source, uv + step * x).rgb * w;\n"
        + "        total += w;\n"
        + "    }\n"
        + "    gl_FragColor = vec4(sum / total, 1.0);\n"
        + "}\n";

    private static Framebuffer across, down;
    private static int program;
    private static int stepUniform, radiusUniform, sourceUniform;
    /** Set when the shader could not be made: the blur falls back to the gradient. */
    private static boolean broken;
    private static boolean blurredThisFrame;

    private ScreenBackground() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ScreenBackground.FrameTracker());
    }

    public static final class FrameTracker {

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) blurredThisFrame = false;
        }
    }

    /** Draws the background of a screen over the world. False to leave the vanilla gradient. */
    public static boolean draw(int width, int height) {
        if (MinecraftModule.backgroundMode == MinecraftModule.BackgroundMode.BLUR && canBlur()) {
            if (!blurredThisFrame) {
                blurredThisFrame = blur();
                if (!blurredThisFrame) return drawDim(width, height, MinecraftModule.darkness);
            }
            drawBlurred(width, height);
            drawDim(width, height, MinecraftModule.blurDarkness);
            return true;
        }
        if (MinecraftModule.darkness == MinecraftModule.VANILLA_DARKNESS) return false;
        return drawDim(width, height, MinecraftModule.darkness);
    }

    private static boolean canBlur() {
        return !broken && OpenGlHelper.isFramebufferEnabled() && OpenGlHelper.shadersSupported;
    }

    /** The vanilla gradient with its alpha scaled to the darkness in percent. Always true: nothing else to draw. */
    private static boolean drawDim(int width, int height, int darkness) {
        if (darkness <= 0) return true;
        float factor = darkness / (float) MinecraftModule.VANILLA_DARKNESS;
        int top = Math.min(255, Math.round(VANILLA_TOP_ALPHA * factor));
        int bottom = Math.min(255, Math.round(VANILLA_BOTTOM_ALPHA * factor));
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        glShadeModel(GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(SHADE, top);
        tessellator.addVertex(width, 0, 0);
        tessellator.addVertex(0, 0, 0);
        tessellator.setColorRGBA_I(SHADE, bottom);
        tessellator.addVertex(0, height, 0);
        tessellator.addVertex(width, height, 0);
        tessellator.draw();
        glShadeModel(GL_FLAT);
        glDisable(GL_BLEND);
        glEnable(GL_ALPHA_TEST);
        glEnable(GL_TEXTURE_2D);
        return true;
    }

    /** Blurs the picture of the game into {@link #down}. False if it could not be done. */
    private static boolean blur() {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        if (program == 0 && !makeProgram()) return false;
        int w = Math.max(1, main.framebufferWidth / 2);
        int h = Math.max(1, main.framebufferHeight / 2);
        across = resize(across, w, h);
        down = resize(down, w, h);

        int oldProgram = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        glPushAttrib(
            GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT
                | GL_CURRENT_BIT
                | GL_DEPTH_BUFFER_BIT
                | GL_TEXTURE_BIT
                | GL_VIEWPORT_BIT
                | GL_TRANSFORM_BIT);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glDepthMask(false);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1F, 1F, 1F, 1F);

        GL20.glUseProgram(program);
        GL20.glUniform1i(sourceUniform, 0);
        // radius in samples of the half resolution: 2 to 20
        GL20.glUniform1f(radiusUniform, Math.min(MAX_RADIUS, MinecraftModule.blurStrength * 2F));
        pass(main, across, true);
        pass(across, down, false);
        GL20.glUseProgram(oldProgram);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glPopAttrib();
        main.bindFramebuffer(true);
        return true;
    }

    /** Draws the source over the whole target, blurred across or down, a step being a pixel of the target. */
    private static void pass(Framebuffer source, Framebuffer target, boolean acrossPass) {
        float uMax = source.framebufferWidth / (float) source.framebufferTextureWidth;
        float vMax = source.framebufferHeight / (float) source.framebufferTextureHeight;
        target.bindFramebuffer(true);
        glBindTexture(GL_TEXTURE_2D, source.framebufferTexture);
        if (acrossPass) GL20.glUniform2f(stepUniform, uMax / target.framebufferWidth, 0F);
        else GL20.glUniform2f(stepUniform, 0F, vMax / target.framebufferHeight);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(-1, -1, 0, 0, 0);
        tessellator.addVertexWithUV(1, -1, 0, uMax, 0);
        tessellator.addVertexWithUV(1, 1, 0, uMax, vMax);
        tessellator.addVertexWithUV(-1, 1, 0, 0, vMax);
        tessellator.draw();
    }

    /** Draws the blurred picture over the screen, in the coordinates of the GUI. */
    private static void drawBlurred(int width, int height) {
        float uMax = down.framebufferWidth / (float) down.framebufferTextureWidth;
        float vMax = down.framebufferHeight / (float) down.framebufferTextureHeight;
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glColor4f(1F, 1F, 1F, 1F);
        glBindTexture(GL_TEXTURE_2D, down.framebufferTexture);
        // the texture of a framebuffer starts at the bottom
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, height, 0, 0, 0);
        tessellator.addVertexWithUV(width, height, 0, uMax, 0);
        tessellator.addVertexWithUV(width, 0, 0, uMax, vMax);
        tessellator.addVertexWithUV(0, 0, 0, 0, vMax);
        tessellator.draw();
        glEnable(GL_ALPHA_TEST);
    }

    private static Framebuffer resize(Framebuffer framebuffer, int w, int h) {
        Framebuffer main = Minecraft.getMinecraft()
            .getFramebuffer();
        if (framebuffer == null) {
            framebuffer = new Framebuffer(w, h, false);
            framebuffer.setFramebufferFilter(GL_LINEAR);
        } else if (framebuffer.framebufferWidth != w || framebuffer.framebufferHeight != h) {
            framebuffer.createBindFramebuffer(w, h);
            framebuffer.setFramebufferFilter(GL_LINEAR);
        } else {
            return framebuffer;
        }
        // making a framebuffer binds it
        main.bindFramebuffer(true);
        return framebuffer;
    }

    private static boolean makeProgram() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vertex == 0 || fragment == 0) return fail(null);
        int made = GL20.glCreateProgram();
        GL20.glAttachShader(made, vertex);
        GL20.glAttachShader(made, fragment);
        GL20.glLinkProgram(made);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(made, GL20.GL_LINK_STATUS) == GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(made, 4096);
            GL20.glDeleteProgram(made);
            return fail(log);
        }
        program = made;
        sourceUniform = GL20.glGetUniformLocation(made, "source");
        stepUniform = GL20.glGetUniformLocation(made, "step");
        radiusUniform = GL20.glGetUniformLocation(made, "radius");
        return true;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL_FALSE) {
            fail(GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static boolean fail(String log) {
        if (!broken && log != null) GTNHTweaks.LOG.warn("The blur of the screen background is off: {}", log);
        broken = true;
        return false;
    }
}
