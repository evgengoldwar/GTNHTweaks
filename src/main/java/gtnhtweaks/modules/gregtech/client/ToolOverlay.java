package gtnhtweaks.modules.gregtech.client;

import java.lang.reflect.Method;
import java.util.function.Function;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEBuffer;
import gregtech.api.metatileentity.implementations.MTECable;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.ItemMachines;
import gtnhtweaks.modules.gregtech.GregTechModule;

/**
 * The tool overlay of GregTech CEu Modern for GregTech 5: which icon every side gets for the held tool, following
 * what that tool does in GT5. GT5 decides when the grid is drawn; this replaces how it looks.
 */
public final class ToolOverlay {

    private ToolOverlay() {}

    /**
     * Draws the grid GT5 asked for, with the arguments of GT5's BlockOverlayRenderer.drawGrid.
     *
     * @return false to let GT5 draw its own grid
     */
    public static boolean drawGrid(DrawBlockHighlightEvent event, boolean showCovers, boolean wrench,
        boolean sneaking) {
        if (!GregTechModule.modernToolOverlay) return false;
        event.setCanceled(true);
        try {
            GridOverlay.drawGrid(event, icons(event, showCovers, wrench, sneaking));
        } catch (Throwable t) {
            // never break the block highlight: GT5's own grid instead
            event.setCanceled(false);
            return false;
        }
        return true;
    }

    private static Function<ForgeDirection, ToolIcon> icons(DrawBlockHighlightEvent event, boolean showCovers,
        boolean wrench, boolean sneaking) {
        World world = event.player.worldObj;
        int x = event.target.blockX, y = event.target.blockY, z = event.target.blockZ;
        TileEntity tile = world.getTileEntity(x, y, z);
        Block block = world.getBlock(x, y, z);
        ItemStack held = event.currentItem;

        // covers: placing, removing or configuring
        if (showCovers) {
            if (!(tile instanceof ICoverable)) return side -> null;
            ICoverable coverable = (ICoverable) tile;
            if (held == null || GTUtility.isStackInList(held, GregTechAPI.sScrewdriverList)) {
                return side -> coverable.hasCoverAtSide(side) ? ToolIcon.COVER_SETTINGS : null;
            }
            if (GTUtility.isStackInList(held, GregTechAPI.sCrowbarList)) {
                return side -> coverable.hasCoverAtSide(side) ? ToolIcon.REMOVE_COVER : null;
            }
            return side -> coverable.hasCoverAtSide(side) ? null : ToolIcon.ATTACH_COVER;
        }

        // pipes and cables: the connection of every side
        if (tile instanceof BaseMetaPipeEntity) {
            BaseMetaPipeEntity pipe = (BaseMetaPipeEntity) tile;
            boolean cable = pipe.getMetaTileEntity() instanceof MTECable;
            if (held != null && held.getItem() instanceof ItemMachines) {
                // a pipe in hand: the sides a new pipe can go to
                return side -> world.isAirBlock(x + side.offsetX, y + side.offsetY, z + side.offsetZ)
                    ? (cable ? ToolIcon.WIRE_CONNECT : ToolIcon.PIPE_CONNECT)
                    : null;
            }
            return side -> {
                ICoverable coverable = pipe;
                if (GTUtility.isStackInList(held, GregTechAPI.sScrewdriverList) && coverable.hasCoverAtSide(side)) {
                    return ToolIcon.COVER_SETTINGS;
                }
                boolean connected = (pipe.mConnections & side.flag) != 0;
                if (cable) return connected ? ToolIcon.WIRE_CONNECT : ToolIcon.WIRE_BLOCK;
                return connected ? ToolIcon.PIPE_CONNECT : ToolIcon.PIPE_BLOCK;
            };
        }

        // machines
        if (tile instanceof BaseMetaTileEntity) {
            BaseMetaTileEntity machine = (BaseMetaTileEntity) tile;
            IMetaTileEntity meta = machine.getMetaTileEntity();
            if (wrench) {
                if (hasAlignment(machine)) {
                    // multiblock controllers: the wrench turns them to the clicked side
                    ForgeDirection front = machine.getFrontFacing();
                    return side -> side != front ? ToolIcon.FRONT_FACING_ROTATION : null;
                }
                if (meta instanceof MTEBasicMachine) {
                    MTEBasicMachine basic = (MTEBasicMachine) meta;
                    // sneaking moves the main face, otherwise the output face
                    if (sneaking) {
                        return side -> side.offsetY == 0 && side != basic.mMainFacing ? ToolIcon.FRONT_FACING_ROTATION
                            : null;
                    }
                    ForgeDirection output = machine.getFrontFacing();
                    return side -> side != output && machine.isValidFacing(side) ? ToolIcon.IO_FACING_ROTATION : null;
                }
                ForgeDirection front = machine.getFrontFacing();
                return side -> side != front && machine.isValidFacing(side) ? ToolIcon.FRONT_FACING_ROTATION : null;
            }
            if (meta instanceof MTEBuffer && GTUtility.isStackInList(held, GregTechAPI.sScrewdriverList)) {
                ForgeDirection output = machine.getFrontFacing();
                return side -> side == output ? ToolIcon.AUTO_OUTPUT
                    : machine.hasCoverAtSide(side) ? ToolIcon.COVER_SETTINGS : null;
            }
            return side -> null;
        }

        // other mods and vanilla blocks the wrench turns
        if (wrench) {
            boolean horizontal = block == Blocks.furnace || block == Blocks.lit_furnace
                || block == Blocks.chest
                || block == Blocks.trapped_chest
                || block == Blocks.ender_chest
                || block == Blocks.pumpkin
                || block == Blocks.lit_pumpkin;
            return side -> !horizontal || side.offsetY == 0 ? ToolIcon.FRONT_FACING_ROTATION : null;
        }
        return side -> null;
    }

