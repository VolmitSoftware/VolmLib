package art.arcane.volmlib.util.stream;

import art.arcane.volmlib.util.function.Function2;
import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.stream.arithmetic.FittedStream;
import art.arcane.volmlib.util.stream.convert.RoundingStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.stream.sources.FunctionStream;

@SuppressWarnings("ALL")
public interface ProceduralStream<T> extends ProceduralLayer, Interpolated<T> {
    static <T> ProceduralStream<T> of(Function2<Double, Double, T> f, Interpolated<T> helper) {
        return of(f, (x, y, z) -> f.apply(x, z), helper);
    }

    static <T> ProceduralStream<T> of(Function3<Double, Double, Double, T> f, Interpolated<T> helper) {
        return of((x, z) -> f.apply(x, 0D, z), f, helper);
    }

    static <T> ProceduralStream<T> of(Function2<Double, Double, T> f2, Function3<Double, Double, Double, T> f3, Interpolated<T> helper) {
        return new FunctionStream<>(f2, f3, helper);
    }

    default double getDouble(double x, double z) {
        return toDouble(get(x, z));
    }

    default double getDouble(double x, double y, double z) {
        return toDouble(get(x, y, z));
    }

    default ProceduralStream<T> fit(double min, double max) {
        return new FittedStream<>(this, min, max);
    }

    default ProceduralStream<T> fit(double inMin, double inMax, double min, double max) {
        return new FittedStream<>(this, inMin, inMax, min, max);
    }

    default ProceduralStream<Integer> round() {
        return new RoundingStream(this);
    }

    ProceduralStream<T> getTypedSource();

    ProceduralStream<?> getSource();

    T get(double x, double z);

    T get(double x, double y, double z);
}
