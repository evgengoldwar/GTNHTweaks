package gtnhtweaks.modules.minecraft.client;

/** Added to every GuiSlot by a mixin: the scroll and the header of the lists of the resource pack screen. */
public interface PackListSlot {

    float gtnhtweaks$getScrolled();

    void gtnhtweaks$setScrolled(float scrolled);

    /** Height of the header above the first row: the title of the resource pack lists. */
    int gtnhtweaks$getHeaderPadding();
}
