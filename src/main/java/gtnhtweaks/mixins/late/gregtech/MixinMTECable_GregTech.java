package gtnhtweaks.mixins.late.gregtech;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTECable;

/**
 * Makes a placed insulated cable pick the bare wire texture of its material. The item is drawn from the registry
 * prototype, whose tile entity has no world, so cables in the inventory keep the insulation.
 */
@Mixin(value = MTECable.class, remap = false)
public class MixinMTECable_GregTech {

    @Redirect(
        method = "getTexture(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;Lnet/minecraftforge/common/util/ForgeDirection;IIZZ)[Lgregtech/api/interfaces/ITexture;",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTECable;mInsulated:Z",
            opcode = Opcodes.GETFIELD))
    private boolean gtnhtweaks$hideInsulationInWorld(MTECable cable) {
        if (!cable.mInsulated) return false;
        IGregTechTileEntity base = cable.getBaseMetaTileEntity();
        return base == null || base.getWorld() == null;
    }
}
