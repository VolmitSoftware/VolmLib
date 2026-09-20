package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import java.lang.reflect.Method;

public enum NativeMixinTarget {
    ENTITY("net.minecraft.world.entity.Entity"),
    LIVING_ENTITY("net.minecraft.world.entity.LivingEntity"),
    MOB("net.minecraft.world.entity.Mob"),
    STRUCTURE_PALETTE("net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$Palette"),
    WORLD_OPEN_FLOWS("net.minecraft.client.gui.screens.worldselection.WorldOpenFlows"),
    WORLD_TYPE_ENTRY("net.minecraft.client.gui.screens.worldselection.WorldCreationUiState$WorldTypeEntry");

    private final String className;

    NativeMixinTarget(String className) {
        this.className = className;
    }

    public boolean hasInjectedHandler(String handlerMethod) throws ClassNotFoundException {
        Class<?> target = Class.forName(className, false, NativeMixinTarget.class.getClassLoader());
        for (Method method : target.getDeclaredMethods()) {
            String name = method.getName();
            if (name.equals(handlerMethod) || name.endsWith('$' + handlerMethod)) {
                return true;
            }
        }
        return false;
    }
}
