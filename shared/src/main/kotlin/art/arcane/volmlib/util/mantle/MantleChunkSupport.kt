package art.arcane.volmlib.util.mantle

import art.arcane.volmlib.util.io.CountingDataInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

abstract class MantleChunkSupport<M> protected constructor(
    sectionHeight: Int,
    private val x: Int,
    private val z: Int,
) : FlaggedChunk() {
    private val sections = AtomicReferenceArray<M>(sectionHeight)
    private val ref = Semaphore(Int.MAX_VALUE, true)
    private val closed = AtomicBoolean(false)

    protected constructor(version: Int, sectionHeight: Int, din: CountingDataInputStream) : this(
        sectionHeight,
        din.readByte().toInt(),
        din.readByte().toInt(),
    ) {
        val s = din.readByte().toInt()
        readFlags(version, din)

        for (i in 0 until s) {
            onBeforeReadSection(i)

            val size = din.readInt().toLong()
            if (size == 0L) {
                continue
            }

            val start = din.count()
            if (i >= sectionHeight) {
                din.skipTo(start + size)
                continue
            }

            try {
                sections.set(i, readSection(din))
            } catch (e: IOException) {
                val end = start + size
                onReadSectionFailure(i, start, end, din, e)
                din.skipTo(end)
            }

            if (din.count() != start + size) {
                throw IOException("Chunk section read size mismatch!")
            }
        }
    }

    fun getX(): Int = x

    fun getZ(): Int = z

    fun sectionCount(): Int = sections.length()

    fun close() {
        closed.set(true)
        ref.acquireUninterruptibly(Int.MAX_VALUE)
        ref.release(Int.MAX_VALUE)
    }

    fun inUse(): Boolean = ref.availablePermits() < Int.MAX_VALUE

    open fun use(): MantleChunkSupport<M> {
        if (closed.get()) {
            throw IllegalStateException("Chunk is closed!")
        }

        ref.acquireUninterruptibly()
        if (closed.get()) {
            ref.release()
            throw IllegalStateException("Chunk is closed!")
        }

        return this
    }

    fun release() {
        ref.release()
    }

    fun copyFrom(chunk: MantleChunkSupport<M>) {
        use()
        super.copyFrom(chunk, Runnable {
            for (i in 0 until sections.length()) {
                sections.set(i, chunk.get(i))
            }
        })
        release()
    }

    fun exists(section: Int): Boolean = get(section) != null

    fun get(section: Int): M? = sections.get(section)

    fun clear() {
        for (i in 0 until sections.length()) {
            delete(i)
        }
    }

    fun delete(section: Int) {
        sections.set(section, null)
    }

    fun getOrCreate(section: Int): M {
        val sectionData = get(section)
        if (sectionData != null) {
            return sectionData
        }

        val instance = createSection()
        val value = sections.compareAndExchange(section, null, instance)
        return value ?: instance
    }

    @Throws(IOException::class)
    fun write(dos: DataOutputStream) {
        close()
        dos.writeByte(x)
        dos.writeByte(z)
        dos.writeByte(sections.length())
        writeFlags(dos)

        val bytes = ByteArrayOutputStream(8192)
        val sub = DataOutputStream(bytes)
        for (i in 0 until sections.length()) {
            trimIndex(i)
            if (exists(i)) {
                try {
                    writeSection(get(i)!!, sub)
                    dos.writeInt(bytes.size())
                    bytes.writeTo(dos)
                } finally {
                    bytes.reset()
                }
            } else {
                dos.writeInt(0)
            }
        }
    }

    fun trimSections() {
        for (i in 0 until sections.length()) {
            if (exists(i)) {
                trimIndex(i)
            }
        }
    }

    override fun isClosed(): Boolean = closed.get()

    protected fun trimIndex(index: Int) {
        if (!exists(index)) {
            return
        }

        val section = get(index)!!
        if (isSectionEmpty(section)) {
            sections.set(index, null)
            return
        }

        trimSection(section)
        if (isSectionEmpty(section)) {
            sections.set(index, null)
        }
    }

    protected open fun onBeforeReadSection(index: Int) {
    }

    protected open fun onReadSectionFailure(
        index: Int,
        start: Long,
        end: Long,
        din: CountingDataInputStream,
        error: IOException,
    ) {
    }

    protected abstract fun createSection(): M

    @Throws(IOException::class)
    protected abstract fun readSection(din: CountingDataInputStream): M

    @Throws(IOException::class)
    protected abstract fun writeSection(section: M, dos: DataOutputStream)

    protected abstract fun trimSection(section: M)

    protected abstract fun isSectionEmpty(section: M): Boolean
}
