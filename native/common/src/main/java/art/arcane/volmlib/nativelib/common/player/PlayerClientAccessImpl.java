package art.arcane.volmlib.nativelib.common.player;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import art.arcane.volmlib.nativelib.player.ClientBlockTags;
import art.arcane.volmlib.nativelib.player.PlayerClientAccess;

public class PlayerClientAccessImpl implements PlayerClientAccess {
  private static volatile MethodHandle networkPayloadConstructor;

  @Override
  public boolean isServerPlayerClass(Class<?> playerClass) {
    return playerClass == CraftPlayer.class;
  }

  @Override
  public boolean sendVerticalMotion(Player p, double targetY) {
    if (!(p instanceof CraftPlayer craftPlayer)) {
      return false;
    }

    ServerPlayer handle = craftPlayer.getHandle();
    Vec3 knownMovement = handle.getKnownMovement();
    Vec3 targetMovement = new Vec3(knownMovement.x, targetY, knownMovement.z);
    handle.setDeltaMovement(targetMovement);
    handle.connection.send(new ClientboundSetEntityMotionPacket(handle.getId(), targetMovement));
    return true;
  }

  @Override
  public ClientBlockTags createBlockTags() {
    return new BlockTagsSession();
  }

  private static final class BlockTagsSession implements ClientBlockTags {
    private final AtomicLong clientTagGeneration = new AtomicLong();
    private final Object clientTagPacketLock = new Object();
    private volatile ClientTagPackets clientTagPackets;

    @Override
    public void invalidate() {
      synchronized (clientTagPacketLock) {
        clientTagPackets = null;
        clientTagGeneration.incrementAndGet();
      }
    }
    @Override
    public boolean sendClimbingState(Player p, Material suppressedMaterial) {
      if (!(p instanceof CraftPlayer craftPlayer)) {
        return false;
      }
  
      ServerPlayer handle = craftPlayer.getHandle();
      ClientTagPackets packets = getClientTagPackets(handle);
      ClientboundUpdateTagsPacket packet = suppressedMaterial == null
          ? packets.standard()
          : packets.suppressed(suppressedMaterial);
      handle.connection.send(packet);
      return true;
    }
  
    private ClientTagPackets getClientTagPackets(ServerPlayer player) {
      long generation = clientTagGeneration.get();
      ClientTagPackets packets = clientTagPackets;
      if (packets != null && packets.generation() == generation) {
        return packets;
      }
  
      synchronized (clientTagPacketLock) {
        packets = clientTagPackets;
        generation = clientTagGeneration.get();
        if (packets == null || packets.generation() != generation) {
          packets = buildClientTagPackets(
              player.level().getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
              BlockTags.CLIMBABLE,
              generation
          );
          clientTagPackets = packets;
        }
        return packets;
      }
    }

  }

  private static <T> ClientTagPackets buildClientTagPackets(Registry<T> registry, TagKey<T> climbableTag, long generation) {
    Map<Identifier, IntList> standardTags = new HashMap<>();
    for (HolderSet.Named<T> named : registry.getTags().toList()) {
      IntList ids = new IntArrayList(named.size());
      for (Holder<T> holder : named) {
        ids.add(registry.getId(holder.value()));
      }
      standardTags.put(named.key().location(), ids);
    }

    Map<Identifier, Integer> blockIds = new HashMap<>();
    for (Identifier identifier : registry.keySet()) {
      T value = registry.getValue(identifier);
      blockIds.put(identifier, registry.getId(value));
    }

    Map<Identifier, IntList> immutableTags = Map.copyOf(standardTags);
    return new ClientTagPackets(
        generation,
        climbableTag.location(),
        immutableTags,
        Map.copyOf(blockIds),
        blockTagPacket(newNetworkPayload(immutableTags))
    );
  }

  /** The NetworkPayload(Map) constructor is package-private on 26.1.x and public on 26.2+. */
  private static TagNetworkSerialization.NetworkPayload newNetworkPayload(Map<Identifier, IntList> tags) {
    try {
      MethodHandle constructor = networkPayloadConstructor;
      if (constructor == null) {
        Constructor<TagNetworkSerialization.NetworkPayload> declared =
            TagNetworkSerialization.NetworkPayload.class.getDeclaredConstructor(Map.class);
        declared.setAccessible(true);
        constructor = MethodHandles.lookup().unreflectConstructor(declared);
        networkPayloadConstructor = constructor;
      }
      return (TagNetworkSerialization.NetworkPayload) constructor.invoke(tags);
    } catch (Throwable error) {
      throw new IllegalStateException("Unable to construct a client block tag payload.", error);
    }
  }

  static Map<Identifier, IntList> suppressBlockInTag(
      Map<Identifier, IntList> standardTags,
      Identifier tag,
      int suppressedBlockId
  ) {
    IntList original = standardTags.get(tag);
    if (original == null) {
      throw new IllegalStateException("Missing client block tag " + tag);
    }

    IntList filtered = new IntArrayList(original.size());
    boolean removed = false;
    for (int index = 0; index < original.size(); index++) {
      int blockId = original.getInt(index);
      if (blockId == suppressedBlockId) {
        removed = true;
      } else {
        filtered.add(blockId);
      }
    }
    if (!removed) {
      throw new IllegalStateException("Block " + suppressedBlockId + " is not in client block tag " + tag);
    }

    Map<Identifier, IntList> suppressedTags = new HashMap<>(standardTags);
    suppressedTags.put(tag, filtered);
    return Map.copyOf(suppressedTags);
  }

  private static ClientboundUpdateTagsPacket blockTagPacket(TagNetworkSerialization.NetworkPayload payload) {
    Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> registryTags = new HashMap<>(1);
    registryTags.put(Registries.BLOCK, payload);
    return new ClientboundUpdateTagsPacket(registryTags);
  }

  private static final class ClientTagPackets {
    private final long generation;
    private final Identifier climbableTag;
    private final Map<Identifier, IntList> standardTags;
    private final Map<Identifier, Integer> blockIds;
    private final ClientboundUpdateTagsPacket standard;
    private final ConcurrentMap<Integer, ClientboundUpdateTagsPacket> suppressed = new ConcurrentHashMap<>();

    private ClientTagPackets(
        long generation,
        Identifier climbableTag,
        Map<Identifier, IntList> standardTags,
        Map<Identifier, Integer> blockIds,
        ClientboundUpdateTagsPacket standard
    ) {
      this.generation = generation;
      this.climbableTag = climbableTag;
      this.standardTags = standardTags;
      this.blockIds = blockIds;
      this.standard = standard;
    }

    private long generation() {
      return generation;
    }

    private ClientboundUpdateTagsPacket standard() {
      return standard;
    }

    private ClientboundUpdateTagsPacket suppressed(Material material) {
      NamespacedKey key = material.getKey();
      Identifier identifier = Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey());
      Integer blockId = blockIds.get(identifier);
      if (blockId == null) {
        throw new IllegalStateException("Missing client block registry entry " + identifier);
      }

      return suppressed.computeIfAbsent(blockId, id -> blockTagPacket(newNetworkPayload(
          suppressBlockInTag(standardTags, climbableTag, id)
      )));
    }
  }

}
