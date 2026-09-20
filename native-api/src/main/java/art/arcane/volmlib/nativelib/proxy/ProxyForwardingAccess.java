package art.arcane.volmlib.nativelib.proxy;

import art.arcane.volmlib.nativelib.NativeBinding;

@NativeBinding("proxy.NativeProxyForwardingAccess")
public interface ProxyForwardingAccess {
    byte[] velocityKey() throws ReflectiveOperationException;
}
