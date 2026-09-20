package art.arcane.volmlib.nativelib.common.entity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import art.arcane.volmlib.nativelib.entity.ClientPacket;
import art.arcane.volmlib.nativelib.entity.VirtualPlayer;
import art.arcane.volmlib.nativelib.entity.VirtualPlayers;
import org.bukkit.plugin.Plugin;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public final class VirtualPlayersImpl implements VirtualPlayers {
  private final boolean supported;
  private final Plugin plugin;
  private final BiConsumer<Player, Runnable> viewerExecutor;
  private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

  private final Method craftServerGetServer;
  private final Method craftWorldGetHandle;
  private final Method craftPlayerGetHandle;

  private final Constructor<?> serverPlayerConstructor;
  private final Method clientInformationCreateDefault;

  private final Constructor<?> gameProfileBasicConstructor;
  private final Constructor<?> gameProfileWithPropertiesConstructor;
  private final Method gameProfilePropertiesAccessor;
  private final Method playerGetGameProfile;

  private final Method entitySetPos;
  private final Method entitySetRot;
  private final Method entitySetOnGround;
  private final Method livingSetYHeadRot;
  private final Method livingSetYBodyRot;
  private final Method entityGetId;
  private final Method entityGetType;
  private final Method entityGetEntityData;
  private final Method synchedEntityDataGetNonDefaultValues;
  private final Method synchedEntityDataPackAll;
  private final Method synchedEntityDataSet;

  private final Method playerInfoCreateSingleInitializing;
  private final Constructor<?> playerInfoActionConstructor;
  private final Constructor<?> playerInfoFromEntriesConstructor;
  private final Constructor<?> playerInfoEntryExplicitConstructor;
  private final Class<?> playerInfoActionClass;
  private final Object defaultGameType;

  private final Constructor<?> addEntityConstructor;
  private final Constructor<?> addEntityExplicitConstructor;
  private final Field blockPosZero;
  private final Field vec3Zero;
  private final Constructor<?> setEntityDataConstructor;
  private final Constructor<?> moveEntityRotConstructor;
  private final Constructor<?> rotateHeadConstructor;
  private final Constructor<?> removeEntitiesConstructor;
  private final Constructor<?> playerInfoRemoveConstructor;
  private final Method entityPositionSyncOf;
  private final Constructor<?> hurtAnimationConstructor;
  private final Constructor<?> setEquipmentConstructor;
  private final Method pairOfMethod;
  private final Method craftItemStackAsNmsCopy;
  private final Class<?> equipmentSlotClass;
  private final Field avatarModelCustomizationAccessor;

  private final Field serverPlayerConnectionField;
  private final Method connectionSendPacket;

  private VirtualPlayersImpl(Plugin plugin, BiConsumer<Player, Runnable> viewerExecutor) throws ReflectiveOperationException {
    this.plugin = plugin;
    this.viewerExecutor = viewerExecutor;
    String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
    if (!craftPackage.startsWith("org.bukkit.craftbukkit")) {
      throw new ClassNotFoundException("CraftBukkit package not detected: " + craftPackage);
    }

    Class<?> craftServerClass = Class.forName(craftPackage + ".CraftServer");
    Class<?> craftWorldClass = Class.forName(craftPackage + ".CraftWorld");
    Class<?> craftPlayerClass = Class.forName(craftPackage + ".entity.CraftPlayer");

    Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
    Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
    Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
    Class<?> clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
    Class<?> nmsPlayerClass = Class.forName("net.minecraft.world.entity.player.Player");
    Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
    Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
    Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
    Class<?> avatarClass = Class.forName("net.minecraft.world.entity.Avatar");
    Class<?> equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
    Class<?> entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
    Class<?> synchedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
    Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
    Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
    Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
    Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
    Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
    Class<?> playerInfoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
    Class<?> playerInfoEntryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
    Class<?> playerInfoActionEnumClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
    Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
    Class<?> remoteChatSessionDataClass = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
    Class<?> addEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
    Class<?> entityPositionSyncPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket");
    Class<?> hurtAnimationPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket");
    Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
    Class<?> setEntityDataPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
    Class<?> moveEntityRotClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot");
    Class<?> rotateHeadPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
    Class<?> removeEntitiesPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
    Class<?> playerInfoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
    Class<?> setEquipmentPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
    Class<?> craftItemStackClass = Class.forName(craftPackage + ".inventory.CraftItemStack");

    this.craftServerGetServer = findMethod(craftServerClass, "getServer");
    this.craftWorldGetHandle = findMethod(craftWorldClass, "getHandle");
    this.craftPlayerGetHandle = findMethod(craftPlayerClass, "getHandle");

    this.serverPlayerConstructor = serverPlayerClass.getConstructor(minecraftServerClass, serverLevelClass, gameProfileClass, clientInformationClass);
    this.clientInformationCreateDefault = findMethod(clientInformationClass, "createDefault");

    this.gameProfileBasicConstructor = findConstructor(gameProfileClass, UUID.class, String.class);
    this.gameProfileWithPropertiesConstructor = findOptionalConstructor(gameProfileClass, UUID.class, String.class, findPropertyMapClass(gameProfileClass));
    this.gameProfilePropertiesAccessor = findOptionalMethod(gameProfileClass, "properties", "getProperties");
    this.playerGetGameProfile = findMethod(nmsPlayerClass, "getGameProfile");

    this.entitySetPos = findMethod(entityClass, "setPos", double.class, double.class, double.class);
    this.entitySetRot = findMethod(entityClass, "setRot", float.class, float.class);
    this.entitySetOnGround = findMethod(entityClass, "setOnGround", boolean.class);
    this.livingSetYHeadRot = findMethod(livingEntityClass, "setYHeadRot", float.class);
    this.livingSetYBodyRot = findMethod(livingEntityClass, "setYBodyRot", float.class);
    this.entityGetId = findMethod(entityClass, "getId");
    this.entityGetType = findMethod(entityClass, "getType");
    this.entityGetEntityData = findMethod(entityClass, "getEntityData");
    this.synchedEntityDataGetNonDefaultValues = findMethod(synchedEntityDataClass, "getNonDefaultValues");
    this.synchedEntityDataPackAll = findOptionalMethod(synchedEntityDataClass, "packAll", new Class<?>[0]);
    this.synchedEntityDataSet = findMethod(synchedEntityDataClass, "set", entityDataAccessorClass, Object.class);

    this.playerInfoCreateSingleInitializing = findOptionalMethod(playerInfoPacketClass, "createSinglePlayerInitializing", serverPlayerClass, boolean.class);
    this.playerInfoActionConstructor = findOptionalConstructor(playerInfoPacketClass, playerInfoActionEnumClass, serverPlayerClass);
    this.playerInfoFromEntriesConstructor = findOptionalConstructor(playerInfoPacketClass, EnumSet.class, List.class);
    this.playerInfoEntryExplicitConstructor = findOptionalConstructor(playerInfoEntryClass, UUID.class, gameProfileClass, boolean.class, int.class, gameTypeClass, componentClass, boolean.class, int.class, remoteChatSessionDataClass);
    this.playerInfoActionClass = playerInfoActionEnumClass;
    this.defaultGameType = resolveDefaultGameType(gameTypeClass);

    this.addEntityConstructor = addEntityPacketClass.getConstructor(entityClass, int.class, blockPosClass);
    this.addEntityExplicitConstructor = findOptionalConstructor(addEntityPacketClass, int.class, UUID.class, double.class, double.class, double.class, float.class, float.class, entityTypeClass, int.class, vec3Class, double.class);
    this.blockPosZero = blockPosClass.getField("ZERO");
    this.vec3Zero = vec3Class.getField("ZERO");
    this.setEntityDataConstructor = setEntityDataPacketClass.getConstructor(int.class, List.class);
    this.moveEntityRotConstructor = moveEntityRotClass.getConstructor(int.class, byte.class, byte.class, boolean.class);
    this.rotateHeadConstructor = rotateHeadPacketClass.getConstructor(entityClass, byte.class);
    this.removeEntitiesConstructor = removeEntitiesPacketClass.getConstructor(int[].class);
    this.playerInfoRemoveConstructor = playerInfoRemovePacketClass.getConstructor(List.class);
    this.entityPositionSyncOf = findMethod(entityPositionSyncPacketClass, "of", entityClass);
    this.hurtAnimationConstructor = findOptionalConstructor(hurtAnimationPacketClass, int.class, float.class);
    this.setEquipmentConstructor = findOptionalConstructor(setEquipmentPacketClass, int.class, List.class);
    this.pairOfMethod = findMethod(pairClass, "of", Object.class, Object.class);
    this.craftItemStackAsNmsCopy = findMethod(craftItemStackClass, "asNMSCopy", ItemStack.class);
    this.equipmentSlotClass = equipmentSlotClass;
    this.avatarModelCustomizationAccessor = avatarClass.getField("DATA_PLAYER_MODE_CUSTOMISATION");

    this.serverPlayerConnectionField = serverPlayerClass.getField("connection");
    this.connectionSendPacket = findMethod(connectionClass, "send", packetClass);
    this.supported = true;
  }

  private VirtualPlayersImpl(Plugin plugin, BiConsumer<Player, Runnable> viewerExecutor, boolean supported) {
    this.plugin = plugin;
    this.viewerExecutor = viewerExecutor;
    this.supported = supported;
    this.craftServerGetServer = null;
    this.craftWorldGetHandle = null;
    this.craftPlayerGetHandle = null;
    this.serverPlayerConstructor = null;
    this.clientInformationCreateDefault = null;
    this.gameProfileBasicConstructor = null;
    this.gameProfileWithPropertiesConstructor = null;
    this.gameProfilePropertiesAccessor = null;
    this.playerGetGameProfile = null;
    this.entitySetPos = null;
    this.entitySetRot = null;
    this.entitySetOnGround = null;
    this.livingSetYHeadRot = null;
    this.livingSetYBodyRot = null;
    this.entityGetId = null;
    this.entityGetType = null;
    this.entityGetEntityData = null;
    this.synchedEntityDataGetNonDefaultValues = null;
    this.synchedEntityDataPackAll = null;
    this.synchedEntityDataSet = null;
    this.playerInfoCreateSingleInitializing = null;
    this.playerInfoActionConstructor = null;
    this.playerInfoFromEntriesConstructor = null;
    this.playerInfoEntryExplicitConstructor = null;
    this.playerInfoActionClass = null;
    this.defaultGameType = null;
    this.addEntityConstructor = null;
    this.addEntityExplicitConstructor = null;
    this.blockPosZero = null;
    this.vec3Zero = null;
    this.setEntityDataConstructor = null;
    this.moveEntityRotConstructor = null;
    this.rotateHeadConstructor = null;
    this.removeEntitiesConstructor = null;
    this.playerInfoRemoveConstructor = null;
    this.entityPositionSyncOf = null;
    this.hurtAnimationConstructor = null;
    this.setEquipmentConstructor = null;
    this.pairOfMethod = null;
    this.craftItemStackAsNmsCopy = null;
    this.equipmentSlotClass = null;
    this.avatarModelCustomizationAccessor = null;
    this.serverPlayerConnectionField = null;
    this.connectionSendPacket = null;
  }

  public static VirtualPlayersImpl create(Plugin plugin, BiConsumer<Player, Runnable> viewerExecutor) {
    try {
      return new VirtualPlayersImpl(plugin, viewerExecutor);
    } catch (Throwable e) {
      plugin.getLogger().log(Level.WARNING, "Virtual player backend is unavailable", e);
      return new VirtualPlayersImpl(plugin, viewerExecutor, false);
    }
  }

  private static byte toAngle(float degrees) {
    return (byte) (degrees * 256.0F / 360.0F);
  }

  private static Constructor<?> findConstructor(Class<?> type, Class<?>... parameterTypes) throws NoSuchMethodException {
    Constructor<?> constructor = type.getConstructor(parameterTypes);
    constructor.setAccessible(true);
    return constructor;
  }

  private static Constructor<?> findOptionalConstructor(Class<?> type, Class<?>... parameterTypes) {
    if (Arrays.stream(parameterTypes).anyMatch(c -> c == null)) {
      return null;
    }

    try {
      Constructor<?> constructor = type.getConstructor(parameterTypes);
      constructor.setAccessible(true);
      return constructor;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    try {
      return type.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException e) {
      Class<?> current = type;
      while (current != null) {
        try {
          Method method = current.getDeclaredMethod(name, parameterTypes);
          method.setAccessible(true);
          return method;
        } catch (NoSuchMethodException ignored) {
        }

        current = current.getSuperclass();
      }

      throw e;
    }
  }

  private static Method findOptionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
    try {
      return findMethod(type, name, parameterTypes);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Method findOptionalMethod(Class<?> type, String... methodNames) {
    for (String methodName : methodNames) {
      try {
        Method method = type.getMethod(methodName);
        method.setAccessible(true);
        return method;
      } catch (NoSuchMethodException ignored) {
      }
    }

    return null;
  }

  private static Class<?> findPropertyMapClass(Class<?> gameProfileClass) {
    for (Constructor<?> constructor : gameProfileClass.getConstructors()) {
      Class<?>[] params = constructor.getParameterTypes();
      if (params.length == 3 && params[0] == UUID.class && params[1] == String.class) {
        return params[2];
      }
    }

    return null;
  }

  private static Object resolveDefaultGameType(Class<?> gameTypeClass) throws ReflectiveOperationException {
    try {
      Field defaultMode = gameTypeClass.getField("DEFAULT_MODE");
      Object value = defaultMode.get(null);
      if (value != null) {
        return value;
      }
    } catch (NoSuchFieldException ignored) {
    }

    if (gameTypeClass.isEnum()) {
      try {
        return Enum.valueOf((Class<Enum>) gameTypeClass, "SURVIVAL");
      } catch (IllegalArgumentException ignored) {
      }

      Object[] values = gameTypeClass.getEnumConstants();
      if (values != null && values.length > 0) {
        return values[0];
      }
    }

    throw new ReflectiveOperationException("No default game mode could be resolved.");
  }

  private static Throwable unwrapRootCause(Throwable throwable) {
    Throwable cursor = throwable;
    while (true) {
      if (cursor instanceof InvocationTargetException invocation && invocation.getCause() != null) {
        cursor = invocation.getCause();
        continue;
      }

      Throwable cause = cursor.getCause();
      if (cause == null || cause == cursor) {
        return cursor;
      }

      cursor = cause;
    }
  }

  public VirtualPlayer create(Player owner, Location location, int skinLayerMask) {
    if (!supported || location.getWorld() == null) {
      return null;
    }

    try {
      Object ownerHandle = craftPlayerGetHandle.invoke(owner);
      Object ownerProfile = playerGetGameProfile.invoke(ownerHandle);
      UUID profileId = UUID.randomUUID();
      Object profile = createProfile(profileId, owner.getName(), ownerProfile);

      Object minecraftServer = craftServerGetServer.invoke(Bukkit.getServer());
      Object worldHandle = craftWorldGetHandle.invoke(location.getWorld());
      Object clientInfo = clientInformationCreateDefault.invoke(null);
      Object nmsDecoy = serverPlayerConstructor.newInstance(minecraftServer, worldHandle, profile, clientInfo);

      entitySetPos.invoke(nmsDecoy, location.getX(), location.getY(), location.getZ());
      entitySetRot.invoke(nmsDecoy, location.getYaw(), location.getPitch());
      livingSetYHeadRot.invoke(nmsDecoy, location.getYaw());
      livingSetYBodyRot.invoke(nmsDecoy, location.getYaw());
      applySkinLayers(nmsDecoy, skinLayerMask);

      int entityId = (int) entityGetId.invoke(nmsDecoy);
      Object playerInfoPacket = createPlayerInfoAddPacket(nmsDecoy, profileId, profile);
      Object addEntityPacket = createAddEntityPacket(nmsDecoy, entityId, profileId, location);
      Object metadataPacket = createMetadataPacket(nmsDecoy, entityId);
      Object equipmentPacket = createEquipmentPacket(entityId, owner, false, true);

      return new NativePlayer(profileId, entityId, nmsDecoy,
          Arrays.asList(playerInfoPacket, addEntityPacket, metadataPacket, equipmentPacket));
    } catch (Throwable e) {
      Throwable root = unwrapRootCause(e);
      report("create virtual player", root);
      return null;
    }
  }

  private Object createProfile(UUID id, String ownerName, Object ownerProfile) throws ReflectiveOperationException {
    String profileName = ownerName;
    if (profileName.length() > 16) {
      profileName = profileName.substring(0, 16);
    }

    if (gameProfileWithPropertiesConstructor != null && gameProfilePropertiesAccessor != null && ownerProfile != null) {
      Object properties = gameProfilePropertiesAccessor.invoke(ownerProfile);
      if (properties != null) {
        return gameProfileWithPropertiesConstructor.newInstance(id, profileName, properties);
      }
    }

    return gameProfileBasicConstructor.newInstance(id, profileName);
  }

  private Object createPlayerInfoAddPacket(Object nmsDecoy, UUID profileId, Object profile) throws ReflectiveOperationException {
    ReflectiveOperationException last = null;

    if (playerInfoFromEntriesConstructor != null && playerInfoEntryExplicitConstructor != null && playerInfoActionClass != null && defaultGameType != null) {
      try {
        Enum addAction = Enum.valueOf((Class<Enum>) playerInfoActionClass, "ADD_PLAYER");
        EnumSet actions = buildInitializationActions(addAction);
        Object entry = playerInfoEntryExplicitConstructor.newInstance(profileId, profile, true, 0, defaultGameType, null, true, 0, null);
        return playerInfoFromEntriesConstructor.newInstance(actions, List.of(entry));
      } catch (ReflectiveOperationException e) {
        last = e;
      }
    }

    if (playerInfoCreateSingleInitializing != null) {
      try {
        return playerInfoCreateSingleInitializing.invoke(null, nmsDecoy, true);
      } catch (ReflectiveOperationException e) {
        last = e;
      }
    }

    if (playerInfoActionConstructor != null && playerInfoActionClass != null) {
      try {
        Object addAction = Enum.valueOf((Class<Enum>) playerInfoActionClass, "ADD_PLAYER");
        return playerInfoActionConstructor.newInstance(addAction, nmsDecoy);
      } catch (ReflectiveOperationException e) {
        last = e;
      }
    }

    if (last != null) {
      throw last;
    }

    throw new ReflectiveOperationException("No supported player-info add packet constructor found.");
  }

  private Object createAddEntityPacket(Object nmsDecoy, int entityId, UUID profileId, Location location) throws ReflectiveOperationException {
    if (addEntityExplicitConstructor != null && entityGetType != null && vec3Zero != null) {
      Object entityType = entityGetType.invoke(nmsDecoy);
      Object velocity = vec3Zero.get(null);
      return addEntityExplicitConstructor.newInstance(
          entityId,
          profileId,
          location.getX(),
          location.getY(),
          location.getZ(),
          location.getPitch(),
          location.getYaw(),
          entityType,
          0,
          velocity,
          (double) location.getYaw()
      );
    }

    return addEntityConstructor.newInstance(nmsDecoy, 0, blockPosZero.get(null));
  }

  private void applySkinLayers(Object nmsDecoy, int mask) throws ReflectiveOperationException {
    if (avatarModelCustomizationAccessor == null || synchedEntityDataSet == null) {
      return;
    }

    Object accessor = avatarModelCustomizationAccessor.get(null);
    Object synchedData = entityGetEntityData.invoke(nmsDecoy);
    synchedEntityDataSet.invoke(synchedData, accessor, (byte) (mask & 0xFF));
  }

  private Object createEquipmentPacket(int entityId, Player owner, boolean hide, boolean includeEmptySlots) throws ReflectiveOperationException {
    if (setEquipmentConstructor == null || pairOfMethod == null || craftItemStackAsNmsCopy == null || equipmentSlotClass == null) {
      return null;
    }

    List<Object> slots = new ArrayList<>();
    ItemStack air = new ItemStack(Material.AIR);
    appendEquipment(slots, "HEAD", hide ? air : owner.getInventory().getHelmet(), includeEmptySlots);
    appendEquipment(slots, "CHEST", hide ? air : owner.getInventory().getChestplate(), includeEmptySlots);
    appendEquipment(slots, "LEGS", hide ? air : owner.getInventory().getLeggings(), includeEmptySlots);
    appendEquipment(slots, "FEET", hide ? air : owner.getInventory().getBoots(), includeEmptySlots);
    appendEquipment(slots, "MAINHAND", hide ? air : owner.getInventory().getItemInMainHand(), includeEmptySlots);
    appendEquipment(slots, "OFFHAND", hide ? air : owner.getInventory().getItemInOffHand(), includeEmptySlots);

    if (slots.isEmpty()) {
      return null;
    }

    return setEquipmentConstructor.newInstance(entityId, slots);
  }

  private void appendEquipment(List<Object> slots, String slotName, ItemStack stack, boolean includeEmptySlots) throws ReflectiveOperationException {
    ItemStack resolved = stack == null ? new ItemStack(Material.AIR) : stack;
    if (!includeEmptySlots && resolved.getType().isAir()) {
      return;
    }

    Object slot = Enum.valueOf((Class<Enum>) equipmentSlotClass, slotName);
    Object nmsStack = craftItemStackAsNmsCopy.invoke(null, resolved.clone());
    Object pair = pairOfMethod.invoke(null, slot, nmsStack);
    slots.add(pair);
  }

  private Object createMetadataPacket(Object nmsDecoy, int entityId) throws ReflectiveOperationException {
    Object synchedData = entityGetEntityData.invoke(nmsDecoy);
    Object packed = synchedEntityDataPackAll != null
        ? synchedEntityDataPackAll.invoke(synchedData)
        : synchedEntityDataGetNonDefaultValues.invoke(synchedData);
    if (!(packed instanceof List<?> values) || values.isEmpty()) {
      return null;
    }

    return setEntityDataConstructor.newInstance(entityId, values);
  }

  public Object createPlayerInfoRemovePacket(UUID profileId) {
    if (!supported) {
      return null;
    }

    try {
      return playerInfoRemoveConstructor.newInstance(List.of(profileId));
    } catch (Throwable e) {
      report("construct virtual player packet", e);
      return null;
    }
  }

  public Object createRemoveEntityPacket(int entityId) {
    if (!supported) {
      return null;
    }

    try {
      return removeEntitiesConstructor.newInstance((Object) new int[]{entityId});
    } catch (Throwable e) {
      report("construct virtual player packet", e);
      return null;
    }
  }

  public boolean applyLook(NativePlayer decoy, float yaw, float pitch, Player viewer) {
    if (!supported) {
      return false;
    }

    try {
      entitySetRot.invoke(decoy.nmsEntity, yaw, pitch);
      livingSetYHeadRot.invoke(decoy.nmsEntity, yaw);
      livingSetYBodyRot.invoke(decoy.nmsEntity, yaw);

      byte yawByte = toAngle(yaw);
      byte pitchByte = toAngle(pitch);
      Object rotatePacket = moveEntityRotConstructor.newInstance(decoy.entityId, yawByte, pitchByte, true);
      Object headPacket = rotateHeadConstructor.newInstance(decoy.nmsEntity, yawByte);

      sendPacket(viewer, rotatePacket);
      sendPacket(viewer, headPacket);

      return true;
    } catch (Throwable e) {
      report("update virtual player", e);
      return false;
    }
  }

  public boolean syncPosition(NativePlayer decoy, Location location, boolean onGround, List<Player> viewers) {
    if (!supported || entityPositionSyncOf == null) {
      return false;
    }

    try {
      entitySetPos.invoke(decoy.nmsEntity, location.getX(), location.getY(), location.getZ());
      entitySetRot.invoke(decoy.nmsEntity, location.getYaw(), location.getPitch());
      if (entitySetOnGround != null) {
        entitySetOnGround.invoke(decoy.nmsEntity, onGround);
      }

      Object packet = entityPositionSyncOf.invoke(null, decoy.nmsEntity);
      if (packet == null) {
        return false;
      }

      for (Player viewer : viewers) {
        sendPacket(viewer, packet);
      }

      return true;
    } catch (Throwable e) {
      report("update virtual player", e);
      return false;
    }
  }

  public void sendHurtAnimation(NativePlayer decoy, float yaw, List<Player> viewers) {
    if (!supported || hurtAnimationConstructor == null) {
      return;
    }

    try {
      Object packet = hurtAnimationConstructor.newInstance(decoy.entityId, yaw);
      for (Player viewer : viewers) {
        sendPacket(viewer, packet);
      }
    } catch (Throwable error) {
      report("send virtual player packet", error);
    }
  }

  @Override
  public ClientPacket equipment(Player owner, boolean hidden) {
    if (!supported || owner == null || !owner.isOnline()) {
      return null;
    }
    try {
      Object packet = createEquipmentPacket(owner.getEntityId(), owner, hidden, true);
      return packet == null ? null : viewer -> sendPacket(viewer, packet);
    } catch (ReflectiveOperationException error) {
      report("create equipment packet", error);
      return null;
    }
  }

  public void sendPacket(Player viewer, Object packet) {
    if (!supported || packet == null || viewer == null || !viewer.isOnline()) {
      return;
    }

    viewerExecutor.accept(viewer, () -> sendPacketOwned(viewer, packet));
  }

  private void sendPacketOwned(Player viewer, Object packet) {
    if (!viewer.isOnline()) {
      return;
    }

    try {
      Object handle = craftPlayerGetHandle.invoke(viewer);
      Object connection = serverPlayerConnectionField.get(handle);
      if (connection == null) {
        return;
      }

      connectionSendPacket.invoke(connection, packet);
    } catch (Throwable error) {
      report("send virtual player packet", error);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private EnumSet buildInitializationActions(Enum addAction) {
    EnumSet actions = EnumSet.noneOf((Class<Enum>) playerInfoActionClass);
    actions.add(addAction);

    String[] optionalActions = new String[]{
        "INITIALIZE_CHAT",
        "UPDATE_GAME_MODE",
        "UPDATE_LISTED",
        "UPDATE_LATENCY",
        "UPDATE_DISPLAY_NAME",
        "UPDATE_HAT",
        "UPDATE_LIST_ORDER"
    };

    for (String name : optionalActions) {
      try {
        actions.add(Enum.valueOf((Class<Enum>) playerInfoActionClass, name));
      } catch (IllegalArgumentException ignored) {
      }
    }

    return actions;
  }
  private void report(String operation, Throwable failure) {
    if (reportedFailures.add(operation)) {
      plugin.getLogger().log(Level.WARNING, "Unable to " + operation, failure);
    }
  }

  private final class NativePlayer implements VirtualPlayer {
    private final UUID profileId;
    private final int entityId;
    private final Object nmsEntity;
    private final List<Object> spawnPackets;
    private final Object removePlayerInfoPacket;
    private final Object removeEntityPacket;

    private NativePlayer(UUID profileId, int entityId, Object nmsEntity, List<Object> spawnPackets) {
      this.profileId = profileId;
      this.entityId = entityId;
      this.nmsEntity = nmsEntity;
      this.spawnPackets = spawnPackets;
      this.removePlayerInfoPacket = createPlayerInfoRemovePacket(profileId);
      this.removeEntityPacket = createRemoveEntityPacket(entityId);
    }

    @Override
    public void spawn(Player viewer) {
      for (Object packet : spawnPackets) {
        sendPacket(viewer, packet);
      }
    }

    @Override
    public void removeFromTab(Player viewer) {
      sendPacket(viewer, removePlayerInfoPacket);
    }

    @Override
    public void destroy(Player viewer) {
      sendPacket(viewer, removeEntityPacket);
      removeFromTab(viewer);
    }

    @Override
    public boolean look(float yaw, float pitch, Player viewer) {
      return applyLook(this, yaw, pitch, viewer);
    }

    @Override
    public boolean move(Location location, boolean onGround, List<Player> viewers) {
      return syncPosition(this, location, onGround, viewers);
    }

    @Override
    public void hurt(float yaw, List<Player> viewers) {
      sendHurtAnimation(this, yaw, viewers);
    }
  }
}
