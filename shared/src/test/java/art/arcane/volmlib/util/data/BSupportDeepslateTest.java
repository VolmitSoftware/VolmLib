package art.arcane.volmlib.util.data;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

public class BSupportDeepslateTest {
    private static final Map<Material, Material> ORE_PAIRS = Map.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);

    @Test
    public void everyOrePairCreatesFreshDefaultDataInBothDirections() {
        try (MockedStatic<Bukkit> bukkit = freshDataFactory()) {
            BSupport<Object> support = new BSupport<Object>() {
            };
            BlockData stone = blockData(Material.STONE);
            BlockData deepslate = blockData(Material.DEEPSLATE);
            for (Map.Entry<Material, Material> pair : ORE_PAIRS.entrySet()) {
                assertConversion(support, deepslate, pair.getKey(), pair.getValue());
                assertConversion(support, stone, pair.getValue(), pair.getKey());
            }
        }
    }

    @Test
    public void unmappedAndAlreadyMatchingMaterialsRetainInputIdentityAndState() {
        try (MockedStatic<Bukkit> bukkit = freshDataFactory()) {
            BSupport<Object> support = new BSupport<Object>() {
            };
            BlockData stone = blockData(Material.STONE);
            BlockData deepslate = blockData(Material.DEEPSLATE);
            bukkit.clearInvocations();
            for (Material material : Material.values()) {
                BlockData original = blockData(material);
                ((Lightable) original).setLit(true);
                if (!ORE_PAIRS.containsKey(material)) {
                    assertSame(original, support.toDeepSlateOre(deepslate, original));
                }
                if (!ORE_PAIRS.containsValue(material)) {
                    assertSame(original, support.toDeepSlateOre(stone, original));
                }
                assertTrue(((Lightable) original).isLit());
            }
            bukkit.verifyNoInteractions();
        }
    }

    @Test
    public void substrateClassificationRemainsOverridableAndRunsOnce() {
        try (MockedStatic<Bukkit> bukkit = freshDataFactory()) {
            AtomicInteger classifications = new AtomicInteger();
            BSupport<Object> support = new BSupport<Object>() {
                @Override
                public boolean isDeepSlate(BlockData blockData) {
                    classifications.incrementAndGet();
                    return true;
                }
            };
            BlockData converted = support.toDeepSlateOre(blockData(Material.STONE), blockData(Material.COAL_ORE));
            assertEquals(Material.DEEPSLATE_COAL_ORE, converted.getMaterial());
            assertEquals(1, classifications.get());
        }
    }

    @Test
    public void defaultDataCreationFailurePropagatesUnchanged() {
        try (MockedStatic<Bukkit> bukkit = freshDataFactory()) {
            BSupport<Object> support = new BSupport<Object>() {
            };
            IllegalStateException failure = new IllegalStateException("Block factory unavailable");
            bukkit.when(() -> Bukkit.createBlockData(Material.DEEPSLATE_COAL_ORE)).thenThrow(failure);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> support.toDeepSlateOre(
                    blockData(Material.DEEPSLATE), blockData(Material.COAL_ORE))));
        }
    }

    private static void assertConversion(BSupport<Object> support, BlockData substrate, Material source, Material target) {
        BlockData original = blockData(source);
        ((Lightable) original).setLit(true);
        BlockData first = support.toDeepSlateOre(substrate, original);
        BlockData second = support.toDeepSlateOre(substrate, original);
        assertEquals(target, first.getMaterial());
        assertEquals(target, second.getMaterial());
        assertNotSame(original, first);
        assertNotSame(first, second);
        assertFalse(((Lightable) first).isLit());
        assertFalse(((Lightable) second).isLit());
        ((Lightable) first).setLit(true);
        assertFalse(((Lightable) second).isLit());
        assertTrue(((Lightable) original).isLit());
    }

    private static MockedStatic<Bukkit> freshDataFactory() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class)))
                .thenAnswer(invocation -> blockData(invocation.getArgument(0)));
        return bukkit;
    }

    private static BlockData blockData(Material material) {
        AtomicBoolean lit = new AtomicBoolean();
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{Lightable.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "isLit" -> lit.get();
                    case "setLit" -> {
                        lit.set((boolean) arguments[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
