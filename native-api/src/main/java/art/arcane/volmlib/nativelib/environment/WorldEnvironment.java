package art.arcane.volmlib.nativelib.environment;

import java.util.Objects;

public record WorldEnvironment(long gameTime, Sky sky, Fog fog, Lighting lighting, Clouds clouds, Dimension dimension) {
    public WorldEnvironment {
        Objects.requireNonNull(sky, "sky");
        Objects.requireNonNull(fog, "fog");
        Objects.requireNonNull(lighting, "lighting");
        Objects.requireNonNull(clouds, "clouds");
        Objects.requireNonNull(dimension, "dimension");
    }

    public record Color(float red, float green, float blue) {
    }

    public record ColorAlpha(float red, float green, float blue, float alpha) {
    }

    public record Sky(Skybox skybox, float sunAngleDegrees, float moonAngleDegrees, float starAngleDegrees,
                      float starBrightness, ColorAlpha sunrise, Color color, int moonPhase, float rain, float thunder) {
        public Sky {
            Objects.requireNonNull(skybox, "skybox");
            Objects.requireNonNull(sunrise, "sunrise");
            Objects.requireNonNull(color, "color");
        }
    }

    public record Fog(Color color, float start, float end, float skyEnd, float cloudEnd,
                      Color waterColor, float waterStart, float waterEnd) {
        public Fog {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(waterColor, "waterColor");
        }
    }

    public record Lighting(Color blockTint, float skyFactor, Color skyColor, Color ambient) {
        public Lighting {
            Objects.requireNonNull(blockTint, "blockTint");
            Objects.requireNonNull(skyColor, "skyColor");
            Objects.requireNonNull(ambient, "ambient");
        }
    }

    public record Clouds(ColorAlpha color, float height) {
        public Clouds {
            Objects.requireNonNull(color, "color");
        }
    }

    public record Dimension(int minY, int height, boolean hasSkyLight, CardinalLighting cardinalLighting,
                            double horizonHeight, boolean hasEndFlashes) {
        public Dimension {
            Objects.requireNonNull(cardinalLighting, "cardinalLighting");
        }
    }

    public enum Skybox {
        NONE, OVERWORLD, END
    }

    public enum CardinalLighting {
        DEFAULT, NETHER
    }
}
