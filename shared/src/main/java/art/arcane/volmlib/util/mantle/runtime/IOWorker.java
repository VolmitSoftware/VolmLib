package art.arcane.volmlib.util.mantle.runtime;

import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.io.IOWorkerCodecSupport;
import art.arcane.volmlib.util.mantle.io.IOWorkerRuntimeSupport;
import art.arcane.volmlib.util.mantle.io.IOWorkerSupport;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class IOWorker<P> implements Closeable {
    private final IOWorkerSupport support;
    private final IOWorkerRuntimeSupport runtime;

    public IOWorker(File root) {
        this(root, IOWorkerCodecSupport.identity(), 128, null);
    }

    public IOWorker(File root, IOWorkerCodecSupport codec) {
        this(root, codec, 128, null);
    }

    public IOWorker(File root,
                    IOWorkerCodecSupport codec,
                    int maxCacheSize,
                    IOWorkerSupport.AcquireListener acquireListener) {
        this.support = new IOWorkerSupport(root, maxCacheSize, acquireListener);
        this.runtime = new IOWorkerRuntimeSupport(support, codec);
    }

    public P read(String name, RegionReader<P> reader) throws IOException {
        return runtime.read(name, reader::read);
    }

    public void write(String name,
                      String tempPrefix,
                      String tempSuffix,
                      P region,
                      RegionWriter<P> writer) throws IOException {
        runtime.writeAtomically(name, tempPrefix, tempSuffix, region, writer::write);
    }

    public void dumpDecoded(String name, Path output) throws IOException {
        runtime.dumpDecoded(name, output);
    }

    @Override
    public void close() throws IOException {
        support.close();
    }

    @FunctionalInterface
    public interface RegionReader<P> {
        P read(String regionName, CountingDataInputStream in) throws IOException;
    }

    @FunctionalInterface
    public interface RegionWriter<P> {
        void write(P region, DataOutputStream dos) throws IOException;
    }
}
