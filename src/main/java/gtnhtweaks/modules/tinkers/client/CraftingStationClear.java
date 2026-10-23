package gtnhtweaks.modules.tinkers.client;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Ctrl + Shift + click on the clear button of Crafting Tweaks in a Tinkers' Crafting Station moves the grid into the
 * inventory attached to the side of the station, instead of the player inventory.
 * <p>
 * Done with ordinary clicks on slots, as a player would do by hand, so the server needs nothing: each stack of the
 * grid is picked up, put on the stacks of the same item in the side inventory, then in its empty slots, and what is
 * left goes back to its grid slot.
 */
public final class CraftingStationClear {

    private static final String CONTAINER = "tconstruct.tools.inventory.CraftingStationContainer";
    private static final String TWEAK_BUTTON = "net.blay09.mods.craftingtweaks.client.GuiTweakButton";
    /** Slots of the Crafting Station: 0 the result, 1 to 9 the grid, 10 to 45 the player, then the side inventory. */
    private static final int GRID_FIRST = 1;
    private static final int GRID_END = 10;
    private static final int SIDE_FIRST = 46;

    private static Method tweakOption;

    private CraftingStationClear() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new CraftingStationClear());
    }

    /** Before Crafting Tweaks, which then does not see the canceled click. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!GuiScreen.isCtrlKeyDown() || !GuiScreen.isShiftKeyDown() || !isClearButton(event.button)) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        Container container = player == null ? null : player.openContainer;
        if (container == null || !CONTAINER.equals(
            container.getClass()
                .getName())
            || container.inventorySlots.size() <= SIDE_FIRST) return;
        event.setCanceled(true);
        event.button.func_146113_a(mc.getSoundHandler());
        // a stack held by the mouse stays where it is: nothing is moved
        if (player.inventory.getItemStack() != null) return;
        for (int grid = GRID_FIRST; grid < GRID_END; grid++) {
            if (((Slot) container.inventorySlots.get(grid)).getHasStack()) moveToSide(mc, player, container, grid);
        }
    }

    private static void moveToSide(Minecraft mc, EntityPlayer player, Container container, int grid) {
        click(mc, player, container, grid);
        List<?> slots = container.inventorySlots;
        // first on the stacks of the same item, then in empty slots
        for (int pass = 0; pass < 2; pass++) {
            for (int i = SIDE_FIRST; i < slots.size(); i++) {
                ItemStack held = player.inventory.getItemStack();
                if (held == null) return;
                Slot slot = (Slot) slots.get(i);
                ItemStack stack = slot.getStack();
                boolean fits = pass == 0
                    ? stack != null && stack.stackSize < Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit())
                        && stack.isItemEqual(held)
                        && ItemStack.areItemStackTagsEqual(stack, held)
                    : stack == null && slot.isItemValid(held);
                if (fits) click(mc, player, container, i);
            }
        }
        // what did not fit goes back
        if (player.inventory.getItemStack() != null) click(mc, player, container, grid);
    }

    private static void click(Minecraft mc, EntityPlayer player, Container container, int slot) {
        mc.playerController.windowClick(container.windowId, slot, 0, 0, player);
    }

    private static boolean isClearButton(GuiButton button) {
        if (button == null || !TWEAK_BUTTON.equals(
            button.getClass()
                .getName()))
            return false;
        try {
            if (tweakOption == null) tweakOption = button.getClass()
                .getMethod("getTweakOption");
            Object option = tweakOption.invoke(button);
            return option != null && "Clear".equals(((Enum<?>) option).name());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
