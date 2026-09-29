package art.arcane.volmlib.nativelib.v26_3_R1.item;

import art.arcane.volmlib.nativelib.item.ItemStackAccess;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ItemStackAccessImpl implements ItemStackAccess {
    @Override
    public byte[] encode(org.bukkit.inventory.ItemStack item) throws IOException {
        ItemStack nativeItem = CraftItemStack.asNMSCopy(item);
        if (nativeItem.isEmpty()) {
            throw new IllegalArgumentException("Item must not be empty");
        }
        CompoundTag tag = (CompoundTag) ItemStack.CODEC.encodeStart(
            MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE), nativeItem).getOrThrow();
        NbtUtils.addCurrentDataVersion(tag);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, bytes);
            return bytes.toByteArray();
        }
    }

    @Override
    public org.bukkit.inventory.ItemStack decode(byte[] encoded) throws IOException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(encoded)) {
            CompoundTag tag = NbtIo.readCompressed(bytes, NbtAccounter.create(16L * 1024 * 1024));
            tag = (CompoundTag) DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag),
                NbtUtils.getDataVersion(tag, 0), SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
            ItemStack item = ItemStack.CODEC.parse(
                MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
            if (item.isEmpty()) {
                throw new IllegalArgumentException("Item must not be empty");
            }
            return CraftItemStack.asBukkitCopy(item);
        }
    }
}
