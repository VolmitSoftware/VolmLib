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

    public <T> void writeAtomically(String name, String tempPrefix, String tempSuffix, T value, PlateWriter<T> writer) throws IOException {
        ioWorkerSupport.withChannel(name, channel -> {
            File file = ioWorkerSupport.createTempFile(tempPrefix, tempSuffix);
            try {
                try (OutputStream raw = new FileOutputStream(file);
                     OutputStream encoded = codecSupport.encode(raw);
                     DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encoded))) {
                    writer.write(value, out);
                    out.flush();
                }

                try (OutputStream out = channel.write()) {
                    Files.copy(file.toPath(), out);
                    out.flush();
                }
            } finally {
                if (!file.delete()) {
                    file.deleteOnExit();
                }
            }
        });
    }

    public void dumpDecoded(String name, Path target) throws IOException {
        ioWorkerSupport.withChannel(name, channel -> {
            try (InputStream decoded = codecSupport.decode(channel.read())) {
                Files.copy(decoded, target, StandardCopyOption.REPLACE_EXISTING);
            }
        });
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
