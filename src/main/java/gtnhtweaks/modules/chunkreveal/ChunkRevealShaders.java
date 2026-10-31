package gtnhtweaks.modules.chunkreveal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import gtnhtweaks.GTNHTweaks;

/**
 * The animation of the chunks drawn by Angelica: written into its terrain shaders as they are read.
 * <p>
 * Celeritas, the renderer of Angelica, knows when every piece of a chunk got its blocks and passes the ages of the
 * pieces of a region to the shader in {@code celeritas_ChunkAges}, the array its own fade in reads. The vertex shader
 * declares that array when the fade in does not, so Celeritas fills it, and moves, scales or hides the vertices of the
 * pieces that appear. The settings come in the uniform {@code gtnht_Params}, set before every region is drawn, so they
 * change without building the shaders again.
 * <p>
 * The shaders of an Angelica shader pack are left alone: with one the chunks appear as usual.
 */
public final class ChunkRevealShaders {

    private static final String VERTEX = "blocks/block_layer_opaque.vsh";

    private static final String MAIN = "void main() {";
    private static final String POSITION = "vec3 position = _vert_position + translation;";
    private static final String GL_POSITION = "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);";

    private static final String VERTEX_CODE = String.join(
        "\n",
        "// GTNHTweaks: chunks appear with an animation",
        "uniform vec4 gtnht_Params; // style, duration in ms, order (0 wave, 1 random), distance in blocks",
        "#if !(defined(USE_FOG) && defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0)",
        "uniform float celeritas_ChunkAges[REGION_SIZE];",
        "#endif",
        "float gtnht_hidden = 0.0;",
        "",
        "float gtnht_outCubic(float t) { float s = 1.0 - t; return 1.0 - s * s * s; }",
        "float gtnht_outBack(float t) { float u = t - 1.0; return 1.0 + 2.70158 * u * u * u + 1.70158 * u * u; }",
        "",
        // position and corner are around the camera
        "vec3 gtnht_animate(vec3 position, vec3 corner) {",
        "    int style = int(gtnht_Params.x + 0.5);",
        "    if (style == 0) return position;",
        "    vec3 center = corner + vec3(8.0);",
        "    float delay = gtnht_Params.z < 0.5",
        "        ? min(length(center), " + ChunkReveal.WAVE_MAX_BLOCKS + ") * " + ChunkReveal.WAVE_MS_PER_BLOCK,
        "        : fract(sin(float(_draw_id) * 91.3458) * 47453.5453) * " + ChunkReveal.RANDOM_MS + ";",
        "    float t = (celeritas_ChunkAges[_draw_id] - delay) / max(gtnht_Params.y, 1.0);",
        "    if (t >= 1.0) return position;",
        "    if (t < 0.0) { gtnht_hidden = 1.0; return position; }",
        "    float d = gtnht_Params.w;",
        "    if (style == 1) position.y -= (1.0 - gtnht_outCubic(t)) * d;",
        "    else if (style == 2) {",
        "        vec2 away = center.xz;",
        "        float length2 = dot(away, away);",
        "        away = length2 > 0.000001 ? away * inversesqrt(length2) : vec2(0.0, 1.0);",
        "        position.xz += away * (1.0 - gtnht_outCubic(t)) * d;",
        "    } else if (style == 3) position = center + (position - center) * max(gtnht_outBack(t), 0.001);",
        "    return position;",
        "}",
        "",
        "");

    private static final String VERTEX_ANIMATE = "\n    position = gtnht_animate(position, translation);";
    private static final String VERTEX_HIDE = "\n    if (gtnht_hidden > 0.5) gl_Position = vec4(0.0);";

    private static boolean warned;

    private ChunkRevealShaders() {}

    /** The source of a shader file of Celeritas or Angelica, with the animation written in when it is a terrain one. */
    public static String patch(String name, String source) {
        if (source == null || name == null) return source;
        try {
            if (name.endsWith(VERTEX)) return patchVertex(source);
        } catch (RuntimeException e) {
            GTNHTweaks.LOG.warn("Could not add the chunk animation to the shader " + name, e);
        }
        return source;
    }

    private static boolean hasVertexMarkers(String source) {
        return source.contains(MAIN) && source.contains(POSITION) && source.contains(GL_POSITION);
    }

    private static String patchVertex(String source) {
        if (!hasVertexMarkers(source)) {
            warnOnce("the terrain vertex shader of Angelica is not the one expected");
            return source;
        }
        String patched = source.replace(MAIN, VERTEX_CODE + MAIN);
        patched = patched.replace(POSITION, POSITION + VERTEX_ANIMATE);
        return patched.replace(GL_POSITION, GL_POSITION + VERTEX_HIDE);
    }

    private static void warnOnce(String why) {
        if (warned) return;
        warned = true;
        GTNHTweaks.LOG.warn("Chunk animation is off with Angelica: " + why);
    }

    /** The location of the settings in the bound terrain program, -1 if it has none. */
    public static int paramsLocation() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        return program == 0 ? -1 : GL20.glGetUniformLocation(program, "gtnht_Params");
    }

    /** Sets the settings in the bound terrain program, before a region is drawn. */
    public static void setParams(int location) {
        if (location < 0) return;
        GL20.glUniform4f(
            location,
            ChunkRevealModule.style.ordinal(),
            Math.max(1, ChunkRevealModule.durationMs),
            ChunkRevealModule.wave ? 0 : 1,
            ChunkRevealModule.distance);
    }
}
