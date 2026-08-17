package art.arcane.volmlib.util.data;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

public class BSupportTest {
    @Test
    public void materialDiscoveryExcludesLegacyAliases() {
        assertTrue(BSupport.isModernMaterial(Material.STONE));
        assertFalse(BSupport.isModernMaterial(Material.valueOf("LEGACY_STONE")));
        assertFalse(BSupport.isModernMaterial(null));
    }

    @Test
    public void pointedDripstoneTipRecognizesTipAcrossApiDescriptors() {
        BlockData tip = pointedDripstone("TIP", false);
        BlockData base = pointedDripstone("BASE", false);

        assertTrue(BSupport.isPointedDripstoneTip(tip));
        assertFalse(BSupport.isPointedDripstoneTip(base));
    }

    @Test
    public void pointedDripstoneTipRejectsOtherDataAndInvocationFailures() {
        BlockData other = (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        );

        assertFalse(BSupport.isPointedDripstoneTip(other));
        assertFalse(BSupport.isPointedDripstoneTip(pointedDripstone("TIP", true)));
        assertFalse(BSupport.isPointedDripstoneTip(null));
    }

    @Test
    public void cactusPlacementAndDecorantClassificationMatchVanillaSupport() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(blockData(Material.AIR));
            BSupport<Object> support = new BSupport<Object>() {
            };
            BlockData cactus = blockData(Material.CACTUS);

            assertTrue(support.canPlaceOnto(Material.CACTUS, Material.CACTUS));
            assertTrue(support.canPlaceOnto(Material.CACTUS, Material.SAND));
            assertTrue(support.canPlaceOnto(Material.CACTUS, Material.RED_SAND));
            assertFalse(support.canPlaceOnto(Material.CACTUS, Material.STONE));
            assertTrue(support.isDecorant(cactus));
        }
    }

    private static BlockData blockData(Material material) {
        return (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, arguments) -> method.getName().equals("getMaterial")
                        ? material
                        : defaultValue(method.getReturnType())
        );
    }

    private static BlockData pointedDripstone(String thicknessName, boolean fail) {
        Class<?> pointedDripstone = pointedDripstoneClass();
        return (BlockData) Proxy.newProxyInstance(
                pointedDripstone.getClassLoader(),
                new Class<?>[]{pointedDripstone},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getThickness")) {
                        if (fail) {
                            throw new IllegalStateException("failed");
                        }
                        for (Object value : method.getReturnType().getEnumConstants()) {
                            if (((Enum<?>) value).name().equals(thicknessName)) {
                                return value;
                            }
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Class<?> pointedDripstoneClass() {
        try {
            return Class.forName("org.bukkit.block.data.type.PointedDripstone");
        } catch (ClassNotFoundException exception) {
            try {
                return Class.forName("org.bukkit.block.data.type.Speleothem");
            } catch (ClassNotFoundException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
