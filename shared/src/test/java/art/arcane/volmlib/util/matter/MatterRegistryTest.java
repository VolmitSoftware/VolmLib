package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.matter.slices.IntMatter;
import art.arcane.volmlib.util.matter.slices.RawMatter;
import art.arcane.volmlib.util.matter.slices.StringMatter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class MatterRegistryTest {
    private static final String VALUE_ID = "matter:test-value";

    @BeforeClass
    public static void registerTypes() {
        IrisMatter.registerSliceType(VALUE_ID, new ValueMatter(1, 1, 1));
    }

    @Test
    public void customIdentifierRoundTripsThroughTheStoredHeader() throws IOException {
        IrisMatter original = new IrisMatter(2, 3, 4);
        original.<Value>slice(Value.class).set(1, 2, 3, new Value(73));
        original.getHeader().setAuthor("registry-test");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.write(bytes);

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(2, input.readInt());
        assertEquals(3, input.readInt());
        assertEquals(4, input.readInt());
        assertEquals(1, input.readUnsignedByte());
        new MatterHeader().read(input);
        input.readInt();
        assertEquals(VALUE_ID, input.readUTF());

        Matter restored = Matter.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("registry-test", restored.getHeader().getAuthor());
        assertEquals(1, restored.getSliceTypes().size());
        assertEquals(new Value(73), restored.<Value>getSlice(Value.class).get(1, 2, 3));
        assertNull(restored.<Value>getSlice(Value.class).get(0, 0, 0));
        assertNull(IrisMatter.getSliceType(Value.class.getCanonicalName()));
    }

    @Test
    public void builtInSliceIdentifiersRemainStable() throws IOException {
        assertEquals("java.lang.Boolean", IrisMatter.getSliceId(Boolean.class));
        assertEquals("java.lang.Integer", IrisMatter.getSliceId(Integer.class));
        assertEquals("java.lang.Long", IrisMatter.getSliceId(Long.class));
        assertEquals("java.lang.String", IrisMatter.getSliceId(String.class));
        assertEquals("art.arcane.volmlib.util.matter.MatterCavern", IrisMatter.getSliceId(MatterCavern.class));
        assertEquals("art.arcane.volmlib.util.matter.MatterBiomeInject", IrisMatter.getSliceId(MatterBiomeInject.class));
        assertEquals("art.arcane.volmlib.util.matter.MatterMarker", IrisMatter.getSliceId(MatterMarker.class));
        assertEquals("art.arcane.volmlib.util.matter.MatterStructurePOI", IrisMatter.getSliceId(MatterStructurePOI.class));
        assertEquals("art.arcane.volmlib.util.matter.MatterUpdate", IrisMatter.getSliceId(MatterUpdate.class));
        assertSame(Integer.class, IrisMatter.getSliceType("java.lang.Integer"));

        IntMatter integers = new IntMatter(1, 1, 1);
        integers.set(0, 0, 0, 42);
        StringMatter strings = new StringMatter(1, 1, 1);
        strings.set(0, 0, 0, "stable");
        assertEquals("java.lang.Integer", new DataInputStream(new ByteArrayInputStream(sliceBytes(integers))).readUTF());
        assertEquals("java.lang.String", new DataInputStream(new ByteArrayInputStream(sliceBytes(strings))).readUTF());
    }

    @Test
    public void identicalRegistrationsAreIdempotent() {
        IrisMatter.registerSliceType(VALUE_ID, new ValueMatter(1, 1, 1));
        IrisMatter.registerSliceType(new IntMatter());
        IrisMatter.registerSliceType(new IntMatter());

        assertEquals(VALUE_ID, IrisMatter.getSliceId(Value.class));
        assertSame(Value.class, IrisMatter.getSliceType(VALUE_ID));
        assertSame(ValueMatter.class, new IrisMatter(1, 1, 1).slice(Value.class).getClass());
    }

    @Test
    public void conflictingIdsTypesAndCodecsLeaveTheExistingRegistrationIntact() {
        assertThrows(IllegalArgumentException.class, () -> IrisMatter.registerSliceType(VALUE_ID, new StringMatter()));
        assertThrows(IllegalArgumentException.class, () -> IrisMatter.registerSliceType("matter:other-id", new ValueMatter(1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> IrisMatter.registerSliceType(VALUE_ID, new AlternateValueMatter(1, 1, 1)));

        assertEquals(VALUE_ID, IrisMatter.getSliceId(Value.class));
        assertSame(Value.class, IrisMatter.getSliceType(VALUE_ID));
        assertNull(IrisMatter.getSliceType("matter:other-id"));
        assertSame(ValueMatter.class, new IrisMatter(1, 1, 1).slice(Value.class).getClass());
    }

    @Test
    public void registrationRejectsInvalidIdsAndNullSlices() {
        assertThrows(NullPointerException.class, () -> IrisMatter.registerSliceType(null, new ValueMatter(1, 1, 1)));
        assertThrows(NullPointerException.class, () -> IrisMatter.registerSliceType("matter:null", null));
        assertThrows(NullPointerException.class, () -> IrisMatter.registerSliceType(null));
        for (String id : new String[]{"", " ", "matter:bad id", "matter:\nvalue", "matter:\u0000value", "x".repeat(65_536), "\u0800".repeat(21_846)}) {
            assertThrows(IllegalArgumentException.class, () -> IrisMatter.registerSliceType(id, new ValueMatter(1, 1, 1)));
        }
    }

    @Test
    public void writingAnUnregisteredPayloadFailsBeforeWritingItsHeader() {
        UnregisteredValueMatter slice = new UnregisteredValueMatter(1, 1, 1);
        slice.set(0, 0, 0, new UnregisteredValue(8));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertThrows(IOException.class, () -> slice.write(new DataOutputStream(bytes)));
        assertEquals(0, bytes.size());
    }

    @Test
    public void unknownSliceSkipsOnlyItsAdvertisedBoundary() throws IOException {
        ByteArrayOutputStream unknown = new ByteArrayOutputStream();
        DataOutputStream unknownOutput = new DataOutputStream(unknown);
        unknownOutput.writeUTF("matter:unknown-value");
        unknownOutput.writeLong(99L);
        IntMatter integers = new IntMatter(1, 1, 1);
        integers.set(0, 0, 0, 42);
        byte[] known = sliceBytes(integers);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(1);
        output.writeInt(1);
        output.writeInt(1);
        output.writeByte(2);
        new MatterHeader().write(output);
        output.writeInt(unknown.size());
        unknown.writeTo(output);
        output.writeInt(known.length);
        output.write(known);
        output.writeInt(12345);
        CountingDataInputStream input = CountingDataInputStream.wrap(new ByteArrayInputStream(bytes.toByteArray()));

        Matter restored = Matter.readDin(input);

        assertEquals(1, restored.getSliceTypes().size());
        assertEquals(Integer.valueOf(42), restored.<Integer>getSlice(Integer.class).get(0, 0, 0));
        assertFalse(restored.hasSlice(Value.class));
        assertEquals(12345, input.readInt());
    }

    private static byte[] sliceBytes(MatterSlice<?> slice) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        slice.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private record Value(int number) {
    }

    private record UnregisteredValue(int number) {
    }

    public static class ValueMatter extends RawMatter<Value> {
        public ValueMatter(int width, int height, int depth) {
            super(width, height, depth, Value.class);
        }

        @Override
        public Palette<Value> getGlobalPalette() {
            return null;
        }

        @Override
        public void writeNode(Value value, DataOutputStream output) throws IOException {
            output.writeInt(value.number());
        }

        @Override
        public Value readNode(DataInputStream input) throws IOException {
            return new Value(input.readInt());
        }
    }

    public static final class AlternateValueMatter extends ValueMatter {
        public AlternateValueMatter(int width, int height, int depth) {
            super(width, height, depth);
        }
    }

    public static final class UnregisteredValueMatter extends RawMatter<UnregisteredValue> {
        public UnregisteredValueMatter(int width, int height, int depth) {
            super(width, height, depth, UnregisteredValue.class);
        }

        @Override
        public Palette<UnregisteredValue> getGlobalPalette() {
            return null;
        }

        @Override
        public void writeNode(UnregisteredValue value, DataOutputStream output) throws IOException {
            output.writeInt(value.number());
        }

        @Override
        public UnregisteredValue readNode(DataInputStream input) throws IOException {
            return new UnregisteredValue(input.readInt());
        }
    }
}
