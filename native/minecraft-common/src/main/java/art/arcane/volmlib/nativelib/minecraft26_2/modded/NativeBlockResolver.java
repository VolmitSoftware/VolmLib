package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class NativeBlockResolver {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final Policy policy;

    public NativeBlockResolver(Policy policy) {
        this.policy = policy;
    }

    public record Parsed(BlockState state, Map<Property<?>, Comparable<?>> properties, String deferredPlacementKey) {
    }

    public BlockState airState() {
        return AIR;
    }

    public ModdedBlockState getAir() {
        return ModdedBlockState.of(AIR, null);
    }

    public ModdedBlockState get(String bdxf) {
        Parsed parsed = resolveGet(bdxf);
        return stateFrom(parsed);
    }

    public ModdedBlockState getNoCompat(String bdxf) {
        Parsed parsed = resolveNoCompat(bdxf);
        return stateFrom(parsed);
    }

    public ModdedBlockState getOrNull(String bdxf) {
        return getOrNull(bdxf, false);
    }

    public ModdedBlockState getOrNull(String bdxf, boolean warn) {
        Parsed parsed = resolveOrNull(bdxf, warn);
        return parsed == null ? null : stateFrom(parsed);
    }

    private static ModdedBlockState stateFrom(Parsed parsed) {
        return parsed.deferredPlacementKey() == null
                ? ModdedBlockState.of(parsed.state(), parsed.properties())
                : ModdedBlockState.deferred(parsed.state(), parsed.properties(), parsed.deferredPlacementKey());
    }

    public Parsed resolveGet(String bdxf) {
        // Mirrors the Bukkit path: an unknown key warns (rate limited) and falls back to air, instead of
        // resolving to air with no output at all.
        return resolveNoCompat(bdxf);
    }

    public Parsed resolveNoCompat(String bdxf) {
        Parsed parsed = resolveOrNull(bdxf, true);
        if (parsed != null) {
            return parsed;
        }
        return new Parsed(AIR, null, null);
    }

    /**
     * Resolves a key, returning null when nothing claims it. Never substitutes air - {@link #resolveNoCompat(String)}
     * owns the air fallback.
     */
    public Parsed resolveOrNull(String bdxf, boolean warn) {
        try {
            String bd = bdxf.trim();

            if (bd.startsWith("minecraft:cauldron[level=")) {
                bd = bd.replaceAll("\\Q:cauldron[\\E", ":water_cauldron[");
            }

            if (bd.equals("minecraft:grass_path")) {
                return new Parsed(Blocks.DIRT_PATH.defaultBlockState(), null, null);
            }

            Parsed bdx = parseBlockData(bd, warn);

            if (bdx == null) {
                ModdedBlockState provided = policy.resolveCustomBlock(bd);
                if (provided != null) {
                    return new Parsed(provided.handle(), null, provided.deferredPlacementKey());
                }
                if (warn) {
                    policy.warnUnresolved(bd, "Unknown Block Data '" + bd + "'");
                }
                return null;
            }

            return bdx;
        } catch (Throwable e) {
            policy.reportError(bdxf, e);
            if (warn) {
                policy.warnUnresolved(bdxf, "Unknown Block Data '" + bdxf + "'");
            }
        }

        return null;
    }

    public ModdedBlockState decode(String key) {
        String normalized = key.trim();
        try {
            return strictParse(normalized);
        } catch (IllegalArgumentException failure) {
            ModdedBlockState custom = policy.resolveCustomBlock(normalized);
            if (custom != null) {
                return custom;
            }
            throw failure;
        }
    }

    public static ModdedBlockState strictParse(String key) {
        Parsed parsed = parseStrict(key);
        return stateFrom(parsed);
    }

    private static Parsed parseStrict(String key) {
        StringReader reader = new StringReader(key);
        BlockStateParser.BlockResult result;
        try {
            result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, false);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Could not parse data: " + key, e);
        }
        if (reader.canRead()) {
            throw new IllegalArgumentException("Could not parse remainder: " + reader.getRemaining());
        }
        return new Parsed(result.blockState(), result.properties(), null);
    }

    private Parsed createBlockData(String s, boolean warn) {
        try {
            return parseStrict(s);
        } catch (IllegalArgumentException e) {
            if (s.contains("[")) {
                String base = s.split("\\Q[\\E")[0];
                Parsed stripped = createBlockData(base, warn);
                if (stripped != null && warn) {
                    // Dedup on the base block key, not the full state string. UnresolvedKeyLog interns every key it
                    // is handed into a set it never trims, and a rejected property is usually rejected for every
                    // value and every combination a pack uses - keying on the state string would intern one entry per
                    // distinct state (16 levels x 6 facings x ...) for a single authoring mistake.
                    policy.warnUnresolved("props:" + base,
                            "Block '" + base + "' rejected state '" + propertySection(s) + "'; using its default state");
                }
                return stripped;
            }
        }

        if (warn) {
            policy.warnUnresolved(s, "Can't find block data for " + s);
        }
        return null;
    }

    private String propertySection(String key) {
        int open = key.indexOf('[');
        if (open < 0) {
            return "";
        }
        int close = key.indexOf(']', open);
        return close < 0 ? key.substring(open + 1) : key.substring(open + 1, close);
    }

    private Parsed materialBlockData(String ix) {
        if (ix.contains("[") || ix.contains(":")) {
            return null;
        }
        Identifier identifier = Identifier.tryParse("minecraft:" + ix.toLowerCase(Locale.ROOT));
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return null;
        }
        return new Parsed(BuiltInRegistries.BLOCK.getValue(identifier).defaultBlockState(), null, null);
    }

    private Parsed parseBlockData(String ix, boolean warn) {
        try {
            Parsed bx = createBlockData(ix.toLowerCase(), warn);

            if (bx == null) {
                bx = createBlockData("minecraft:" + ix.toLowerCase(), warn);
            }

            if (bx == null) {
                bx = materialBlockData(ix);
            }

            if (bx == null) {
                if (warn) {
                    policy.warnUnresolved(ix, "Unknown Block Data: " + ix);
                }
                return null;
            }

            if (bx.state().getBlock() instanceof LeavesBlock) {
                BlockState mutated = bx.state().setValue(LeavesBlock.PERSISTENT, shouldPreventLeafDecay());
                bx = new Parsed(mutated, bx.properties(), bx.deferredPlacementKey());
            }

            return bx;
        } catch (Throwable e) {
            String block = ix.contains(":") ? ix.split(":")[1].toLowerCase() : ix.toLowerCase();
            String state = block.contains("[") ? block.split("\\Q[\\E")[1].split("\\Q]\\E")[0] : "";
            Map<String, String> stateMap = new HashMap<>();
            if (!state.equals("")) {
                Arrays.stream(state.split(",")).forEach((String s) -> stateMap.put(s.split("=")[0], s.split("=")[1]));
            }
            block = block.split("\\Q[\\E")[0];

            switch (block) {
                case "cauldron" -> block = "water_cauldron";
                case "grass_path" -> block = "dirt_path";
                case "concrete" -> block = "white_concrete";
                case "wool" -> block = "white_wool";
                case "beetroots" -> {
                    if (stateMap.containsKey("age")) {
                        String updated = stateMap.get("age");
                        switch (updated) {
                            case "7" -> updated = "3";
                            case "3", "4", "5" -> updated = "2";
                            case "1", "2" -> updated = "1";
                        }
                        stateMap.put("age", updated);
                    }
                }
            }

            Map<String, String> newStates = new HashMap<>();
            for (String key : stateMap.keySet()) {
                createBlockData(block + "[" + key + "=" + stateMap.get(key) + "]", false);
                newStates.put(key, stateMap.get(key));
            }

            String joined = newStates.entrySet().stream()
                    .map((Map.Entry<String, String> entry) -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
            if (!joined.equals("")) {
                joined = "[" + joined + "]";
            }
            String newBlock = block + joined;
            policy.debug("Converting " + ix + " to " + newBlock);

            try {
                return createBlockData(newBlock, false);
            } catch (Throwable e1) {
                policy.reportError(newBlock, e1);
            }

            return null;
        }
    }

    private boolean shouldPreventLeafDecay() {
        return policy.preventLeafDecay();
    }

    public interface Policy {
        ModdedBlockState resolveCustomBlock(String key);

        boolean preventLeafDecay();

        void reportError(String key, Throwable failure);

        void warnUnresolved(String key, String message);

        void debug(String message);
    }
}
