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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockDropHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void iris$applyBlockDrops(BlockState state, ServerLevel level, BlockPos position, BlockEntity blockEntity,
                                             Entity breaker, ItemInstance tool, CallbackInfoReturnable<List<ItemStack>> info) {
        if (!(breaker instanceof Player)) {
            return;
        }
        NativeBlockDropHooks.Result result = NativeBlockDropHooks.completePrepared(level, position);
        if (result == null) {
            return;
        }
        if (result.routeCombinedDrops(info.getReturnValue())) {
            info.setReturnValue(List.of());
            return;
        }
        List<ItemStack> drops = result.replaceVanillaDrops()
                ? new ArrayList<>()
                : new ArrayList<>(info.getReturnValue());
        drops.addAll(result.drops());
        info.setReturnValue(drops);
    }
}
