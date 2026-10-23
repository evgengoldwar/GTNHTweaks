package gtnhtweaks.modules.minecraft.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * Heads of the players before their messages in the chat.
 * <p>
 * A chat message of 1.7.10 is only text, so the writer is found in it: the last name of an online player before the
 * first {@code >}, {@code :} or {@code »}, as in {@code <Name> hi} or {@code [Admin] Name: hi}. Messages without
 * such a name, like the ones of the server, get no head. The skin is the one of the player entity when it is in the
 * world, else the one downloaded by name, the same way the tab list of ServerUtilities does.
 */
public final class ChatHeads {

    /** Space of a head before the text, in chat pixels. */
    public static final int SPACE = 10;
    private static final int SIZE = 8;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{2,16}");

    /**
     * The writer of a message, kept for the message, also while the chat is wrapped again. By identity: hashCode of
     * a chat component can throw for a style without a color, a bug of vanilla. The chat keeps at most a few hundred
     * messages, so the oldest are forgotten after a while.
     */
    private static final Map<Message, String> WRITERS = new LinkedHashMap<Message, String>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Message, String> eldest) {
            return size() > 2048;
        }
    };
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** The writer of the message being split into lines, and whether its first line was made. */
    private static String current;
    private static boolean firstMade;

    /** Rows of the chat on the screen, from the bottom, with a head or after one: drawn in the last frame. */
    private static final boolean[] SHIFTED_ROWS = new boolean[256];

    private ChatHeads() {}

    // ---- lines of a message

    /** A message is split into lines: finds its writer. */
    public static void beginMessage(IChatComponent message) {
        Message key = new Message(message);
        String writer;
        if (WRITERS.containsKey(key)) writer = WRITERS.get(key);
        else {
            writer = findWriter(message);
            WRITERS.put(key, writer);
        }
        current = writer;
        firstMade = false;
    }

    public static void endMessage() {
        current = null;
    }

    /** The space for the head, taken from the width the message is wrapped to. */
    public static float wrapWidth(float width) {
        return current == null ? width : Math.max(SIZE, width - SPACE);
    }

    /** A new line of the current message: the first one gets the head, the others its space. */
    public static void lineMade(ChatHeadLine line) {
        if (current == null) return;
        line.gtnhtweaks$setWriter(current, !firstMade);
        firstMade = true;
    }

    /** A message compared by identity. */
    private static final class Message {

        private final IChatComponent component;

        Message(IChatComponent component) {
            this.component = component;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Message && ((Message) other).component == component;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(component);
        }
    }

    // ---- the writer

    static String findWriter(IChatComponent message) {
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(message.getUnformattedText());
        if (text == null) return null;
        int end = -1;
        for (int i = 0; i < Math.min(text.length(), 80); i++) {
            char c = text.charAt(i);
            if (c == '>' || c == ':' || c == '»') {
                end = i;
                break;
            }
        }
        if (end <= 0) return null;
        Map<String, String> online = onlinePlayers();
        String writer = null;
        Matcher matcher = NAME.matcher(text.substring(0, end));
        while (matcher.find()) {
            String name = online.get(matcher.group().toLowerCase(Locale.ROOT));
            // the last one: the nearest to the message, after ranks and tags
            if (name != null) writer = name;
        }
        return writer;
    }

    /** Names of the players on the server by their lower case. */
    private static Map<String, String> onlinePlayers() {
        Map<String, String> names = new HashMap<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return names;
        names.put(
            mc.thePlayer.getCommandSenderName()
                .toLowerCase(Locale.ROOT),
            mc.thePlayer.getCommandSenderName());
        NetHandlerPlayClient handler = mc.thePlayer.sendQueue;
        if (handler != null) {
            @SuppressWarnings("unchecked")
            List<GuiPlayerInfo> players = handler.playerInfoList;
            for (GuiPlayerInfo info : players) {
                String name = EnumChatFormatting.getTextWithoutFormattingCodes(info.name);
                if (name != null && !name.isEmpty()) names.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return names;
    }

    // ---- drawing

    public static void beginFrame() {
        Arrays.fill(SHIFTED_ROWS, false);
    }

    /**
     * A line of the chat is drawn at x, y with the alpha of its color: draws the head of its first line, returns
     * where its text starts.
     */
    public static int drawLine(ChatHeadLine line, int x, int y, int color, int row) {
        String writer = line == null ? null : line.gtnhtweaks$getWriter();
        if (writer == null) return x;
        if (row >= 0 && row < SHIFTED_ROWS.length) SHIFTED_ROWS[row] = true;
        if (line.gtnhtweaks$hasHead()) {
            int alpha = color >>> 24;
            if (alpha > 3) drawHead(writer, x, y, alpha / 255F);
        }
        return x + SPACE;
    }

    /** Whether the text of the row is moved by the head, for the text under the mouse. */
    public static boolean shiftedRow(int row) {
        return row >= 0 && row < SHIFTED_ROWS.length && SHIFTED_ROWS[row];
    }

    private static void drawHead(String writer, int x, int y, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(skin(writer));
        // skins of 64x64, as uploaded by skin backports, or of 64x32
        int textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        float tileWidth = Math.max(64, textureWidth);
        float tileHeight = Math.max(32, textureHeight);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, alpha);
        // the face, then the hat layer over it
        Gui.func_152125_a(x, y, 8, 8, 8, 8, SIZE, SIZE, tileWidth, tileHeight);
        Gui.func_152125_a(x, y, 40, 8, 8, 8, SIZE, SIZE, tileWidth, tileHeight);
        GL11.glColor4f(1, 1, 1, 1);
        if (!blend) GL11.glDisable(GL11.GL_BLEND);
    }

    private static ResourceLocation skin(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            EntityPlayer player = mc.theWorld.getPlayerEntityByName(name);
            if (player instanceof AbstractClientPlayer) {
                ResourceLocation live = ((AbstractClientPlayer) player).getLocationSkin();
                if (live != null) {
                    SKINS.put(name, live);
                    return live;
                }
            }
        }
        ResourceLocation skin = SKINS.get(name);
        if (skin == null) {
            skin = AbstractClientPlayer.getLocationSkin(name);
            AbstractClientPlayer.getDownloadImageSkin(skin, name);
            SKINS.put(name, skin);
        }
        return skin;
    }
}
