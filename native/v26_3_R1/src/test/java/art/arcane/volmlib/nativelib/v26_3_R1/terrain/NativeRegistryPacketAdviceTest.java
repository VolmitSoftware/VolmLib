package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.RegistryClientNames;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class NativeRegistryPacketAdviceTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void packetNamesChangeWithoutChangingEntryOrderDataOrServerKeys() throws Exception {
        String registry = Registries.BIOME.identifier().toString();
        RegistryClientNames.register(registry, "iris:biomes/packet_z", "iris:overworld/packet_forest");
        RegistryClientNames.register(registry, "iris:biomes/packet_a", "iris:overworld/packet_forest");
        RegistrySynchronization.PackedRegistryEntry vanilla = entry("minecraft:plains", "vanilla");
        RegistrySynchronization.PackedRegistryEntry second = entry("iris:biomes/packet_z", "second");
        RegistrySynchronization.PackedRegistryEntry first = entry("iris:biomes/packet_a", "first");
        List<RegistrySynchronization.PackedRegistryEntry> original = List.of(vanilla, second, first);
        PluginManager manager = mock(PluginManager.class);
        when(manager.getPlugin("Iris")).thenReturn(mock(Plugin.class));
        ClassReloadingStrategy strategy = instrumentPacket();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);

            ClientboundRegistryDataPacket packet = new ClientboundRegistryDataPacket(Registries.BIOME, original);

            assertEquals(Registries.BIOME, packet.registry());
            assertEquals(List.of("minecraft:plains", "iris_2:overworld/packet_forest", "iris:overworld/packet_forest"),
                    keys(packet.entries()));
            assertSame(vanilla, packet.entries().get(0));
            assertSame(second.data(), packet.entries().get(1).data());
            assertSame(first.data(), packet.entries().get(2).data());
            assertEquals(List.of("minecraft:plains", "iris:biomes/packet_z", "iris:biomes/packet_a"), keys(original));
        } finally {
            strategy.reset(ClientboundRegistryDataPacket.class);
        }
    }

    @Test
    public void absentPluginAndUnrelatedRegistriesLeavePacketsUntouched() throws Exception {
        List<RegistrySynchronization.PackedRegistryEntry> original = List.of(entry("iris:biomes/packet_a", "data"));
        PluginManager manager = mock(PluginManager.class);
        ClassReloadingStrategy strategy = instrumentPacket();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);

            assertSame(original, new ClientboundRegistryDataPacket(Registries.BIOME, original).entries());
            assertSame(original, new ClientboundRegistryDataPacket(Registries.DIMENSION_TYPE, original).entries());
        } finally {
            strategy.reset(ClientboundRegistryDataPacket.class);
        }
    }

    private static ClassReloadingStrategy instrumentPacket() {
        ClassReloadingStrategy strategy = ClassReloadingStrategy.of(ByteBuddyAgent.install());
        new ByteBuddy().redefine(ClientboundRegistryDataPacket.class)
                .visit(Advice.withCustomMapping()
                        .bind(NativeWorldLifecycle.PluginName.class, "Iris")
                        .bind(NativeWorldLifecycle.PolicyClassName.class, RegistryClientNames.class.getName())
                        .to(NativeWorldLifecycle.RegistryPacketAdvice.class)
                        .on(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(2))))
                .make()
                .load(ClientboundRegistryDataPacket.class.getClassLoader(), strategy);
        return strategy;
    }

    private static RegistrySynchronization.PackedRegistryEntry entry(String key, String marker) {
        CompoundTag data = new CompoundTag();
        data.putString("marker", marker);
        return new RegistrySynchronization.PackedRegistryEntry(Identifier.parse(key), Optional.of(data));
    }

    private static List<String> keys(List<RegistrySynchronization.PackedRegistryEntry> entries) {
        return entries.stream().map(entry -> entry.id().toString()).toList();
    }
}
