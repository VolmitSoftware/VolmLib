package art.arcane.volmlib.nativelib.common.block;

import art.arcane.volmlib.nativelib.block.BlockEntityAccess;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public class ReflectiveBlockEntityAccess implements BlockEntityAccess {
    private final ClassValue<Optional<Method>> snapshotAccessors = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod("getSnapshotNBT"));
            } catch (NoSuchMethodException absent) {
                return Optional.empty();
            }
        }
    };
    private volatile Method nbtWriter;
    private volatile LockReflection lockReflection;

    @Override
    public byte[] snapshotNbt(BlockState state) throws ReflectiveOperationException {
        if (state == null) {
            return null;
        }
        Optional<Method> accessor = snapshotAccessors.get(state.getClass());
        if (accessor.isEmpty()) {
            return null;
        }
        Object tag = accessor.get().invoke(state);
        if (tag == null) {
            return null;
        }
        Method writer = nbtWriter;
        if (writer == null) {
            writer = resolveWriter(tag.getClass());
            nbtWriter = writer;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
        writer.invoke(null, tag, new DataOutputStream(buffer));
        return buffer.toByteArray();
    }

    @Override
    public boolean matchesLock(BlockState state, ItemStack keyItem) throws ReflectiveOperationException {
        LockReflection reflection = lockReflection;
        if (reflection == null) {
            reflection = resolveLock(state);
            lockReflection = reflection;
        }
        Object blockEntity = reflection.getBlockEntity().invoke(state);
        Object lockCode = reflection.lockKey().get(blockEntity);
        Object nativeItem = reflection.asNmsCopy().invoke(null, keyItem);
        return (boolean) reflection.unlocksWith().invoke(lockCode, nativeItem);
    }

    private static Method resolveWriter(Class<?> tagClass) throws ReflectiveOperationException {
        Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo", false, tagClass.getClassLoader());
        for (Method candidate : nbtIo.getMethods()) {
            if (!"write".equals(candidate.getName()) || !Modifier.isStatic(candidate.getModifiers())) {
                continue;
            }
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length == 2 && parameters[0].isAssignableFrom(tagClass) && parameters[1] == DataOutput.class) {
                return candidate;
            }
        }
        throw new NoSuchMethodException("Native block entity NBT writer is unavailable");
    }

    private static LockReflection resolveLock(BlockState state) throws ReflectiveOperationException {
        Method getBlockEntity = state.getClass().getMethod("getBlockEntity");
        Object blockEntity = getBlockEntity.invoke(state);
        Field lockKey = blockEntity.getClass().getField("lockKey");
        Object lockCode = lockKey.get(blockEntity);
        ClassLoader classLoader = state.getClass().getClassLoader();
        Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack", true, classLoader);
        Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
        Method unlocksWith = lockCode.getClass().getMethod("unlocksWith", asNmsCopy.getReturnType());
        return new LockReflection(getBlockEntity, lockKey, asNmsCopy, unlocksWith);
    }

    private record LockReflection(Method getBlockEntity, Field lockKey, Method asNmsCopy, Method unlocksWith) {
    }
}
