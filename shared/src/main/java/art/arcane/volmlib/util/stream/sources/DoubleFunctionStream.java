package art.arcane.volmlib.util.stream.sources;

import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.stream.BasicStream;

/**
 * A 2D double source whose primitive reads never box; {@link #get} boxes only for object consumers.
 * The 3D form samples the same column, as the boxed 2D function streams always did.
 */
public class DoubleFunctionStream extends BasicStream<Double> {
    private final NoiseProvider function;

    public DoubleFunctionStream(NoiseProvider function) {
        super();
        this.function = function;
    }

    @Override
    public double toDouble(Double t) {
        return t;
    }

    @Override
    public Double fromDouble(double d) {
        return d;
    }

    @Override
    public Double get(double x, double z) {
        return function.noise(x, z);
    }

    @Override
    public Double get(double x, double y, double z) {
        return function.noise(x, z);
    }

    @Override
    public double getDouble(double x, double z) {
        return function.noise(x, z);
    }

    @Override
    public double getDouble(double x, double y, double z) {
        return function.noise(x, z);
    }
}
