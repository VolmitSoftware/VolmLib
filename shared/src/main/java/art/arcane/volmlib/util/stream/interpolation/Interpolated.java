package art.arcane.volmlib.util.stream.interpolation;

public interface Interpolated<T> {
    Interpolated<Double> DOUBLE = new Interpolated<>() {
        @Override
        public double toDouble(Double t) {
            return t == null ? 0D : t;
        }

        @Override
        public Double fromDouble(double d) {
            return d;
        }
    };

    Interpolated<Integer> INTEGER = new Interpolated<>() {
        @Override
        public double toDouble(Integer t) {
            return t == null ? 0D : t.doubleValue();
        }

        @Override
        public Integer fromDouble(double d) {
            return (int) Math.round(d);
        }
    };

    Interpolated<Long> LONG = new Interpolated<>() {
        @Override
        public double toDouble(Long t) {
            return t == null ? 0D : t.doubleValue();
        }

        @Override
        public Long fromDouble(double d) {
            return Math.round(d);
        }
    };

    Interpolated<Float> FLOAT = new Interpolated<>() {
        @Override
        public double toDouble(Float t) {
            return t == null ? 0D : t.doubleValue();
        }

        @Override
        public Float fromDouble(double d) {
            return (float) d;
        }
    };

    Interpolated<Boolean> BOOLEAN = new Interpolated<>() {
        @Override
        public double toDouble(Boolean t) {
            return Boolean.TRUE.equals(t) ? 1D : 0D;
        }

        @Override
        public Boolean fromDouble(double d) {
            return d >= 0.5D;
        }
    };

    Interpolated<Short> SHORT = new Interpolated<>() {
        @Override
        public double toDouble(Short t) {
            return t == null ? 0D : t.doubleValue();
        }

        @Override
        public Short fromDouble(double d) {
            return (short) Math.round(d);
        }
    };

    Interpolated<Byte> BYTE = new Interpolated<>() {
        @Override
        public double toDouble(Byte t) {
            return t == null ? 0D : t.doubleValue();
        }

        @Override
        public Byte fromDouble(double d) {
            return (byte) Math.round(d);
        }
    };

    double toDouble(T t);

    T fromDouble(double d);
}
