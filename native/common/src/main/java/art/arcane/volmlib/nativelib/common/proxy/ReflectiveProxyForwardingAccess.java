package art.arcane.volmlib.nativelib.common.proxy;

import art.arcane.volmlib.nativelib.proxy.ProxyForwardingAccess;

import java.nio.charset.StandardCharsets;

public class ReflectiveProxyForwardingAccess implements ProxyForwardingAccess {

    public byte[] velocityKey() throws ReflectiveOperationException {
        Class<?> configurationClass;
        try {
            configurationClass = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
        } catch (ClassNotFoundException exception) {
            return null;
        }
        try {
            Object configuration = configurationClass.getMethod("get").invoke(null);
            Object proxies = configurationClass.getField("proxies").get(configuration);
            Object velocity = proxies.getClass().getField("velocity").get(proxies);
            if (!velocity.getClass().getField("enabled").getBoolean(velocity)) {
                return null;
            }
            String secret = (String) velocity.getClass().getField("secret").get(velocity);
            return secret == null || secret.isEmpty() ? null : secret.getBytes(StandardCharsets.UTF_8);
        } catch (ReflectiveOperationException exception) {
            throw new ReflectiveOperationException("Cannot read Paper's active Velocity forwarding configuration", exception);
        }
    }
}