    private static Method getAlignment;
    private static boolean noAlignment;

    /** True for machines turned by StructureLib, like multiblock controllers. */
    private static boolean hasAlignment(BaseMetaTileEntity machine) {
        if (noAlignment) return false;
        try {
            if (getAlignment == null) getAlignment = BaseMetaTileEntity.class.getMethod("getAlignment");
            return getAlignment.invoke(machine) != null;
        } catch (Throwable t) {
            noAlignment = true;
            return false;
        }
    }

    /**
     * Tools GT5 draws no grid for: the soft mallet shows if the machine runs, the hard hammer if it is muffled, with
     * one larger icon on the looked at face, like in GTCEu.
     */
    public static class SingleIcons {

        @SubscribeEvent
        public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
            if (!GregTechModule.modernToolOverlay || event.isCanceled()
                || event.currentItem == null
                || event.target == null) return;
            EntityPlayer player = event.player;
            TileEntity tile = player.worldObj
                .getTileEntity(event.target.blockX, event.target.blockY, event.target.blockZ);
            if (!(tile instanceof BaseMetaTileEntity)) return;
            BaseMetaTileEntity machine = (BaseMetaTileEntity) tile;
            if (machine.getMetaTileEntity() == null) return;
            ItemStack held = event.currentItem;
            ToolIcon icon = null;
            if (GTUtility.isStackInList(held, GregTechAPI.sSoftMalletList)) {
                icon = machine.isAllowedToWork() ? ToolIcon.START : ToolIcon.PAUSE;
            } else if (!player.isSneaking() && GTUtility.isStackInList(held, GregTechAPI.sHardHammerList)
                && !machine.hasCoverAtSide(GridOverlay.gridSide(event.target))) {
                    icon = machine.isMuffled() ? ToolIcon.SOUND : ToolIcon.MUTE;
                }
            if (icon == null) return;
            try {
                GridOverlay.drawSingle(event, icon);
            } catch (Throwable ignored) {
                // a broken overlay must never break the game
            }
        }
    }
}
