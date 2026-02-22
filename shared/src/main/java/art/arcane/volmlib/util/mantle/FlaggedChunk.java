package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.parallel.AtomicBooleanArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FlaggedChunk {
    private final AtomicBooleanArray flags = new AtomicBooleanArray(MantleFlag.MAX_ORDINAL + 1);
    private final ReentrantLock[] locks;

    protected FlaggedChunk() {
        this.locks = new ReentrantLock[flags.length()];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public abstract boolean isClosed();

    protected void copyFrom(FlaggedChunk other, Runnable action) {
        lockAll();
        try {
            action.run();
            for (int i = 0; i < flags.length(); i++) {
                flags.set(i, other.flags.get(i));
            }
        } finally {
            unlockAll();
        }
    }

    public boolean isFlagged(MantleFlag flag) {
        return flags.get(flag.ordinal());
    }

    public void flag(MantleFlag flag, boolean value) {
        if (isClosed()) {
            throw new IllegalStateException("Chunk is closed!");
        }
        flags.set(flag.ordinal(), value);
    }

    public void raiseFlagSuspend(MantleFlag flag, Runnable task) {
        if (isClosed()) {
            throw new IllegalStateException("Chunk is closed!");
        }
        if (isFlagged(flag)) {
            return;
        }

        int index = flag.ordinal();
        ReentrantLock lock = locks[index];
        lock.lock();
        try {
            if (isFlagged(flag)) {
                return;
            }

            task.run();
            if (flags.getAndSet(index, true)) {
                throw new IllegalStateException("Flag " + flag.name() + " was already set after task ran!");
            }
        } finally {
            lock.unlock();
        }
    }

    public void raiseFlagUnchecked(MantleFlag flag, Runnable task) {
        if (isClosed()) {
            throw new IllegalStateException("Chunk is closed!");
        }
        int index = flag.ordinal();
        if (flags.compareAndSet(index, false, true)) {
            try {
                task.run();
            } catch (Throwable throwable) {
                flags.set(index, false);
                throw throwable;
            }
        }
    }

    protected void readFlags(int version, DataInput din) throws IOException {
        int length = version < 0 ? 16 : Varint.readUnsignedVarInt(din);

        if (version >= 1) {
            int i = 0;
            while (i < length) {
                byte raw = din.readByte();
                int j = 0;
                while (j < Byte.SIZE && i < flags.length()) {
                    flags.set(i, (raw & (1 << j)) != 0);
                    j++;
                    i++;
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                flags.set(i, din.readBoolean());
            }
        }
    }

    protected void writeFlags(DataOutput dos) throws IOException {
        Varint.writeUnsignedVarInt(flags.length(), dos);
        int count = flags.length();
        int i = 0;
        while (i < count) {
            int packed = 0;
            for (int j = 0; j < Byte.SIZE; j++) {
                if (i >= count) {
                    break;
                }
                if (flags.get(i)) {
                    packed |= 1 << j;
                }
                i++;
            }
            dos.write(packed);
        }
    }

    private void lockAll() {
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
    }

    private void unlockAll() {
        for (int i = locks.length - 1; i >= 0; i--) {
            locks[i].unlock();
        }
    }
}
