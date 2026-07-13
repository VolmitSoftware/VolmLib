package art.arcane.volmlib.util.data;

import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BSupportTest {
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
