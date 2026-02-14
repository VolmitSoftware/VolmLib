package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.math.Position2;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StreamUtilsSupport {
    public static Stream<Position2> streamRadius(int x, int z, int radius) {
        return streamRadius(x, z, radius, radius);
    }

    public static Stream<Position2> streamRadius(int x, int z, int radiusX, int radiusZ) {
        return IntStream.rangeClosed(-radiusX, radiusX)
                .mapToObj(xx -> IntStream.rangeClosed(-radiusZ, radiusZ)
                        .mapToObj(zz -> new Position2(x + xx, z + zz)))
                .flatMap(Function.identity());
    }

    public static <T, M> void forEach(Stream<T> stream, Function<T, Stream<M>> mapper, Consumer<M> consumer, @Nullable BurstProvider burst) {
        forEach(stream.flatMap(mapper), consumer, burst);
    }

    public static <T> void forEach(Stream<T> stream, Consumer<T> task, @Nullable BurstProvider burst) {
        if (burst == null) {
            stream.forEach(task);
            return;
        }

        var list = stream.toList();
        var exec = burst.burst(list.size());
        list.forEach(val -> exec.queue(() -> task.accept(val)));
        exec.complete();
    }

    @FunctionalInterface
    public interface BurstProvider {
        BurstExecutorSupport burst(int estimate);
    }
}
