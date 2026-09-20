package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

public record NativeBlockBreakContext(NativeWorld world, NativeProtocolPlayer player,
                                      NativeBlockPoint position, NativeBlockState state) {
}
