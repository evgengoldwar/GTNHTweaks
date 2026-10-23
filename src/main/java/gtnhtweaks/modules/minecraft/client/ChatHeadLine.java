package gtnhtweaks.modules.minecraft.client;

/** A line of the chat with the writer of its message, added to ChatLine by a mixin. */
public interface ChatHeadLine {

    /** @param head true for the first line of the message, which shows the head */
    void gtnhtweaks$setWriter(String writer, boolean head);

    /** The writer of the message, or null for a message without a head. */
    String gtnhtweaks$getWriter();

    boolean gtnhtweaks$hasHead();
}
