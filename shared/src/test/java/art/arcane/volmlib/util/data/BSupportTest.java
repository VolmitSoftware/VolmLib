package art.arcane.volmlib.util.data;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;

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
    public void speleothemTipRecognizesTipAcrossApiDescriptors() {
        Class<?> speleothem = speleothemClass();
        BlockData tip = speleothem(speleothem, "TIP", false);
        BlockData base = speleothem(speleothem, "BASE", false);

        assertTrue(BSupport.isSpeleothemTip(tip));
        assertFalse(BSupport.isSpeleothemTip(base));
        assertFalse(BSupport.isSpeleothemTip(speleothem(speleothem, "TIP_MERGE", false)));
    }

    @Test
    public void speleothemTipRejectsOtherDataAndInvocationFailures() {
        BlockData other = (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        );

        assertFalse(BSupport.isSpeleothemTip(other));
        assertFalse(BSupport.isSpeleothemTip(speleothem(speleothemClass(), "TIP", true)));
        assertFalse(BSupport.isSpeleothemTip(null));
    }

    @Test
    public void sharedSpeleothemInterfaceTakesPriorityWhenPointedDripstoneStillExists() throws Exception {
        try (InputStream bytecode = Objects.requireNonNull(BSupport.class.getResourceAsStream("BSupport.class"))) {
            ClassLoader loader = new BlockApiClassLoader(Map.of(
                    BSupport.class.getName(), bytecode.readAllBytes(),
                    "org.bukkit.block.data.type.Speleothem", speleothemInterface()
            ));
            Class<?> speleothem = Class.forName("org.bukkit.block.data.type.Speleothem", true, loader);
            BlockData tip = speleothem(speleothem, "TIP", false);
            assertFalse(Class.forName("org.bukkit.block.data.type.PointedDripstone", true, loader).isInstance(tip));
            Method classifier = Class.forName(BSupport.class.getName(), true, loader)
                    .getDeclaredMethod("isSpeleothemTip", BlockData.class);
            classifier.setAccessible(true);

            assertTrue((boolean) classifier.invoke(null, tip));
            assertFalse((boolean) classifier.invoke(null, speleothem(speleothem, "BASE", false)));
        }
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

    @Test
    public void sugarCaneRequiresNativeSubstrate() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(blockData(Material.AIR));
            BSupport<Object> support = new BSupport<Object>() {
            };
            for (Material material : new Material[]{Material.SUGAR_CANE, Material.GRASS_BLOCK, Material.DIRT,
                    Material.COARSE_DIRT, Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT,
                    Material.MOSS_BLOCK, Material.MUD, Material.MUDDY_MANGROVE_ROOTS,
                    Material.SAND, Material.RED_SAND, Material.SUSPICIOUS_SAND}) {
                assertTrue(material.name(), support.canPlaceOnto(Material.SUGAR_CANE, material));
            }
            for (Material material : new Material[]{Material.STONE, Material.GRAVEL, Material.CLAY,
                    Material.DIRT_PATH, Material.FARMLAND, Material.AIR, Material.WATER}) {
                assertFalse(material.name(), support.canPlaceOnto(Material.SUGAR_CANE, material));
            }
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

    private static BlockData speleothem(Class<?> speleothem, String thicknessName, boolean fail) {
        return (BlockData) Proxy.newProxyInstance(
                speleothem.getClassLoader(),
                new Class<?>[]{speleothem},
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

    private static Class<?> speleothemClass() {
        try {
            return Class.forName("org.bukkit.block.data.type.Speleothem");
        } catch (ClassNotFoundException exception) {
            try {
                return Class.forName("org.bukkit.block.data.type.PointedDripstone");
            } catch (ClassNotFoundException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static byte[] speleothemInterface() throws ReflectiveOperationException {
        Class<?> thickness = Class.forName("org.bukkit.block.data.type.PointedDripstone")
                .getMethod("getThickness").getReturnType();
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                "org/bukkit/block/data/type/Speleothem", null, "java/lang/Object",
                new String[]{Type.getInternalName(BlockData.class)});
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getThickness",
                "()" + Type.getDescriptor(thickness), null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
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

    private static class BlockApiClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;

        private BlockApiClassLoader(Map<String, byte[]> definitions) {
            super(BSupportTest.class.getClassLoader());
            this.definitions = definitions;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            byte[] bytecode = definitions.get(name);
            if (bytecode == null) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(name, bytecode, 0, bytecode.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
