package io.github.foundationgames.automobility.automobile.attachment.front;

import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseHarvesterFrontAttachment extends FrontAttachment {
    private final BlockPos.MutableBlockPos blockIter = new BlockPos.MutableBlockPos();
    private Vec3 lastPos = null;
    private static final TagKey<Item> SEEDS_TAG = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "seeds"));

    public BaseHarvesterFrontAttachment(FrontAttachmentType<?> type, AutomobileEntity automobile) {
        super(type, automobile);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 pos = this.pos();
        Level world = this.world();
        if (canModifyBlocks() && lastPos != null
                && lastPos.subtract(pos).length() > 0.03
                && world instanceof ServerLevel serverWorld) {
            this.harvest(pos, serverWorld);
        }
        if (canModifyBlocks() && shouldTryPlantFromInventory()) {
            tryPlantFromInventory(pos, world);
        }
        this.lastPos = pos;
    }

    public void harvest(Vec3 pos, ServerLevel world) {
        int minX = (int) Math.floor(pos.x - 0.5);
        int maxX = (int) Math.floor(pos.x + 0.5);
        int minZ = (int) Math.floor(pos.z - 0.5);
        int maxZ = (int) Math.floor(pos.z + 0.5);
        int y = (int) Math.floor(pos.y + 0.25);
        Entity entity = this.automobile;
        if (this.automobile.isVehicle()) {
            entity = this.automobile.getFirstPassenger();
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                blockIter.set(x, y, z);
                BlockState state = world.getBlockState(blockIter);
                if (canHarvest(state)) {
                    List<ItemStack> stacks = Block.getDrops(state, world, blockIter, null, entity, ItemStack.EMPTY);
                    world.destroyBlock(blockIter, false);
                    this.onBlockHarvested(state, blockIter, stacks);
                }
            }
        }
    }

    private boolean shouldTryPlantFromInventory() {
        return (automobile.tickCount % 1 == 0);
    }

    private void tryPlantFromInventory(Vec3 pos, Level world) {
        int minX = (int) Math.floor(pos.x - 0.5);
        int maxX = (int) Math.floor(pos.x + 0.5);
        int minZ = (int) Math.floor(pos.z - 0.5);
        int maxZ = (int) Math.floor(pos.z + 0.5);
        int y = (int) Math.floor(pos.y + 0.25);
        List<ItemStack> seedStacks = getSeedItemsFromInventory();
        if (seedStacks.isEmpty()) return;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos targetPos = new BlockPos(x, y, z);
                if (!world.getBlockState(targetPos).isAir()) continue;
                for (ItemStack seedStack : seedStacks) {
                    if (!seedStack.isEmpty()) {
                        if (seedStack.getItem() == Items.WHEAT_SEEDS) {
                            BlockState wheatState = Blocks.WHEAT.defaultBlockState();
                            if (wheatState.canSurvive(world, targetPos)) {
                                world.setBlockAndUpdate(targetPos, wheatState);
                                seedStack.shrink(1);
                                break;
                            }
                        } else if (seedStack.getItem() instanceof BlockItem item) {
                            BlockState newState = item.getBlock().defaultBlockState();
                            if (newState.canSurvive(world, targetPos)) {
                                world.setBlockAndUpdate(targetPos, newState);
                                seedStack.shrink(1);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private List<ItemStack> getSeedItemsFromInventory() {
        List<ItemStack> seeds = new ArrayList<>();
        var rearAtt = automobile.getRearAttachment();
        if (rearAtt instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && isSeed(stack)) {
                    seeds.add(stack);
                }
            }
        }
        return seeds;
    }

    private boolean isSeed(ItemStack stack) {
        return stack.is(SEEDS_TAG)
                || (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof CropBlock);
    }

    public abstract boolean canHarvest(BlockState state);
    public abstract void onBlockHarvested(BlockState state, BlockPos pos, List<ItemStack> drops);
}
