package gtnhtweaks.modules.cropsnh;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.cropsnh.api.ICropStickTile;
import com.gtnewhorizon.cropsnh.api.ISeedData;
import com.gtnewhorizon.cropsnh.api.SeedPlantingResult;
import com.gtnewhorizon.cropsnh.init.CropsNHBlocks;
import com.gtnewhorizon.cropsnh.utility.CropsNHUtils;

import gtnhtweaks.common.Backhand;

/**
 * Crop sticks in the off hand and a seed in the main hand: a right click on a soil the seed grows on places the sticks
 * with the seed planted in them. The client checks the click with {@link #target} and asks the server, which checks it
 * again and does it with {@link #plant}.
 */
public final class CropPlanting {

    /** How far from the eyes a player may reach, squared, a little more than the 6 blocks vanilla allows. */
    private static final double REACH_SQ = 8 * 8;

    private CropPlanting() {}

    /**
     * Where the crop sticks go for a right click on the side of the block, or null when the click is not one for this:
     * not the two items in hand, the player sneaks, no room, or a soil the seed does not grow on.
     */
    public static int[] target(World world, EntityPlayer player, int x, int y, int z, int side) {
        if (!CropsNHModule.plantWithSticks || player.isSneaking()) return null;
        int offhandSlot = Backhand.offhandSlot(player);
        // Backhand selects the slot of the off hand while it uses the off hand: that is no click of the main hand
        if (offhandSlot < 0 || player.inventory.currentItem == offhandSlot) return null;
        ItemStack seed = player.inventory.getCurrentItem();
        ItemStack sticks = Backhand.offhandItem(player);
        if (seed == null || sticks == null
            || sticks.stackSize <= 0
            || sticks.getItem() != Item.getItemFromBlock(CropsNHBlocks.blockCropSticks)) return null;
        ISeedData data = CropsNHUtils.getSeedData(CropsNHUtils.copyStackWithSize(seed, 1), true, false);
        if (data == null) return null;

        Block clicked = world.getBlock(x, y, z);
        // crop sticks already there plant the seed by themselves
        if (clicked == CropsNHBlocks.blockCropSticks) return null;
        // the sticks replace grass and snow as any block does, else go on the clicked side
        if (!clicked.isReplaceable(world, x, y, z)) {
            if (side < 0 || side >= Facing.offsetsXForSide.length) return null;
            x += Facing.offsetsXForSide[side];
            y += Facing.offsetsYForSide[side];
            z += Facing.offsetsZForSide[side];
        }
        if (!world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z)) return null;
        Block soil = world.getBlock(x, y - 1, z);
        if (!data.getCrop()
            .getSoilTypes()
            .isRegistered(soil, world.getBlockMetadata(x, y - 1, z))) return null;
        if (!CropsNHBlocks.blockCropSticks.canPlaceBlockAt(world, x, y, z)) return null;
        return new int[] { x, y, z };
    }

    /**
     * On the server: places the sticks from the off hand, as the player would, and plants the seed of the main hand.
     */
    static void plant(EntityPlayerMP player, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        World world = player.worldObj;
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > REACH_SQ) return;
        int[] target = target(world, player, x, y, z, side);
        if (target == null) {
            // the client thought otherwise: show it the world and its hands as they are
            player.inventoryContainer.detectAndSendChanges();
            return;
        }
        int offhandSlot = Backhand.offhandSlot(player);
        int mainSlot = player.inventory.currentItem;
        ItemStack sticks = Backhand.offhandItem(player);
        ItemStack seed = player.inventory.getCurrentItem();

        // placed through the item, as a right click with the off hand would: protection of claims and the sound of
        // CropsNH; the sticks take one from the stack themselves
        player.inventory.currentItem = offhandSlot;
        try {
            sticks.tryPlaceItemIntoWorld(player, world, x, y, z, side, hitX, hitY, hitZ);
        } finally {
            player.inventory.currentItem = mainSlot;
        }
        if (sticks.stackSize <= 0) player.inventory.setInventorySlotContents(offhandSlot, null);

        TileEntity tile = world.getTileEntity(target[0], target[1], target[2]);
        if (tile instanceof ICropStickTile) {
            ICropStickTile crop = (ICropStickTile) tile;
            Pair<SeedPlantingResult, ISeedData> result = crop.tryPlantSeed(seed);
            if (result.getLeft() == SeedPlantingResult.SUCCESS) {
                if (!player.capabilities.isCreativeMode) seed.stackSize--;
                if (seed.stackSize <= 0) player.inventory.setInventorySlotContents(mainSlot, null);
                crop.playPlantingSound();
                // as CropsNH does after planting by hand: tells the crop whether it can grow there
                crop.areGrowthRequirementsMet();
            }
        }
        player.inventoryContainer.detectAndSendChanges();
    }
}
