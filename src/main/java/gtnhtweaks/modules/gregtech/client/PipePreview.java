package gtnhtweaks.modules.gregtech.client;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.implementations.MTECable;

/** A GregTech pipe for previews, drawn like in the inventory. Only loaded with GregTech installed. */
public final class PipePreview {

    private static int pipeId = -1;

    private PipePreview() {}

    /** Draws the pipe centered at the origin, one block large. Block textures must be bound. */
    public static boolean render(RenderBlocks renderer) {
        int id = pipeId();
        if (id <= 0 || GregTechAPI.sBlockMachines == null) return false;
        renderer.renderBlockAsItem(GregTechAPI.sBlockMachines, id, 1F);
        return true;
    }

    /** Icons of a wrench on a pipe connected east and west, as the tool overlay shows them. */
    public static ToolIcon wrenchIcon(ForgeDirection side) {
        return side == ForgeDirection.EAST || side == ForgeDirection.WEST ? ToolIcon.PIPE_CONNECT : ToolIcon.PIPE_BLOCK;
    }

    /** The first fluid or item pipe of GregTech. */
    private static int pipeId() {
        if (pipeId >= 0) return pipeId;
        pipeId = 0;
        IMetaTileEntity[] all = GregTechAPI.METATILEENTITIES;
        for (int i = 1; i < all.length; i++) {
            if (all[i] instanceof MetaPipeEntity && !(all[i] instanceof MTECable)) {
                pipeId = i;
                break;
            }
        }
        return pipeId;
    }
}
