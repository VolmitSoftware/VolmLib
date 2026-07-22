package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.parse.DirectorParserRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionDispatcher;
import art.arcane.volmlib.util.director.runtime.DirectorInvocationHook;

import java.util.List;
import java.util.Objects;

public final class DirectorEngineOptions {
    private final DirectorParserRegistry parsers;
    private final DirectorContextRegistry contexts;
    private final DirectorExecutionDispatcher dispatcher;
    private final DirectorInvocationHook invocationHook;
    private final List<DirectorParameterHandler<?>> legacyHandlers;
    private final DirectorTextResolver textResolver;

    private DirectorEngineOptions(Builder builder) {
        this.parsers = builder.parsers;
        this.contexts = builder.contexts;
        this.dispatcher = builder.dispatcher;
        this.invocationHook = builder.invocationHook;
        this.legacyHandlers = List.copyOf(builder.legacyHandlers);
        this.textResolver = builder.textResolver;
    }

    public static Builder builder() {
        return new Builder();
    }

    public DirectorParserRegistry getParsers() {
        return parsers;
    }

    public DirectorContextRegistry getContexts() {
        return contexts;
    }

    public DirectorExecutionDispatcher getDispatcher() {
        return dispatcher;
    }

    public DirectorInvocationHook getInvocationHook() {
        return invocationHook;
    }

    public List<DirectorParameterHandler<?>> getLegacyHandlers() {
        return legacyHandlers;
    }

    public DirectorTextResolver getTextResolver() {
        return textResolver;
    }

    public static final class Builder {
        private DirectorParserRegistry parsers = new DirectorParserRegistry();
        private DirectorContextRegistry contexts = new DirectorContextRegistry();
        private DirectorExecutionDispatcher dispatcher = DirectorExecutionDispatcher.IMMEDIATE;
        private DirectorInvocationHook invocationHook = DirectorInvocationHook.NOOP;
        private List<? extends DirectorParameterHandler<?>> legacyHandlers = List.of();
        private DirectorTextResolver textResolver = DirectorTextResolver.ENGLISH;

        private Builder() {
        }

        public Builder parsers(DirectorParserRegistry parsers) {
            this.parsers = Objects.requireNonNull(parsers, "Director parser registry cannot be null");
            return this;
        }

        public Builder contexts(DirectorContextRegistry contexts) {
            this.contexts = Objects.requireNonNull(contexts, "Director context registry cannot be null");
            return this;
        }

        public Builder dispatcher(DirectorExecutionDispatcher dispatcher) {
            this.dispatcher = Objects.requireNonNull(dispatcher, "Director dispatcher cannot be null");
            return this;
        }

        public Builder invocationHook(DirectorInvocationHook invocationHook) {
            this.invocationHook = Objects.requireNonNull(invocationHook, "Director invocation hook cannot be null");
            return this;
        }

        public Builder legacyHandlers(List<? extends DirectorParameterHandler<?>> legacyHandlers) {
            this.legacyHandlers = List.copyOf(Objects.requireNonNull(
                    legacyHandlers,
                    "Director legacy handlers cannot be null"
            ));
            return this;
        }

        public Builder textResolver(DirectorTextResolver textResolver) {
            this.textResolver = Objects.requireNonNull(textResolver, "Director text resolver cannot be null");
            return this;
        }

        public DirectorEngineOptions build() {
            return new DirectorEngineOptions(this);
        }
    }
}
