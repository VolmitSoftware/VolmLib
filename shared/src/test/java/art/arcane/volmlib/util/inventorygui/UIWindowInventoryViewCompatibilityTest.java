package art.arcane.volmlib.util.inventorygui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UIWindowInventoryViewCompatibilityTest {
    private static final String INVENTORY_VIEW_INTERNAL_NAME = "org/bukkit/inventory/InventoryView";

    @Test
    public void inventoryViewAccess_usesRuntimeMethods() {
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getTitle()).thenReturn("Jigsaw Studio");

        assertSame(inventory, UIWindow.inventoryViewTopInventory(view));
        assertSame("Jigsaw Studio", UIWindow.inventoryViewTitle(view));
    }

    @Test
    public void uiWindowBytecode_doesNotBindInventoryViewInvocationKind() throws IOException {
        List<String> directCalls = new ArrayList<>();
        try (InputStream bytecode = UIWindow.class.getResourceAsStream("UIWindow.class")) {
            assertNotNull(bytecode);
            ClassReader reader = new ClassReader(bytecode);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                            if (INVENTORY_VIEW_INTERNAL_NAME.equals(owner)) {
                                directCalls.add(methodName + methodDescriptor);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue("Direct InventoryView calls bind bytecode to either the class or interface ABI: " + directCalls, directCalls.isEmpty());
    }
}
