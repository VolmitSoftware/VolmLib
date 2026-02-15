package art.arcane.volmlib.util.director.visual;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.specialhandlers.NoParameterHandler;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DirectorVisualCommand {
    private final String name;
    private final String description;
    private final KList<String> names;
    private final DirectorVisualCommand parent;
    private final KList<DirectorVisualCommand> nodes;
    private final DirectorVisualNode node;

    private DirectorVisualCommand(
            String name,
            String description,
            KList<String> names,
            DirectorVisualCommand parent,
            KList<DirectorVisualCommand> nodes,
            DirectorVisualNode node
    ) {
        this.name = name;
        this.description = description;
        this.names = names;
        this.parent = parent;
        this.nodes = nodes;
        this.node = node;
    }

    public static DirectorVisualCommand createRoot(DirectorRuntimeEngine engine) {
        if (engine == null || engine.getRoot() == null) {
            throw new IllegalArgumentException("Director engine/root is required");
        }

        return fromRuntime(engine.getRoot(), null, engine.getLegacyHandlers());
    }

    public static Optional<HelpRequest> resolveHelp(DirectorVisualCommand root, List<String> rawArgs) {
        if (root == null) {
            return Optional.empty();
        }

        KList<String> args = rawArgs == null ? new KList<>() : new KList<>(rawArgs);
        DirectorVisualCommand cursor = root;

        while (true) {
            if (cursor.isNode()) {
                return Optional.empty();
            }

            if (args.isEmpty()) {
                return Optional.of(new HelpRequest(cursor, 0));
            }

            if (args.size() == 1 && isHelpToken(args.get(0))) {
                return Optional.of(new HelpRequest(cursor, readHelpPage(args.get(0))));
            }

            String head = args.pop();
            DirectorVisualCommand child = matchChild(cursor, head);
            if (child == null) {
                return Optional.empty();
            }

            cursor = child;
        }
    }

    private static DirectorVisualCommand fromRuntime(
            DirectorRuntimeNode runtime,
            DirectorVisualCommand parent,
            List<DecreeParameterHandler<?>> legacyHandlers
    ) {
        KList<String> names = new KList<>();
        names.add(runtime.getDescriptor().getName());
        names.addAll(runtime.getDescriptor().getAliases());
        names.removeDuplicates();

        DirectorVisualNode node = runtime.isInvocable()
                ? new DirectorVisualNode(buildParameters(runtime.getParameters(), legacyHandlers))
                : null;

        DirectorVisualCommand visual = new DirectorVisualCommand(
                runtime.getDescriptor().getName(),
                runtime.getDescriptor().getDescription(),
                names,
                parent,
                new KList<>(),
                node
        );

        for (DirectorRuntimeNode child : runtime.getChildren()) {
            visual.nodes.add(fromRuntime(child, visual, legacyHandlers));
        }

        return visual;
    }

    private static KList<DirectorVisualParameter> buildParameters(
            List<DirectorRuntimeParameter> runtimeParameters,
            List<DecreeParameterHandler<?>> legacyHandlers
    ) {
        KList<DirectorVisualParameter> required = new KList<>();
        KList<DirectorVisualParameter> optional = new KList<>();

        for (DirectorRuntimeParameter runtimeParameter : runtimeParameters) {
            DirectorVisualParameter parameter = DirectorVisualParameter.from(runtimeParameter, legacyHandlers);
            if (parameter.isRequired()) {
                required.add(parameter);
            } else {
                optional.add(parameter);
            }
        }

        required.addAll(optional);
        return required;
    }

    private static DirectorVisualCommand matchChild(DirectorVisualCommand parent, String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String value = input.trim();
        for (DirectorVisualCommand child : parent.getNodes()) {
            for (String name : child.getNames()) {
                if (name.equalsIgnoreCase(value)) {
                    return child;
                }
            }
        }

        for (DirectorVisualCommand child : parent.getNodes()) {
            for (String name : child.getNames()) {
                String n = name.toLowerCase();
                String v = value.toLowerCase();
                if (n.contains(v) || v.contains(n)) {
                    return child;
                }
            }
        }

        return null;
    }

    private static boolean isHelpToken(String arg) {
        return arg != null && arg.toLowerCase().startsWith("help=");
    }

    private static int readHelpPage(String arg) {
        try {
            int page = Integer.parseInt(arg.substring("help=".length()).trim());
            return Math.max(0, page - 1);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public String getPath() {
        KList<String> n = new KList<>();
        DirectorVisualCommand cursor = this;

        while (cursor.getParent() != null) {
            cursor = cursor.getParent();
            n.add(cursor.getName());
        }

        return "/" + n.reverse().qadd(getName()).toString(" ");
    }

    public String getParentPath() {
        return getParent().getPath();
    }

    public boolean isNode() {
        return node != null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public KList<String> getNames() {
        return names;
    }

    public DirectorVisualCommand getParent() {
        return parent;
    }

    public KList<DirectorVisualCommand> getNodes() {
        return nodes;
    }

    public DirectorVisualNode getNode() {
        return node;
    }

    public record HelpRequest(DirectorVisualCommand command, int page) {
    }

    public static final class DirectorVisualNode {
        private final KList<DirectorVisualParameter> parameters;

        private DirectorVisualNode(KList<DirectorVisualParameter> parameters) {
            this.parameters = parameters;
        }

        public KList<DirectorVisualParameter> getParameters() {
            return parameters;
        }
    }

    public static final class DirectorVisualParameter {
        private final String name;
        private final String description;
        private final Class<?> type;
        private final boolean required;
        private final boolean contextual;
        private final String defaultValue;
        private final KList<String> names;
        private final DecreeParameterHandler<?> handler;
        private final ParamView param;

        private DirectorVisualParameter(
                String name,
                String description,
                Class<?> type,
                boolean required,
                boolean contextual,
                String defaultValue,
                KList<String> names,
                DecreeParameterHandler<?> handler
        ) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.required = required;
            this.contextual = contextual;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.names = names;
            this.handler = handler;
            this.param = new ParamView(this.defaultValue);
        }

        private static DirectorVisualParameter from(
                DirectorRuntimeParameter runtime,
                List<DecreeParameterHandler<?>> legacyHandlers
        ) {
            String name = runtime.getDescriptor().getName();
            KList<String> names = new KList<>();
            names.addAll(runtime.getDescriptor().getAliases());
            names.add(name);
            names.removeDuplicates();

            DecreeParameterHandler<?> handler = resolveHandler(runtime, legacyHandlers);

            return new DirectorVisualParameter(
                    name,
                    runtime.getDescriptor().getDescription(),
                    runtime.getDescriptor().getType(),
                    runtime.getDescriptor().isRequired(),
                    runtime.getDescriptor().isContextual(),
                    runtime.getDescriptor().getDefaultValue(),
                    names,
                    handler
            );
        }

        private static DecreeParameterHandler<?> resolveHandler(
                DirectorRuntimeParameter runtime,
                List<DecreeParameterHandler<?>> legacyHandlers
        ) {
            Class<?> customType = runtime.getAnnotation().customHandler();
            boolean systemHandler = customType == null
                    || customType == NoParameterHandler.class
                    || "DummyHandler".equals(customType.getSimpleName());

            if (!systemHandler) {
                try {
                    Object instance = customType.getConstructor().newInstance();
                    if (instance instanceof DecreeParameterHandler<?> decreeHandler) {
                        return decreeHandler;
                    }
                } catch (Throwable ignored) {
                }
            }

            for (DecreeParameterHandler<?> handler : legacyHandlers == null ? List.<DecreeParameterHandler<?>>of() : legacyHandlers) {
                try {
                    if (handler.supports(runtime.getDescriptor().getType())) {
                        return handler;
                    }
                } catch (Throwable ignored) {
                }
            }

            return null;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Class<?> getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isContextual() {
            return contextual;
        }

        public boolean hasDefault() {
            return !defaultValue.trim().isEmpty();
        }

        public KList<String> getNames() {
            return names;
        }

        public ParamView getParam() {
            return param;
        }

        public String example() {
            if (handler == null) {
                return "NOEXAMPLE";
            }

            KList<?> source;
            try {
                source = handler.getPossibilities();
            } catch (Throwable ignored) {
                source = new KList<>();
            }

            KList<String> options = source == null
                    ? new KList<>()
                    : source.convert(handler::toStringForce);
            if (options.isEmpty()) {
                try {
                    options.add(handler.getRandomDefault());
                } catch (Throwable ignored) {
                    return "NOEXAMPLE";
                }
            }

            return options.getRandom();
        }
    }

    public record ParamView(String defaultValue) {
    }
}
