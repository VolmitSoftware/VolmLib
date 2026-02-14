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

public final class MCABlockStateCodecSupport {
    private MCABlockStateCodecSupport() {
    }

    public static CompoundTag encodeBlockState(String blockDataString, String namespacedMaterialKey) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", namespacedMaterialKey);

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

            tag.put("Properties", props);
        }

        return tag;
    }

    public static String decodeBlockStateString(CompoundTag tag) {
        if (tag == null) {
            return null;
        }

        StringBuilder p = new StringBuilder(tag.getString("Name"));
        if (tag.containsKey("Properties")) {
            CompoundTag props = tag.getCompoundTag("Properties");
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
}
