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

import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public abstract class ReflectiveStructureReader implements NativeStructureReader {
    protected Session session(Object registries, Object templates) {
        return new SessionView(registries, templates, this::readConnectors);
    }

    private record SessionView(Object registries, Object templates, ConnectorReader connectors) implements Session {
        @Override
        public Structure structure(String key) throws Exception {
            Object registry = StructureReflection.lookupRegistry(registries, "STRUCTURE");
            NamespacedKey id = NamespacedKey.fromString(key.toLowerCase());
            Object value = id == null ? null : StructureReflection.registryGet(registry, id);
            return value == null ? null : new StructureView(value, this);
        }

        @Override
        public Pool pool(String key) throws Exception {
            NamespacedKey id = NamespacedKey.fromString(key.toLowerCase());
            Object registry = pools();
            Object value = id == null ? null : StructureReflection.registryGet(registry, id);
            return value == null ? null : new PoolView(value, this);
        }

        Object pools() throws Exception {
            return StructureReflection.lookupRegistry(registries, "TEMPLATE_POOL");
        }

        String poolKey(Object holder) throws Exception {
            return StructureReflection.registryKeyOf(pools(), StructureReflection.unwrapHolder(holder));
        }
    }

    private record StructureView(Object value, SessionView session) implements Structure {
        @Override
        public String typeName() { return value.getClass().getSimpleName(); }
        @Override
        public boolean jigsaw() { return value.getClass().getName().endsWith("JigsawStructure"); }
        @Override
        public String startPoolKey() throws Exception { return session.poolKey(StructureReflection.invoke(value, "getStartPool")); }
        @Override
        public int maxDepth() throws Exception { return StructureReflection.readIntMember(value, "maxDepth"); }
        @Override
        public int maxDistanceFromCenter() throws Exception { return StructureReflection.readIntMember(value, "maxDistanceFromCenter"); }
    }

    private record PoolView(Object value, SessionView session) implements Pool {
        @Override
        public String fallbackKey() throws Exception {
            return session.poolKey(StructureReflection.invoke(value, "getFallback"));
        }

        @Override
        public List<Entry> entries() throws Exception {
            List<?> templates = (List<?>) StructureReflection.invoke(value, "getTemplates");
            List<Entry> entries = new ArrayList<>(templates.size());
            for (Object pair : templates) {
                entries.add(new EntryView(pair, session.templates(), session.connectors()));
            }
            return entries;
        }
    }

    private record EntryView(Object value, Object templates, ConnectorReader connectors) implements Entry {
        @Override
        public Element element() throws Exception {
            Object element = StructureReflection.invoke(value, "getFirst");
            return element == null ? null : new ElementView(element, templates, connectors);
        }

        @Override
        public int weight() throws Exception {
            Object weight = StructureReflection.invoke(value, "getSecond");
            return weight instanceof Number number ? number.intValue() : 1;
        }
    }

    static final class ElementView implements Element {
        private final Object value;
        private final Object templates;
        private final ConnectorReader connectors;

        ElementView(Object value, Object templates, ConnectorReader connectors) {
            this.value = value;
            this.templates = templates;
            this.connectors = connectors;
        }

        @Override
        public String typeName() { return value.getClass().getSimpleName(); }

        @Override
        public String templateLocation() throws Exception {
            Method method = StructureReflection.findMethod(value.getClass(), "getTemplateLocation");
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return StructureReflection.identifierString(method.invoke(value));
        }

        @Override
        public List<Element> children() throws Exception {
            if (!typeName().endsWith("ListPoolElement")) {
                return null;
            }
            Object raw = StructureReflection.invoke(value, "getElements");
            if (!(raw instanceof List<?> children)) {
                throw new IllegalStateException("getElements on " + value.getClass().getName() + " is not a list");
            }
            List<Element> elements = new ArrayList<>(children.size());
            for (Object child : children) {
                elements.add(child == null ? null : new ElementView(child, templates, connectors));
            }
            return elements;
        }

        @Override
        public ConnectorSet connectors(LongSupplier randomSeed) throws Exception {
            return connectors.read(value, templates, randomSeed);
        }
    }

    protected ConnectorSet readConnectors(Object value, Object templates, LongSupplier randomSeed) throws Exception {
        return readDefaultConnectors(value, templates, randomSeed);
    }

    static ConnectorSet readDefaultConnectors(Object value, Object templates, LongSupplier randomSeed) throws Exception {
        Method method = StructureReflection.findMethod(value.getClass(), "getShuffledJigsawBlocks", 4);
        if (method == null) {
            return new ConnectorSet(ConnectorStatus.UNSUPPORTED, List.of());
        }
        method.setAccessible(true);
        Object zero = StructureReflection.staticField("net.minecraft.core.BlockPos", "ZERO");
        Object rotation = StructureReflection.staticField("net.minecraft.world.level.block.Rotation", "NONE");
        Object random = StructureReflection.freshRandomSource(randomSeed.getAsLong());
        List<?> values = (List<?>) method.invoke(value, templates, zero, rotation, random);
        if (values == null) {
            return new ConnectorSet(ConnectorStatus.MISSING, List.of());
        }
        List<Connector> connectors = new ArrayList<>(values.size());
        for (Object connector : values) {
            connectors.add(new ConnectorView(connector));
        }
        return new ConnectorSet(ConnectorStatus.AVAILABLE, connectors);
    }

    @FunctionalInterface
    interface ConnectorReader {
        ConnectorSet read(Object value, Object templates, LongSupplier randomSeed) throws Exception;
    }

    private record ConnectorView(Object value) implements Connector {
        @Override
        public ConnectorData read() throws Exception {
            Object info = StructureReflection.invoke(value, "info");
            Object pos = StructureReflection.invoke(info, "pos");
            Object state = StructureReflection.invoke(info, "state");
            Object pool = StructureReflection.invoke(value, "pool");
            Object joint = StructureReflection.invoke(value, "jointType");
            return new ConnectorData(StructureReflection.readInt(pos, "getX"), StructureReflection.readInt(pos, "getY"),
                    StructureReflection.readInt(pos, "getZ"), StructureReflection.frontFacing(state), StructureReflection.topFacing(state),
                    StructureReflection.identifierString(StructureReflection.invoke(pool, "identifier")),
                    StructureReflection.identifierString(StructureReflection.invoke(value, "name")),
                    StructureReflection.identifierString(StructureReflection.invoke(value, "target")),
                    joint == null ? null : joint.toString(), StructureReflection.connectorMetadata(value, info));
        }
    }
}
