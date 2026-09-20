package art.arcane.volmlib.nativelib.advancement;

import art.arcane.volmlib.nativelib.NativeBinding;

@NativeBinding("advancement.AdvancementAccessImpl")
public interface AdvancementAccess {
    <T> Class<? extends T> wrapperClass(Class<T> wrapper);
    Class<?> minecraftClass(String name, String minecraftPackage) throws ClassNotFoundException;
    Class<?> craftClass(String name) throws ClassNotFoundException;
}
