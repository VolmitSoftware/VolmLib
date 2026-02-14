package art.arcane.volmlib.util.uniques.features;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.uniques.UFeature;
import art.arcane.volmlib.util.uniques.UFeatureMeta;
import art.arcane.volmlib.util.uniques.UImage;

import java.util.function.Consumer;

public class UFNOOP implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
    }
}
