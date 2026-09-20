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

package art.arcane.volmlib.nativelib.view;

public record WorldMarker(String label, double worldX, double worldY, double worldZ, double health, double maxHealth) {
    public static WorldMarker player(String name, double worldX, double worldZ) {
        return new WorldMarker(name, worldX, 0, worldZ, 0, 0);
    }

    public static WorldMarker entity(String label, double worldX, double worldY, double worldZ, double health, double maxHealth) {
        return new WorldMarker(label, worldX, worldY, worldZ, health, maxHealth);
    }
}
