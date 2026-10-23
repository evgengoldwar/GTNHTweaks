package gtnhtweaks.modules.gregtech.client;

import java.util.Locale;

import net.minecraft.util.ResourceLocation;

/** The tool overlay icons of GregTech CEu Modern (LGPL-3.0), in assets/gtnhtweaks/textures/gui/overlay. */
public enum ToolIcon {

    ALLOW_INPUT,
    ATTACH_COVER,
    AUTO_OUTPUT,
    COVER_SETTINGS,
    DISABLE_AUTO_OUTPUT,
    FRONT_FACING_ROTATION,
    IO_FACING_ROTATION,
    MUTE,
    PAUSE,
    PIPE_BLOCK,
    PIPE_CONNECT,
    REMOVE_COVER,
    SOUND,
    START,
    WIRE_BLOCK,
    WIRE_CONNECT;

    public final ResourceLocation texture = new ResourceLocation(
        "gtnhtweaks",
        "textures/gui/overlay/tool_" + name().toLowerCase(Locale.ROOT) + ".png");
}
