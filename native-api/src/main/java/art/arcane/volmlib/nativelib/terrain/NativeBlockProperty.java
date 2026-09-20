/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.volmlib.nativelib.terrain;

import java.util.List;

/**
 * One block state property as JSON schema vocabulary, so pack schema generation can describe block keys without
 * knowing platform property types.
 * <p>
 * Immutable metadata for schema generation, separate from the generation path.
 *
 * @param name          the property name as it appears in a block key, for example {@code waterlogged}
 * @param jsonType      JSON schema type: {@code boolean}, {@code integer} or {@code string}. Never null or empty
 * @param defaultValue  the value the host's default state carries, boxed as its JSON representation
 * @param allowedValues every legal value, empty when the adapter cannot enumerate them. Never null
 * @param numericRange  bounds for a numeric property, null for {@code boolean} and {@code string}
 */
public record NativeBlockProperty(String name, String jsonType, Object defaultValue, List<Object> allowedValues, NativeNumericRange numericRange) {
    /**
     * Whether {@link #numericRange()} is present, and therefore whether schema output should emit bounds.
     */
    public boolean hasNumericRange() {
        return numericRange != null;
    }
}
