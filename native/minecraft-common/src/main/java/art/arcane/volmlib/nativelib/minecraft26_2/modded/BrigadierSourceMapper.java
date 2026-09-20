package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BrigadierSourceMapper<S, T> {
    private final CommandDispatcher<T> dispatcher;
    private final SourceMapping<S, T> sources;
    private final Map<CommandNode<S>, CommandNode<T>> mappedNodes = new IdentityHashMap<>();
    private final Map<CommandNode<T>, CommandNode<S>> originalNodes = new IdentityHashMap<>();
    private final Map<Command<T>, Command<S>> originalCommands = new IdentityHashMap<>();
    private final Map<RedirectModifier<T>, RedirectModifier<S>> originalRedirects = new IdentityHashMap<>();

    public BrigadierSourceMapper(CommandDispatcher<T> dispatcher, SourceMapping<S, T> sources) {
        this.dispatcher = dispatcher;
        this.sources = sources;
        originalNodes.put(dispatcher.getRoot(), new RootCommandNode<>());
    }

    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> builder) {
        LiteralCommandNode<S> original = builder.build();
        dispatcher.getRoot().addChild(mapNode(original));
        return original;
    }

    private CommandNode<T> mapNode(CommandNode<S> original) {
        CommandNode<T> existing = mappedNodes.get(original);
        if (existing != null) {
            return existing;
        }
        Command<T> command = original.getCommand() == null ? null
                : context -> original.getCommand().run(mapContext(context));
        if (command != null) {
            originalCommands.put(command, original.getCommand());
        }
        Predicate<T> requirement = source -> original.canUse(sources.toPublic().apply(source));
        CommandNode<T> redirect = original.getRedirect() == null ? null : mapNode(original.getRedirect());
        RedirectModifier<T> modifier = mapRedirect(original.getRedirectModifier());
        CommandNode<T> mapped;
        if (original instanceof LiteralCommandNode<S> literal) {
            mapped = new LiteralCommandNode<>(literal.getLiteral(), command, requirement, redirect,
                    modifier, original.isFork());
        } else if (original instanceof ArgumentCommandNode<S, ?> argument) {
            mapped = mapArgument(argument, command, requirement, redirect, modifier);
        } else if (original instanceof RootCommandNode<S>) {
            mapped = new RootCommandNode<>();
        } else {
            throw new IllegalArgumentException("Unsupported command node " + original.getClass().getName());
        }
        mappedNodes.put(original, mapped);
        originalNodes.put(mapped, original);
        for (CommandNode<S> child : original.getChildren()) {
            mapped.addChild(mapNode(child));
        }
        return mapped;
    }

    private <A> ArgumentCommandNode<T, A> mapArgument(ArgumentCommandNode<S, A> argument,
            Command<T> command, Predicate<T> requirement, CommandNode<T> redirect, RedirectModifier<T> modifier) {
        SuggestionProvider<S> suggestions = argument.getCustomSuggestions();
        return new ArgumentCommandNode<>(argument.getName(), argument.getType(), command, requirement,
                redirect, modifier, argument.isFork(), suggestions == null ? null
                : (context, builder) -> suggestions.getSuggestions(mapContext(context), builder));
    }

    private RedirectModifier<T> mapRedirect(RedirectModifier<S> original) {
        if (original == null) {
            return null;
        }
        RedirectModifier<T> mapped = context -> {
            List<T> redirected = new ArrayList<>();
            for (S source : original.apply(mapContext(context))) {
                redirected.add(sources.toNative().apply(source));
            }
            return redirected;
        };
        originalRedirects.put(mapped, original);
        return mapped;
    }

    private CommandContext<S> mapContext(CommandContext<T> context) {
        Map<String, ParsedArgument<S, ?>> arguments = new HashMap<>();
        List<ParsedCommandNode<S>> nodes = new ArrayList<>(context.getNodes().size());
        for (ParsedCommandNode<T> parsed : context.getNodes()) {
            CommandNode<S> original = originalNodes.get(parsed.getNode());
            if (original == null) {
                throw new IllegalStateException("Command node is outside the registered source mapping");
            }
            nodes.add(new ParsedCommandNode<>(original, parsed.getRange()));
            if (original instanceof ArgumentCommandNode<S, ?>) {
                arguments.put(original.getName(), new ParsedArgument<>(parsed.getRange().getStart(),
                        parsed.getRange().getEnd(), context.getArgument(original.getName(), Object.class)));
            }
        }
        return new CommandContext<>(sources.toPublic().apply(context.getSource()), context.getInput(), arguments,
                originalCommands.get(context.getCommand()), originalNodes.get(context.getRootNode()), nodes,
                context.getRange(), context.getChild() == null ? null : mapContext(context.getChild()),
                originalRedirects.get(context.getRedirectModifier()), context.isForked());
    }

    public record SourceMapping<S, T>(Function<T, S> toPublic, Function<S, T> toNative) {
    }
}
