package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeStructureBootstrapPolicy;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

public final class NativeStructureStateLifecycle {
    private static final Runnable NO_OP = () -> {};
    private final ServerLevel runtimeLevel;
    private final ChunkGenerator generator;
    private final NativeStructureBootstrapPolicy policy;
    private final AtomicReference<RetainedState> retainedState = new AtomicReference<>();

    public NativeStructureStateLifecycle(Configuration configuration) {
        this.runtimeLevel = configuration.level();
        this.generator = configuration.generator();
        this.policy = configuration.policy();
    }

    public void retainState(
            ServerLevel level,
            ChunkMap chunkMap,
            ChunkGeneratorStructureState structureState
    ) {
        requireCurrentStructureOwner(level, chunkMap);
        RetainedState retained = new RetainedState(
                level,
                chunkMap,
                Objects.requireNonNull(structureState, "Retained native structure state"));
        if (!retainedState.compareAndSet(null, retained)) {
            throw new IllegalStateException("Retained native structure state is already retained.");
        }
    }

    public RetainedState retainedState(ServerLevel level, ChunkMap chunkMap) {
        RetainedState retained = retainedState.get();
        if (retained == null) {
            return null;
        }
        requireCurrentStructureOwner(level, chunkMap);
        if (retained.level() != level || retained.chunkMap() != chunkMap) {
            throw new IllegalStateException("Retained native structure state belongs to another world runtime.");
        }
        if (level.getChunkSource().getGeneratorState() != retained.structureState()) {
            throw new IllegalStateException("Retained native structure state is no longer current.");
        }
        return retained;
    }

    public void claimRetainedState(RetainedState retained) {
        if (!retainedState.compareAndSet(retained, null)) {
            throw new IllegalStateException("Retained native structure state changed before activation began.");
        }
    }

    public void abandonRetainedState() {
        retainedState.set(null);
    }

    public CompletableFuture<Void> initializeAndPublishStructureState(
            ChunkGeneratorStructureState structureState,
            StructureStatePublisher publisher
    ) {
        return startStructureStateBootstrap(
                structureState,
                NO_OP,
                () -> publishStructureState(publisher));
    }

    public CompletableFuture<Void> activateRetainedState(RetainedState retained) {
        Objects.requireNonNull(retained, "Retained native structure state");
        return startStructureStateBootstrap(
                retained.structureState(),
                () -> {
                    RetainedState current = retainedState(
                            retained.level(), retained.chunkMap());
                    if (current != retained) {
                        throw new IllegalStateException("Retained native structure state changed before activation.");
                    }
                    claimRetainedState(retained);
                },
                NO_OP);
    }

    private CompletableFuture<Void> startStructureStateBootstrap(
            ChunkGeneratorStructureState structureState,
            Runnable claim,
            Runnable activation
    ) {
        AtomicReference<CompletableFuture<Void>> registeredCompletion = new AtomicReference<>();
        CompletableFuture<Void> completion = policy.start(
                claim,
                () -> {
                    CompletableFuture<Void> rings = initializeStructureState(structureState);
                    registeredCompletion.set(rings);
                    return rings;
                },
                () -> {
                    CompletableFuture<Void> rings = Objects.requireNonNull(
                            registeredCompletion.get(),
                            "Registered native structure ring completion");
                    if (rings.isCompletedExceptionally()) {
                        throw new IllegalStateException(
                                "Minecraft native structure ring bootstrap failed before activation.");
                    }
                    activation.run();
                });
        completion.whenComplete((ignored, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                policy.failed(cause);
            }
        });
        return completion;
    }

    private CompletableFuture<Void> initializeStructureState(ChunkGeneratorStructureState structureState) {
        Map<?, ?> ringPositions = structureRingPositions(structureState);
        structureState.ensureStructuresGenerated();
        return structureRingCompletion(ringPositions);
    }

    private Map<?, ?> structureRingPositions(ChunkGeneratorStructureState structureState) {
        try {
            Field field = structureRingPositionsField();
            field.setAccessible(true);
            Object value = field.get(structureState);
            if (!(value instanceof Map<?, ?> ringPositions)) {
                throw new IllegalStateException("Minecraft native structure ring state is unavailable.");
            }
            return ringPositions;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not bind Minecraft native structure ring completions.", e);
        }
    }

    private CompletableFuture<Void> structureRingCompletion(Map<?, ?> ringPositions) {
        List<CompletableFuture<?>> futures = new ArrayList<>(ringPositions.size());
        for (Object candidate : ringPositions.values()) {
            if (candidate instanceof CompletableFuture<?> future) {
                futures.add(future);
            } else {
                throw new IllegalStateException(
                        "Minecraft native structure ring completion is not a future.");
            }
        }
        CompletableFuture<?>[] completions = futures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(completions);
    }

    private Field structureRingPositionsField() {
        List<Field> candidates = new ArrayList<>(1);
        for (Field field : ChunkGeneratorStructureState.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Type genericType = field.getGenericType();
            if (!(genericType instanceof ParameterizedType parameterizedType)) {
                continue;
            }
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length == 2
                    && arguments[1].getTypeName().contains(CompletableFuture.class.getName())) {
                candidates.add(field);
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalStateException("Expected one Minecraft native structure ring-future map, found "
                    + candidates.size() + ".");
        }
        return candidates.getFirst();
    }

    private void requireCurrentStructureOwner(ServerLevel level, ChunkMap chunkMap) {
        if (runtimeLevel != level
                || level.getChunkSource().chunkMap != chunkMap
                || level.getChunkSource().getGenerator() != generator) {
            throw new IllegalStateException("Native structure state no longer belongs to the active world runtime.");
        }
    }

    private void publishStructureState(StructureStatePublisher publisher) {
        try {
            publisher.publish();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not publish Minecraft native structure state.", e);
        }
    }


    public record Configuration(ServerLevel level, ChunkGenerator generator, NativeStructureBootstrapPolicy policy) {
    }

    public record RetainedState(ServerLevel level, ChunkMap chunkMap, ChunkGeneratorStructureState structureState) {
    }

    @FunctionalInterface
    public interface StructureStatePublisher {
        void publish() throws IllegalAccessException;
    }
}
