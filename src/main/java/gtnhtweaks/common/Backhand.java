package gtnhtweaks.common;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import gtnhtweaks.GTNHTweaks;

/** The off hand of the Backhand mod, through its API found by reflection. Without Backhand there is no off hand. */
public final class Backhand {

    private static final String UTILS = "xonin.backhand.api.core.BackhandUtils";

    private static boolean looked;
    private static MethodHandle offhandItem;
    private static MethodHandle offhandSlot;

    private Backhand() {}

    /**
     * Whether the stack is drawn in the off hand of the player. In first person Backhand draws the off hand by
     * selecting its slot for a moment; in third person it is the stack of that slot.
     */
    public static boolean isOffhand(EntityPlayer player, ItemStack stack) {
        if (!find()) return false;
        try {
            if (player.inventory.currentItem == (int) offhandSlot.invokeExact(player)) return true;
            return stack != null && stack == (ItemStack) offhandItem.invokeExact(player)
                && stack != player.inventory.getCurrentItem();
        } catch (Throwable e) {
            GTNHTweaks.LOG.warn("Failed to read the off hand of Backhand", e);
            offhandItem = null;
            return false;
        }
    }

    /** The stack in the off hand, or null without Backhand. */
    public static ItemStack offhandItem(EntityPlayer player) {
        if (!find()) return null;
        try {
            return (ItemStack) offhandItem.invokeExact(player);
        } catch (Throwable e) {
            GTNHTweaks.LOG.warn("Failed to read the off hand of Backhand", e);
            offhandItem = null;
            return null;
        }
    }

    /** The inventory slot of the off hand, or -1 without Backhand. */
    public static int offhandSlot(EntityPlayer player) {
        if (!find()) return -1;
        try {
            return (int) offhandSlot.invokeExact(player);
        } catch (Throwable e) {
            GTNHTweaks.LOG.warn("Failed to read the off hand of Backhand", e);
            offhandItem = null;
            return -1;
        }
    }

    private static boolean find() {
        if (!looked) {
            looked = true;
            if (Loader.isModLoaded("backhand")) {
                try {
                    Class<?> utils = Class.forName(UTILS);
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                    offhandItem = lookup.findStatic(
                        utils,
                        "getOffhandItem",
                        MethodType.methodType(ItemStack.class, EntityPlayer.class));
                    offhandSlot = lookup
                        .findStatic(utils, "getOffhandSlot", MethodType.methodType(int.class, EntityPlayer.class));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    GTNHTweaks.LOG.warn("Backhand is loaded but its off hand API was not found", e);
                    offhandItem = null;
                }
            }
        }
        return offhandItem != null && offhandSlot != null;
    }
}
