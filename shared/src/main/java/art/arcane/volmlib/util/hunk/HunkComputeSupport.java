package art.arcane.volmlib.util.hunk;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer5;
import art.arcane.volmlib.util.function.Consumer6;
import art.arcane.volmlib.util.parallel.BurstExecutorSupport;

import java.util.function.Consumer;
import java.util.function.Function;

public final class HunkComputeSupport {
    private HunkComputeSupport() {
    }

    public static <H> void computeAtomic(int parallelism, Function<Integer, ? extends BurstExecutorSupport> burstFactory, Consumer<Consumer4<Integer, Integer, Integer, H>> sectionProvider, Consumer4<Integer, Integer, Integer, H> task) {
        BurstExecutorSupport e = burstFactory.apply(parallelism);
        sectionProvider.accept((x, y, z, h) -> e.queue(() -> task.accept(x, y, z, h)));
        e.complete();
    }

    public static <H> void computeMerged(int parallelism, Function<Integer, ? extends BurstExecutorSupport> burstFactory, Consumer<Consumer5<Integer, Integer, Integer, H, Runnable>> sectionProvider, Consumer4<Integer, Integer, Integer, H> task) {
        BurstExecutorSupport e = burstFactory.apply(parallelism);
        KList<Runnable> rq = new KList<>(parallelism);

        sectionProvider.accept((x, y, z, h, r) -> e.queue(() -> {
            task.accept(x, y, z, h);

            synchronized (rq) {
                rq.add(r);
            }
        }));

        e.complete();
        rq.forEach(Runnable::run);
    }

    public static <A, B> void computeDualMerged(int parallelism, Function<Integer, ? extends BurstExecutorSupport> burstFactory, Consumer<Consumer6<Integer, Integer, Integer, A, B, Runnable>> sectionProvider, Consumer5<Integer, Integer, Integer, A, B> task) {
        BurstExecutorSupport e = burstFactory.apply(parallelism);
        KList<Runnable> rq = new KList<>(parallelism);

        sectionProvider.accept((x, y, z, a, b, r) -> e.queue(() -> {
            task.accept(x, y, z, a, b);

            synchronized (rq) {
                rq.add(r);
            }
        }));

        e.complete();
        rq.forEach(Runnable::run);
    }
}
