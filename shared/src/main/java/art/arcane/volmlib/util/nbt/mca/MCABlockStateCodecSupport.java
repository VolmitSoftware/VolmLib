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

package art.arcane.volmlib.util.nbt.mca;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

public final class MCABlockStateCodecSupport {
    private MCABlockStateCodecSupport() {
    }

    public static CompoundTag encodeBlockState(String blockDataString, String namespacedMaterialKey, Format format) {
        CompoundTag tag = new CompoundTag();
        tag.putString(format.nameKey(), namespacedMaterialKey);

        if (blockDataString.contains("[")) {
            String raw = blockDataString.split("\\Q[\\E")[1].replaceAll("\\Q]\\E", "");
            CompoundTag props = new CompoundTag();

            if (raw.contains(",")) {
                for (String i : raw.split("\\Q,\\E")) {
                    String[] m = i.split("\\Q=\\E");
                    props.put(m[0], new StringTag(m[1]));
                }
            } else {
                String[] m = raw.split("\\Q=\\E");
                props.put(m[0], new StringTag(m[1]));
            }

            tag.put(format.propertiesKey(), props);
        }

        return tag;
    }

    public static String decodeBlockStateString(Tag<?> tag, Format format) {
        if (tag == null) {
            return null;
        }

        if (format == Format.LOWERCASE && tag instanceof StringTag name) {
            return name.getValue();
        }
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Expected a compound block state");
        }
        if (format == Format.LOWERCASE && compound.size() == 1
                && compound.get("") instanceof StringTag name) {
            return name.getValue();
        }

        StringBuilder p = new StringBuilder(compound.getString(format.nameKey()));
        if (compound.containsKey(format.propertiesKey())) {
            CompoundTag props = compound.getCompoundTag(format.propertiesKey());
            p.append('[');

            for (String i : props.keySet()) {
                p.append(i).append('=').append(props.getString(i)).append(',');
            }

            if (!props.keySet().isEmpty()) {
                p.deleteCharAt(p.length() - 1);
            }
            p.append(']');
        }

        return p.toString();
    }

    public enum Format {
        CAPITALIZED("Name", "Properties"),
        LOWERCASE("id", "properties");

        private final String nameKey;
        private final String propertiesKey;

        Format(String nameKey, String propertiesKey) {
            this.nameKey = nameKey;
            this.propertiesKey = propertiesKey;
        }

        public String nameKey() {
            return nameKey;
        }

        public String propertiesKey() {
            return propertiesKey;
        }
    }
}
