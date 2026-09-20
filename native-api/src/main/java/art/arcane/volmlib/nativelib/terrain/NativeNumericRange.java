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

import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;

/**
 * Inclusive-or-exclusive numeric bounds for a {@link NativeBlockProperty}, mirroring JSON schema's
 * {@code minimum}/{@code maximum} pair.
 * <p>
 * Immutable. Bounds are carried as {@code double} regardless of the property's JSON type; an
 * {@code integer} property's bounds are whole numbers and are narrowed by the schema writer. Shared native metadata contract.
 *
 * @param minimum          lower bound
 * @param maximum          upper bound
 * @param exclusiveMinimum whether {@code minimum} itself is disallowed
 * @param exclusiveMaximum whether {@code maximum} itself is disallowed
 */
public record NativeNumericRange(double minimum, double maximum, boolean exclusiveMinimum, boolean exclusiveMaximum) {
}
