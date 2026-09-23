package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.BlockPos;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class NativeBlockProperties {
    private static final Set<Block> FOLIAGE = blockSet(
            "poppy", "dandelion", "cornflower", "sweet_berry_bush", "crimson_roots", "warped_roots",
            "nether_sprouts", "allium", "azure_bluet", "blue_orchid", "oxeye_daisy", "lily_of_the_valley",
            "wither_rose", "dark_oak_sapling", "acacia_sapling", "jungle_sapling", "birch_sapling",
            "spruce_sapling", "oak_sapling", "orange_tulip", "pink_tulip", "red_tulip", "white_tulip",
            "fern", "large_fern", "short_grass", "tall_grass");
    private static final Set<Block> DECORANT = decorantSet();
    private static final Set<Block> LIT = blockSet(
            "glowstone", "amethyst_cluster", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
            "end_rod", "soul_sand", "torch", "redstone_torch", "soul_torch", "redstone_wall_torch", "wall_torch",
            "soul_wall_torch", "lantern", "candle", "jack_o_lantern", "redstone_lamp", "magma_block", "light",
            "shroomlight", "sea_lantern", "soul_lantern", "fire", "soul_fire", "sea_pickle", "brewing_stand",
            "redstone_ore");
    private static final Set<Block> STORAGE = blockSet(
            "chest", "smoker", "trapped_chest", "shulker_box", "white_shulker_box", "orange_shulker_box",
            "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box",
            "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box",
            "purple_shulker_box", "blue_shulker_box", "brown_shulker_box", "green_shulker_box",
            "red_shulker_box", "black_shulker_box", "barrel", "dispenser", "dropper", "hopper", "furnace",
            "blast_furnace");
    private static final Set<Block> STORAGE_CHEST = storageChestSet();
    private static final Set<Block> DEEPSLATE = blockSet(
            "deepslate", "deepslate_bricks", "deepslate_brick_slab", "deepslate_brick_stairs",
            "deepslate_brick_wall", "deepslate_tile_slab", "deepslate_tiles", "deepslate_tile_stairs",
            "deepslate_tile_wall", "cracked_deepslate_tiles", "deepslate_coal_ore", "deepslate_iron_ore",
            "deepslate_copper_ore", "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_gold_ore",
            "deepslate_lapis_ore", "deepslate_redstone_ore");
    private static final Map<Block, Block> NORMAL_TO_DEEPSLATE = oreMap(false);
    private static final Map<Block, Block> DEEPSLATE_TO_NORMAL = oreMap(true);
    private static final Set<Block> FOLIAGE_PLANTABLE_STATE = blockSet(
            "grass_block", "moss_block", "rooted_dirt", "dirt", "coarse_dirt", "podzol");
    private static final Set<Block> FOLIAGE_PLANTABLE_MATERIAL = blockSet(
            "grass_block", "moss_block", "dirt", "tall_grass", "tall_seagrass", "large_fern", "sunflower",
            "peony", "lilac", "rose_bush", "rooted_dirt", "coarse_dirt", "podzol");
    private static final Set<Block> PLACE_ONTO_LEAVES = blockSet(
            "acacia_leaves", "birch_leaves", "dark_oak_leaves", "jungle_leaves", "oak_leaves", "spruce_leaves");
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public static boolean matches(NativeBlockState first, NativeBlockState second, boolean exact) {
        BlockState left = (BlockState) first.nativeHandle();
        BlockState right = (BlockState) second.nativeHandle();
        return exact ? left.equals(right) : left.getBlock() == right.getBlock();
    }

    private NativeBlockProperties() {
    }

    private static Set<Block> blockSet(String... ids) {
        Set<Block> blocks = new HashSet<>();
        for (String id : ids) {
            Identifier identifier = Identifier.parse("minecraft:" + id);
            if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
                blocks.add(BuiltInRegistries.BLOCK.getValue(identifier));
            }
        }
        return blocks;
    }

    private static Set<Block> decorantSet() {
        Set<Block> blocks = blockSet(
                "short_grass", "tall_grass", "fern", "large_fern", "cornflower", "sunflower", "chorus_flower",
                "poppy", "dandelion", "oxeye_daisy", "orange_tulip", "pink_tulip", "red_tulip", "white_tulip",
                "lilac", "dead_bush", "sweet_berry_bush", "rose_bush", "wither_rose", "allium", "blue_orchid",
                "lily_of_the_valley", "crimson_fungus", "warped_fungus", "red_mushroom", "brown_mushroom",
                "crimson_roots", "azure_bluet", "cactus", "weeping_vines", "weeping_vines_plant", "warped_roots",
                "nether_sprouts", "twisting_vines", "twisting_vines_plant", "sugar_cane", "wheat", "potatoes",
                "carrots", "beetroots", "nether_wart", "sea_pickle", "seagrass", "tall_seagrass",
                "acacia_button", "birch_button", "crimson_button", "dark_oak_button", "jungle_button",
                "oak_button", "polished_blackstone_button", "spruce_button", "stone_button", "warped_button",
                "torch", "soul_torch", "glow_lichen", "vine", "sculk_vein");
        blocks.addAll(FOLIAGE);
        return blocks;
    }

    private static Set<Block> storageChestSet() {
        Set<Block> blocks = new HashSet<>(STORAGE);
        blocks.remove(Blocks.SMOKER);
        blocks.remove(Blocks.FURNACE);
        blocks.remove(Blocks.BLAST_FURNACE);
        return blocks;
    }

    private static Map<Block, Block> oreMap(boolean deepslateToNormal) {
        String[][] pairs = {
                {"coal_ore", "deepslate_coal_ore"},
                {"emerald_ore", "deepslate_emerald_ore"},
                {"diamond_ore", "deepslate_diamond_ore"},
                {"copper_ore", "deepslate_copper_ore"},
                {"gold_ore", "deepslate_gold_ore"},
                {"iron_ore", "deepslate_iron_ore"},
                {"lapis_ore", "deepslate_lapis_ore"},
                {"redstone_ore", "deepslate_redstone_ore"}
        };
        Map<Block, Block> map = new HashMap<>();
        for (String[] pair : pairs) {
            Block normal = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + pair[0]));
            Block deep = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + pair[1]));
            if (deepslateToNormal) {
                map.put(deep, normal);
            } else {
                map.put(normal, deep);
            }
        }
        return map;
    }

    public static BlockState toDeepSlateOre(BlockState block, BlockState ore) {
        Block key = ore.getBlock();

        if (isDeepSlate(block)) {
            Block mapped = NORMAL_TO_DEEPSLATE.get(key);
            if (mapped != null) {
                return mapped.defaultBlockState();
            }
        } else {
            Block mapped = DEEPSLATE_TO_NORMAL.get(key);
            if (mapped != null) {
                return mapped.defaultBlockState();
            }
        }

        return ore;
    }

    public static boolean isDeepSlate(BlockState state) {
        return DEEPSLATE.contains(state.getBlock());
    }

    public static boolean isOre(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore");
    }

    public static boolean isAir(BlockState state) {
        if (state == null) {
            return true;
        }
        Block block = state.getBlock();
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    public static boolean isSolid(BlockState state) {
        if (state == null) {
            return false;
        }
        return NativeBlockMaterial.isSolid(state.getBlock());
    }

    public static boolean isOccluding(BlockState state) {
        return state.getBlock().defaultBlockState().isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static boolean isFluid(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.WATER || block == Blocks.LAVA;
    }

    public static boolean isWater(BlockState state) {
        return state.getBlock() == Blocks.WATER;
    }

    public static boolean isWaterLogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    public static boolean isLit(BlockState state) {
        return LIT.contains(state.getBlock());
    }

    public static boolean isUpdatable(BlockState state) {
        return isStorage(state)
                || (state.getBlock() instanceof SpeleothemBlock
                && state.getValue(SpeleothemBlock.THICKNESS) == SpeleothemThickness.TIP);
    }

    public static boolean isFoliage(BlockState state) {
        return FOLIAGE.contains(state.getBlock());
    }

    public static boolean isFoliagePlantable(BlockState state) {
        return FOLIAGE_PLANTABLE_STATE.contains(state.getBlock());
    }

    public static boolean isDecorant(BlockState state) {
        return DECORANT.contains(state.getBlock());
    }

    public static boolean isStorage(BlockState state) {
        return STORAGE.contains(state.getBlock());
    }

    public static boolean isStorageChest(BlockState state) {
        return STORAGE_CHEST.contains(state.getBlock());
    }

    public static boolean isVineBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.VINE || block == Blocks.SCULK_VEIN || block == Blocks.GLOW_LICHEN;
    }

    public static boolean hasTileEntity(BlockState state) {
        return state.getBlock().defaultBlockState().hasBlockEntity();
    }

    public static boolean canPlaceOnto(Block mat, Block onto) {
        if (mat == Blocks.SUGAR_CANE) {
            return onto == Blocks.SUGAR_CANE || onto == Blocks.GRASS_BLOCK || onto == Blocks.DIRT
                    || onto == Blocks.COARSE_DIRT || onto == Blocks.PODZOL || onto == Blocks.MYCELIUM
                    || onto == Blocks.ROOTED_DIRT
                    || onto == Blocks.MOSS_BLOCK || onto == Blocks.PALE_MOSS_BLOCK || onto == Blocks.MUD
                    || onto == Blocks.MUDDY_MANGROVE_ROOTS || onto == Blocks.SAND || onto == Blocks.RED_SAND
                    || onto == Blocks.SUSPICIOUS_SAND;
        }

        if (mat == Blocks.CACTUS) {
            return onto == Blocks.CACTUS || onto == Blocks.SAND || onto == Blocks.RED_SAND;
        }

        if ((onto == Blocks.CRIMSON_NYLIUM || onto == Blocks.WARPED_NYLIUM || onto == Blocks.SOUL_SOIL)
                && (mat == Blocks.CRIMSON_FUNGUS || mat == Blocks.CRIMSON_ROOTS
                || mat == Blocks.WARPED_FUNGUS || mat == Blocks.WARPED_ROOTS || mat == Blocks.NETHER_SPROUTS)) {
            return true;
        }

        if (FOLIAGE.contains(mat)) {
            if (!FOLIAGE_PLANTABLE_MATERIAL.contains(onto)) {
                return false;
            }
        }

        if (onto == Blocks.AIR || onto == Blocks.CAVE_AIR || onto == Blocks.VOID_AIR) {
            return false;
        }

        if (onto == Blocks.GRASS_BLOCK && mat == Blocks.DEAD_BUSH) {
            return false;
        }

        if (onto == Blocks.DIRT_PATH) {
            if (!NativeBlockMaterial.isSolid(mat)) {
                return false;
            }
        }

        if (PLACE_ONTO_LEAVES.contains(onto)) {
            return NativeBlockMaterial.isSolid(mat);
        }

        return true;
    }
}
