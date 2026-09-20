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

package art.arcane.volmlib.nativelib.minecraft26_2.fabric.mixin;


import art.arcane.volmlib.nativelib.minecraft26_2.fabric.NativeFabricPackSources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void iris$addForcedDatapackSource(RepositorySource[] sources, CallbackInfo info) {
        for (RepositorySource source : sources) {
            if (source instanceof ServerPacksSource) {
                NativeFabricPackSources.attach((PackRepository) (Object) this);
                return;
            }
        }
        // Client resource-pack repositories legitimately have no ServerPacksSource; a missing server-data
        // repository is reported once at boot by ModdedForcedDatapack.verifyInjected().
        System.getLogger(PackRepositoryMixin.class.getName()).log(System.Logger.Level.DEBUG,
                "Server datapack source not attached: no server-data source among "
                        + sources.length + " source(s) " + Arrays.toString(sources));
    }
}
