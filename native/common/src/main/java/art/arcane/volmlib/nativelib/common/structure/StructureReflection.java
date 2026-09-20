/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.nativelib.common.structure;

import art.arcane.volmlib.nativelib.terrain.NativeStructureReader.ConnectorMetadata;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

final class StructureReflection {
    private StructureReflection() {
    }

    static Object resolveRegistryAccess(Object server) {
        try {
            Class<?> frozen = Class.forName("net.minecraft.core.RegistryAccess$Frozen");
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && frozen.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object o = m.invoke(server);
                    if (o != null) {
                        return o;
                    }
                }
            }
            Class<?> ra = Class.forName("net.minecraft.core.RegistryAccess");
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && ra.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object o = m.invoke(server);
                    if (o != null) {
                        return o;
                    }
                }
            }
        } catch (Throwable e) {
            throw new IllegalStateException("Could not read the server registry access", e);
        }
        return null;
    }

    static Object lookupRegistry(Object registryAccess, String registryName) throws Exception {
        Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
        Object resourceKey = registries.getField(registryName).get(null);
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
        Method registryOverload = null;
        for (Method m : registryAccess.getClass().getMethods()) {
            if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].getName().endsWith("ResourceKey")
                    && registryClass.isAssignableFrom(m.getReturnType())) {
                registryOverload = m;
                break;
            }
        }
        if (registryOverload == null) {
            for (Method m : registryAccess.getClass().getMethods()) {
                if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().endsWith("ResourceKey")) {
                    registryOverload = m;
                    break;
                }
            }
        }
        if (registryOverload == null) {
            throw new NoSuchMethodException("lookupOrThrow(ResourceKey) on " + registryAccess.getClass().getName());
        }
        registryOverload.setAccessible(true);
        return registryOverload.invoke(registryAccess, resourceKey);
    }

    static Object registryGet(Object registry, NamespacedKey key) throws Exception {
        Object id = identifierOf(key);
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getValue") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().endsWith("Identifier")) {
                m.setAccessible(true);
                return m.invoke(registry, id);
            }
        }
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getOptional") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().endsWith("Identifier")) {
                m.setAccessible(true);
                return unwrapOptional(m.invoke(registry, id));
            }
        }
        return null;
    }

    static String registryKeyOf(Object registry, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getKey") && m.getParameterCount() == 1) {
                m.setAccessible(true);
                Object id = m.invoke(registry, value);
                String s = identifierString(id);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    static Object identifierOf(NamespacedKey key) throws Exception {
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
        try {
            Method fromNamespaceAndPath = identifier.getMethod("fromNamespaceAndPath", String.class, String.class);
            return fromNamespaceAndPath.invoke(null, key.getNamespace(), key.getKey());
        } catch (NoSuchMethodException e) {
            Method withDefaultNamespace = identifier.getMethod("parse", String.class);
            return withDefaultNamespace.invoke(null, key.toString());
        }
    }

    static String identifierString(Object id) {
        if (id == null) {
            return null;
        }
        try {
            Method getNamespace = id.getClass().getMethod("getNamespace");
            Method getPath = id.getClass().getMethod("getPath");
            getNamespace.setAccessible(true);
            getPath.setAccessible(true);
            return getNamespace.invoke(id) + ":" + getPath.invoke(id);
        } catch (Throwable e) {
            return id.toString();
        }
    }

    static Object unwrapHolder(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Method value = findMethod(holder.getClass(), "value");
            if (value != null) {
                value.setAccessible(true);
                return value.invoke(holder);
            }
        } catch (Throwable ignored) {
        }
        return holder;
    }

    static Object unwrapOptional(Object opt) {
        if (opt == null) {
            return null;
        }
        if (opt instanceof java.util.Optional<?> o) {
            return o.orElse(null);
        }
        return opt;
    }

    static int readIntMember(Object value, String memberName) throws Exception {
        Class<?> type = value.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(memberName);
                field.setAccessible(true);
                return coerceInt(field.get(value), memberName, value);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        Method method = findMethod(value.getClass(), memberName);
        if (method != null) {
            method.setAccessible(true);
            return coerceInt(method.invoke(value), memberName, value);
        }
        throw new NoSuchFieldException(memberName + " on " + value.getClass().getName());
    }

    /**
     * Members that are plain numbers on one server build are wrapper objects on another
     * (JigsawStructure.maxDistanceFromCenter became a MaxDistance{horizontal, vertical} record).
     * Numbers pass through; a wrapper contributes its horizontal component, else its largest
     * integral component, so a distance bound is never under-read.
     */
    static int coerceInt(Object member, String memberName, Object owner) throws Exception {
        if (member instanceof Number n) {
            return n.intValue();
        }
        if (member == null) {
            throw new NoSuchFieldException(memberName + " on " + owner.getClass().getName() + " is null");
        }
        Method horizontal = findMethod(member.getClass(), "horizontal");
        if (horizontal != null && Number.class.isAssignableFrom(boxedType(horizontal.getReturnType()))) {
            horizontal.setAccessible(true);
            return ((Number) horizontal.invoke(member)).intValue();
        }
        Integer widest = null;
        for (Field component : member.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(component.getModifiers())
                    || !Number.class.isAssignableFrom(boxedType(component.getType()))) {
                continue;
            }
            component.setAccessible(true);
            Object componentValue = component.get(member);
            if (componentValue instanceof Number n && (widest == null || n.intValue() > widest)) {
                widest = n.intValue();
            }
        }
        if (widest != null) {
            return widest;
        }
        throw new NoSuchFieldException(memberName + " on " + owner.getClass().getName()
                + " is a " + member.getClass().getName() + " with no integral component");
    }

    static Class<?> boxedType(Class<?> type) {
        return type == int.class ? Integer.class : type;
    }

    static int readInt(Object o, String method) throws Exception {
        Method m = o.getClass().getMethod(method);
        m.setAccessible(true);
        return ((Number) m.invoke(o)).intValue();
    }

    static Object staticField(String className, String fieldName) throws Exception {
        Class<?> c = Class.forName(className);
        Field f = c.getField(fieldName);
        return f.get(null);
    }

    static Object invoke(Object target, String method) throws Exception {
        Method m = findMethod(target.getClass(), method);
        if (m == null) {
            throw new NoSuchMethodException(method + " on " + target.getClass().getName());
        }
        m.setAccessible(true);
        return m.invoke(target);
    }

    static Method findMethod(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> c = type;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                return m;
            }
        }
        return null;
    }

    static Object freshRandomSource(long seed) throws Exception {
        Class<?> randomSource = Class.forName("net.minecraft.util.RandomSource");
        Method create = randomSource.getMethod("create", long.class);
        return create.invoke(null, seed);
    }

    static String readNbtString(Object nbt, String key) throws Exception {
        if (nbt == null) {
            return null;
        }
        Method method = findMethod(nbt.getClass(), "getString", 1);
        if (method == null) {
            throw new NoSuchMethodException("getString(String) on " + nbt.getClass().getName());
        }
        method.setAccessible(true);
        Object value = method.invoke(nbt, key);
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? String.valueOf(optional.get()) : null;
        }
        return value == null ? null : String.valueOf(value);
    }

    static int readIntAccessor(Object value, String accessor) throws Exception {
        Method method = findMethod(value.getClass(), accessor);
        if (method == null) {
            throw new NoSuchMethodException(accessor + "() on " + value.getClass().getName());
        }
        method.setAccessible(true);
        Object result = method.invoke(value);
        if (result instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException(accessor + " on " + value.getClass().getName() + " is not numeric");
    }

    static String frontFacing(Object blockState) throws Exception {
        Class<?> jigsawBlock = Class.forName("net.minecraft.world.level.block.JigsawBlock");
        Method getFront = jigsawBlock.getMethod("getFrontFacing", Class.forName("net.minecraft.world.level.block.state.BlockState"));
        Object direction = getFront.invoke(null, blockState);
        if (direction == null) {
            return "north";
        }
        Method getName = direction.getClass().getMethod("getName");
        getName.setAccessible(true);
        return String.valueOf(getName.invoke(direction)).toLowerCase();
    }

    static String topFacing(Object blockState) throws Exception {
        Class<?> jigsawBlock = Class.forName("net.minecraft.world.level.block.JigsawBlock");
        Method getTop = jigsawBlock.getMethod("getTopFacing", Class.forName("net.minecraft.world.level.block.state.BlockState"));
        Object direction = getTop.invoke(null, blockState);
        if (direction == null) {
            return "up";
        }
        Method getName = direction.getClass().getMethod("getName");
        getName.setAccessible(true);
        return String.valueOf(getName.invoke(direction)).toLowerCase();
    }
    static ConnectorMetadata connectorMetadata(Object jigsaw, Object info) throws Exception {
        return new ConnectorMetadata(readNbtString(invoke(info, "nbt"), "final_state"),
                readIntAccessor(jigsaw, "selectionPriority"), readIntAccessor(jigsaw, "placementPriority"));
    }

}
