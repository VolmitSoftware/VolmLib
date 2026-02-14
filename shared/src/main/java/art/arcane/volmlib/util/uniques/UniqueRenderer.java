package art.arcane.volmlib.util.uniques;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.CNGFactory;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UniqueRenderer {
    public static final UniqueRenderer renderer = new UniqueRenderer("shared", 256, 256);

    @Getter
    private final String id;
    @Getter
    private final int width;
    @Getter
    private final int height;
    private final KList<CNGFactory> styles = new KList<>();
    private final KList<InterpolationMethod> interpolators = new KList<>();

    public UniqueRenderer(String id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
        styles.add(CNG::signature);
        interpolators.add(InterpolationMethod.BILINEAR);
        interpolators.add(InterpolationMethod.BICUBIC);
        interpolators.add(InterpolationMethod.HERMITE);
    }

    public List<CNGFactory> getStyles() {
        return styles;
    }

    public List<InterpolationMethod> getInterpolators() {
        return interpolators;
    }

    public void writeCollectionFrames(File folder, int frameStep, int frameCount) {
        if (folder == null) {
            return;
        }

        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }
    }

    public void writeCollectionFrames(File folder) throws IOException {
        writeCollectionFrames(folder, 1, 1);
    }
}
