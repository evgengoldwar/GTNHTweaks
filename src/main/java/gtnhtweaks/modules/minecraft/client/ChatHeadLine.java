package gtnhtweaks.modules.minecraft.client;

/** A line of the chat with the writer of its message, added to ChatLine by a mixin. */
public interface ChatHeadLine {

    /** Not a line of a message split by the chat: drawn as it is. */
    int PLAIN = 0;
    /** The first line of a message of a player: the head before it. */
    int HEAD = 1;
    /** A next line of a message of a player: the space of the head before it. */
    int AFTER_HEAD = 2;
    /** A line of a message not written by a player: a bar before it. */
    int SYSTEM = 3;

    void gtnhtweaks$setWriter(String writer, int kind);

    /** The writer of the message, or null for a message without a head. */
    String gtnhtweaks$getWriter();

    int gtnhtweaks$getKind();
}
