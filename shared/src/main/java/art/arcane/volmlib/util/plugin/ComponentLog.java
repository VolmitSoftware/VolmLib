package art.arcane.volmlib.util.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ComponentLog {
    private static final String COMPONENT_CLASS = kyoriClass("adventure.text.Component");
    private static final String COMPONENT_LOGGER_CLASS = kyoriClass("adventure.text.logger.slf4j.ComponentLogger");
    private static final String MINI_MESSAGE_CLASS = kyoriClass("adventure.text.minimessage.MiniMessage");

    private ComponentLog() {
    }

    public static String discriminator(String pluginName, String legacyAccent) {
        String requiredName = Objects.requireNonNull(pluginName, "pluginName");
        String requiredAccent = Objects.requireNonNull(legacyAccent, "legacyAccent");
        return "\u00a78[" + requiredAccent + requiredName + "\u00a78]\u00a7r ";
    }

    public static void logLegacy(
            Plugin plugin,
            Logger fallbackLogger,
            String fallbackPrefix,
            Level level,
            String legacyText,
            Throwable failure) {
        log(plugin, fallbackLogger, fallbackPrefix, level, ComponentText.legacy(legacyText), failure);
    }

    public static void logMarkup(
            Plugin plugin,
            Logger fallbackLogger,
            String fallbackPrefix,
            Level level,
            String trustedMarkup,
            Throwable failure) {
        log(plugin, fallbackLogger, fallbackPrefix, level, ComponentText.markup(trustedMarkup), failure);
    }

    public static void log(
            Plugin plugin,
            Logger fallbackLogger,
            String fallbackPrefix,
            Level level,
            ComponentText message,
            Throwable failure) {
        Logger requiredFallback = Objects.requireNonNull(fallbackLogger, "fallbackLogger");
        ComponentText requiredMessage = Objects.requireNonNull(message, "message");
        Level targetLevel = level == null ? Level.INFO : level;
        ComponentText prefix = ComponentText.legacy(fallbackPrefix);
        ComponentText componentMessage = fallbackPrefix == null || fallbackPrefix.isEmpty()
                ? requiredMessage
                : prefix.append(requiredMessage);
        if (plugin != null && logComponent(plugin, targetLevel, componentMessage.miniMessage(),
                componentMessage.component(), failure)) {
            return;
        }

        Logger pluginLogger = resolvePluginLogger(plugin);
        Logger targetLogger = pluginLogger == null ? requiredFallback : pluginLogger;
        String plainMessage = requiredMessage.plain();
        if (pluginLogger == null && fallbackPrefix != null) {
            plainMessage = prefix.plain() + plainMessage;
        }
        logPlain(targetLogger, targetLevel, plainMessage, failure);
    }

    /**
     * Logs an Adventure component through Paper's component logger, or as plain text through the plugin's logger. It
     * never touches {@link ComponentText} or the legacy and plain serializers, so a shrunk jar can leave them out.
     */
    public static void log(Plugin plugin, Logger fallbackLogger, Level level, Component message, Throwable failure) {
        Logger requiredFallback = Objects.requireNonNull(fallbackLogger, "fallbackLogger");
        Component requiredMessage = Objects.requireNonNull(message, "message");
        Level targetLevel = level == null ? Level.INFO : level;
        if (plugin != null && logComponent(plugin, targetLevel, MiniMessage.miniMessage().serialize(requiredMessage),
                requiredMessage, failure)) {
            return;
        }

        Logger pluginLogger = resolvePluginLogger(plugin);
        StringBuilder plainMessage = new StringBuilder();
        ComponentFlattener.basic().flatten(requiredMessage, plainMessage::append);
        logPlain(pluginLogger == null ? requiredFallback : pluginLogger, targetLevel, plainMessage.toString(), failure);
    }

    private static void logPlain(Logger logger, Level level, String message, Throwable failure) {
        if (failure == null) {
            logger.log(level, message);
        } else {
            logger.log(level, message, failure);
        }
    }

    private static boolean logComponent(
            Plugin plugin,
            Level level,
            String miniMessage,
            Object localComponent,
            Throwable failure) {
        try {
            Plugin.class.getMethod("getComponentLogger");
            ClassLoader classLoader = Plugin.class.getClassLoader();
            Class<?> componentLoggerType = Class.forName(COMPONENT_LOGGER_CLASS, true, classLoader);
            Object componentLogger = componentLoggerType.getMethod("logger", String.class).invoke(null, "");
            if (componentLogger == null) {
                return false;
            }

            Class<?> miniMessageType = Class.forName(MINI_MESSAGE_CLASS, true, classLoader);
            Object parser = miniMessageType.getMethod("miniMessage").invoke(null);
            Object component = miniMessageType.getMethod("deserialize", Object.class)
                    .invoke(parser, miniMessage);
            Class<?> componentType = Class.forName(COMPONENT_CLASS, true, classLoader);
            invokeComponentLogger(componentLoggerType, componentLogger, componentType, component, level, failure);
            return true;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return logLocalComponent(level, localComponent, failure);
        }
    }

    private static boolean logLocalComponent(Level level, Object component, Throwable failure) {
        try {
            Plugin.class.getMethod("getComponentLogger");
            Class<?> componentLoggerType = Class.forName(COMPONENT_LOGGER_CLASS, true, Plugin.class.getClassLoader());
            Object componentLogger = componentLoggerType.getMethod("logger", String.class).invoke(null, "");
            if (componentLogger == null) {
                return false;
            }
            for (Method method : componentLoggerType.getMethods()) {
                if (!method.getName().equals(componentMethod(level))) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (failure == null && parameterTypes.length == 1
                        && parameterTypes[0].isInstance(component)) {
                    method.invoke(componentLogger, component);
                    return true;
                }
                if (failure != null && parameterTypes.length == 2
                        && parameterTypes[0].isInstance(component)
                        && Throwable.class.isAssignableFrom(parameterTypes[1])) {
                    method.invoke(componentLogger, component, failure);
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return false;
        }
    }

    private static void invokeComponentLogger(
            Class<?> componentLoggerType,
            Object componentLogger,
            Class<?> componentType,
            Object component,
            Level level,
            Throwable failure) throws ReflectiveOperationException {
        String methodName = componentMethod(level);
        if (failure == null) {
            componentLoggerType.getMethod(methodName, componentType).invoke(componentLogger, component);
        } else {
            componentLoggerType.getMethod(methodName, componentType, Throwable.class)
                    .invoke(componentLogger, component, failure);
        }
    }

    private static Logger resolvePluginLogger(Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        try {
            return plugin.getLogger();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String componentMethod(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return "error";
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return "warn";
        }
        if (level.intValue() <= Level.FINE.intValue()) {
            return "debug";
        }
        return "info";
    }

    private static String kyoriClass(String suffix) {
        return new String(new char[]{'n', 'e', 't', '.', 'k', 'y', 'o', 'r', 'i', '.'}) + suffix;
    }
}
