package art.arcane.volmlib.util.editor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * The test JVM's name resolver (registered in META-INF/services): records every name looked up, refuses names under
 * {@code .invalid} itself and hands the rest to the built-in resolver.
 */
public final class RecordingResolver extends InetAddressResolverProvider {
    static final List<String> LOOKUPS = new CopyOnWriteArrayList<>();

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                LOOKUPS.add(host);
                if (host.endsWith(".invalid")) throw new UnknownHostException(host);
                return builtin.lookupByName(host, policy);
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException { return builtin.lookupByAddress(address); }
        };
    }

    @Override
    public String name() { return "editor-link-test-recorder"; }
}
