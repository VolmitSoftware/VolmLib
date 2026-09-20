package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class NativeCommandRegistration {
    public static final Predicate<NativeCommandSource> ALL = source -> true;
    public static final Predicate<NativeCommandSource> GAMEMASTERS = NativeCommandSource::isGameMaster;
    private final BrigadierSourceMapper<NativeCommandSource, CommandSourceStack> mapper;

    public NativeCommandRegistration(CommandDispatcher<CommandSourceStack> dispatcher) {
        mapper = new BrigadierSourceMapper<>(dispatcher,
                new BrigadierSourceMapper.SourceMapping<>(NativeCommandSource::new, NativeCommandSource::source));
    }

    public static LiteralArgumentBuilder<NativeCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<NativeCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public LiteralCommandNode<NativeCommandSource> register(LiteralArgumentBuilder<NativeCommandSource> command) {
        return mapper.register(command);
    }

    public static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(values, builder);
    }

    public static CompletableFuture<Suggestions> suggest(Stream<String> values, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(values, builder);
    }

    public static CompletableFuture<Suggestions> suggest(String[] values, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(values, builder);
    }
}
