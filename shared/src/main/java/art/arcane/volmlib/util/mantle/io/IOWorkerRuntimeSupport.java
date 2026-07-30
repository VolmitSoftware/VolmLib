/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.mantle.io;

import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class IOWorkerRuntimeSupport {
    private static final int STAGING_HEAP_LIMIT = 4 * 1024 * 1024;

    private final IOWorkerSupport ioWorkerSupport;
    private final IOWorkerCodecSupport codecSupport;

    public IOWorkerRuntimeSupport(IOWorkerSupport ioWorkerSupport, IOWorkerCodecSupport codecSupport) {
        this.ioWorkerSupport = ioWorkerSupport;
        this.codecSupport = codecSupport == null ? IOWorkerCodecSupport.identity() : codecSupport;
    }

    public <T> T read(String name, PlateReader<T> reader) throws IOException {
        return ioWorkerSupport.withChannel(name, channel -> {
            try (InputStream decoded = codecSupport.decode(channel.read());
                 CountingDataInputStream in = CountingDataInputStream.wrap(new BufferedInputStream(decoded))) {
                return reader.read(name, in);
            }
        });
    }

    public <T> void write(String name, T value, PlateWriter<T> writer) throws IOException {
        ioWorkerSupport.withChannel(name, channel -> {
            try (OutputStream encoded = codecSupport.encode(channel.write());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encoded))) {
                writer.write(value, out);
                out.flush();
            }
        });
    }

    /**
     * Encodes the plate fully before touching the target channel, so a failing writer cannot leave
     * a half-written plate on disk. Staging happens in memory up to {@link #STAGING_HEAP_LIMIT};
     * only oversized plates spill to a temp file, so the common case is a single IO pass instead of
     * the previous write-temp / read-temp / write-target round trip. The bytes handed to the
     * channel are the same encoded bytes as before.
     */
    public <T> void writeAtomically(String name, String tempPrefix, String tempSuffix, T value, PlateWriter<T> writer) throws IOException {
        StagedPlate staged = new StagedPlate(tempPrefix, tempSuffix);
        try {
            try (OutputStream encoded = codecSupport.encode(staged);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encoded))) {
                writer.write(value, out);
                out.flush();
            }

            ioWorkerSupport.withChannel(name, channel -> {
                try (OutputStream out = channel.write()) {
                    staged.transferTo(out);
                    out.flush();
                }
            });
        } finally {
            staged.discard();
        }
    }

    public void dumpDecoded(String name, Path target) throws IOException {
        ioWorkerSupport.withChannel(name, channel -> {
            try (InputStream decoded = codecSupport.decode(channel.read())) {
                Files.copy(decoded, target, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    /**
     * Staging sink for an encoded plate. Buffers on the heap and spills to a temp file once the
     * plate grows past {@link #STAGING_HEAP_LIMIT}. {@link #close()} is called by the codec chain
     * to finish the encoding, so it must not release the staged payload; use {@link #discard()}.
     */
    private final class StagedPlate extends OutputStream {
        private final String tempPrefix;
        private final String tempSuffix;
        private ByteArrayOutputStream heap = new ByteArrayOutputStream(8192);
        private File spillFile;
        private OutputStream spill;

        private StagedPlate(String tempPrefix, String tempSuffix) {
            this.tempPrefix = tempPrefix;
            this.tempSuffix = tempSuffix;
        }

        @Override
        public void write(int b) throws IOException {
            spillIfNeeded(1);
            if (spill != null) {
                spill.write(b);
            } else {
                heap.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            spillIfNeeded(len);
            if (spill != null) {
                spill.write(b, off, len);
            } else {
                heap.write(b, off, len);
            }
        }

        @Override
        public void close() throws IOException {
            if (spill != null) {
                spill.flush();
                spill.close();
                spill = null;
            }
        }

        private void spillIfNeeded(int incoming) throws IOException {
            if (spill != null || heap == null || heap.size() + incoming <= STAGING_HEAP_LIMIT) {
                return;
            }

            spillFile = ioWorkerSupport.createTempFile(tempPrefix, tempSuffix);
            spill = new BufferedOutputStream(new FileOutputStream(spillFile));
            heap.writeTo(spill);
            heap = null;
        }

        private void transferTo(OutputStream target) throws IOException {
            close();
            if (spillFile != null) {
                Files.copy(spillFile.toPath(), target);
            } else {
                heap.writeTo(target);
            }
        }

        private void discard() throws IOException {
            close();
            heap = null;
            if (spillFile != null && !spillFile.delete()) {
                spillFile.deleteOnExit();
            }
            spillFile = null;
        }
    }

    @FunctionalInterface
    public interface PlateReader<T> {
        T read(String name, CountingDataInputStream input) throws IOException;
    }

    @FunctionalInterface
    public interface PlateWriter<T> {
        void write(T value, DataOutputStream output) throws IOException;
    }
}
