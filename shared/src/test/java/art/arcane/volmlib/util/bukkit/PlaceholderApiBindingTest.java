package art.arcane.volmlib.util.bukkit;

import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlaceholderApiBindingTest {
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final PluginManager plugins = mock(PluginManager.class);
    private final Plugin host = plugin("Host", true);
    private final Plugin placeholderApi = plugin("PlaceholderAPI", true);
    private final PlaceholderApiBinding binding = new PlaceholderApiBinding(() -> plugins, () -> host, clock::get);

    @Test
    public void fillsWithoutAPlayer() {
        when(plugins.getPlugin("PlaceholderAPI")).thenReturn(placeholderApi);

        assertEquals("Online: 3", binding.fill(null, "Online: %server_online%"));
        assertEquals("nobody", binding.fill(null, "%viewer%"));
    }

    @Test
    public void usesItsOwnLoaderWhenThePluginManagerCannotHandOutPlaceholderApi() {
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);

        assertEquals("Online: 3", binding.fill(null, "Online: %server_online%"));
    }

    @Test
    public void bindsWhenPlaceholderApiEnablesAfterFirstUse() throws Exception {
        assertEquals("Online: %server_online%", binding.fill(null, "Online: %server_online%"));

        when(plugins.getPlugin("PlaceholderAPI")).thenReturn(placeholderApi);
        executor(PluginEnableEvent.class).execute(new Listener() {
        }, event(PluginEnableEvent.class, placeholderApi));

        assertEquals("Online: 3", binding.fill(null, "Online: %server_online%"));
    }

    @Test
    public void dropsPlaceholderApiWhenItDisablesAndDoesNotPollForIt() throws Exception {
        when(plugins.getPlugin("PlaceholderAPI")).thenReturn(placeholderApi);
        assertEquals("Online: 3", binding.fill(null, "Online: %server_online%"));

        executor(PluginDisableEvent.class).execute(new Listener() {
        }, event(PluginDisableEvent.class, placeholderApi));
        for (int second = 0; second < 5; second++) {
            clock.addAndGet(1_000L);
            assertEquals("Online: %server_online%", binding.fill(null, "Online: %server_online%"));
        }

        verify(plugins, times(1)).getPlugin("PlaceholderAPI");
    }

    @Test
    public void looksAgainOnceTheHostIsDisabled() throws Exception {
        when(plugins.getPlugin("PlaceholderAPI")).thenReturn(placeholderApi);
        assertEquals("Online: 3", binding.fill(null, "Online: %server_online%"));

        executor(PluginDisableEvent.class).execute(new Listener() {
        }, event(PluginDisableEvent.class, host));
        assertEquals("Online: 3", binding.fill(null, "Online: %server_online%"));

        verify(plugins, times(2)).getPlugin("PlaceholderAPI");
    }

    @Test
    public void looksAgainAtMostOnceASecondWhileTheHostCannotListen() {
        when(host.isEnabled()).thenReturn(false);
        assertEquals("%server_online%", binding.fill(null, "%server_online%"));

        when(plugins.getPlugin("PlaceholderAPI")).thenReturn(placeholderApi);
        assertEquals("%server_online%", binding.fill(null, "%server_online%"));
        clock.addAndGet(1_000L);
        assertEquals("3", binding.fill(null, "%server_online%"));
    }

    private EventExecutor executor(Class<? extends org.bukkit.event.Event> type) {
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(plugins).registerEvent(eq(type), any(Listener.class), eq(EventPriority.MONITOR), executor.capture(), eq(host));
        return executor.getValue();
    }

    /** A mock, because a real event asks the absent server which thread it is on. */
    private static <T extends PluginEvent> T event(Class<T> type, Plugin plugin) {
        T event = mock(type);
        when(event.getPlugin()).thenReturn(plugin);
        return event;
    }

    private static Plugin plugin(String name, boolean enabled) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(enabled);
        return plugin;
    }
}
