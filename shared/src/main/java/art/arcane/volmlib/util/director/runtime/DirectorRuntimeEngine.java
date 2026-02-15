package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.director.context.DirectorContextMap;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.parse.DirectorConfidence;
import art.arcane.volmlib.util.director.parse.DirectorParser;
import art.arcane.volmlib.util.director.parse.DirectorParserRegistry;
import art.arcane.volmlib.util.director.parse.DirectorTokenizationSupport;
import art.arcane.volmlib.util.director.parse.DirectorValue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DirectorRuntimeEngine implements DirectorCommandEngine {
    private final DirectorRuntimeNode root;
    private final DirectorParserRegistry parsers;
    private final DirectorContextRegistry contexts;
    private final DirectorExecutionDispatcher dispatcher;
    private final List<DecreeParameterHandler<?>> legacyHandlers;
    private final DirectorInvocationHook invocationHook;

    public DirectorRuntimeEngine(
            DirectorRuntimeNode root,
            DirectorParserRegistry parsers,
            DirectorContextRegistry contexts,
            DirectorExecutionDispatcher dispatcher
    ) {
        this(root, parsers, contexts, dispatcher, List.of(), DirectorInvocationHook.NOOP);
    }

    public DirectorRuntimeEngine(
            DirectorRuntimeNode root,
            DirectorParserRegistry parsers,
            DirectorContextRegistry contexts,
            DirectorExecutionDispatcher dispatcher,
            List<? extends DecreeParameterHandler<?>> legacyHandlers,
            DirectorInvocationHook invocationHook
    ) {
        this.root = root;
        this.parsers = parsers;
        this.contexts = contexts;
        this.dispatcher = dispatcher == null ? DirectorExecutionDispatcher.IMMEDIATE : dispatcher;
        this.legacyHandlers = legacyHandlers == null ? List.of() : List.copyOf(legacyHandlers);
        this.invocationHook = invocationHook == null ? DirectorInvocationHook.NOOP : invocationHook;
    }

    public DirectorRuntimeNode getRoot() {
        return root;
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

    public List<DecreeParameterHandler<?>> getLegacyHandlers() {
        return legacyHandlers;
    }

    public DirectorInvocationHook getInvocationHook() {
        return invocationHook;
    }

    @Override
    public DirectorExecutionResult execute(DirectorInvocation invocation) {
        if (invocation == null || root == null) {
            return DirectorExecutionResult.notHandled();
        }

        List<String> args = tokenize(invocation.getArgs(), true);
        Traversal traversal = traverseForExecution(root, args);
        if (traversal.node == null || !traversal.node.isInvocable()) {
            return DirectorExecutionResult.notHandled();
        }

        return invokeNode(invocation, traversal.node, traversal.remainingArgs);
    }

    @Override
    public List<String> tabComplete(DirectorInvocation invocation) {
        if (invocation == null || root == null) {
            return List.of();
        }

        List<String> raw = invocation.getArgs() == null ? List.of() : invocation.getArgs();
        if (raw.isEmpty()) {
            return childNameSuggestions(root, "");
        }

        List<String> tokens = new ArrayList<>(raw);
        String currentInput = tokens.remove(tokens.size() - 1);

        DirectorRuntimeNode cursor = root;
        int consumed = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            DirectorRuntimeNode match = findBestNode(cursor.getChildren(), token, true);
            if (match == null) {
                return List.of();
            }

            cursor = match;
            consumed++;
            if (cursor.isInvocable()) {
                break;
            }
        }

        if (cursor.isInvocable()) {
            List<String> nodeArgs = new ArrayList<>();
            for (int i = consumed; i < tokens.size(); i++) {
                nodeArgs.add(tokens.get(i));
            }
            nodeArgs.add(currentInput);
            return methodSuggestions(cursor, nodeArgs);
        }

        return childNameSuggestions(cursor, currentInput);
    }

    private DirectorExecutionResult invokeNode(DirectorInvocation invocation, DirectorRuntimeNode node, List<String> rawArgs) {
        DirectorSender sender = invocation.getSender();
        if (!node.getDescriptor().getOrigin().validFor(sender.isPlayer())) {
            sender.sendMessage("This command cannot be run from this origin.");
            return DirectorExecutionResult.failure("Invalid origin");
        }

        DirectorContextMap contextMap = new DirectorContextMap();
        contextMap.put(DirectorSender.class, sender);
        contextMap.put(DirectorInvocation.class, invocation);

        MappingResult mapping = mapArguments(node, rawArgs);
        for (String warning : mapping.warnings) {
            sender.sendMessage(warning);
        }

        Object[] params = new Object[node.getParameters().size()];
        for (int i = 0; i < node.getParameters().size(); i++) {
            DirectorRuntimeParameter parameter = node.getParameters().get(i);
            DirectorParameterDescriptor descriptor = parameter.getDescriptor();
            String raw = mapping.mappedValues.get(parameter);

            Object value = null;
            if (raw != null) {
                ValueResult result = parseValue(parameter, raw);
                if (!result.valid) {
                    String message = "Cannot convert \"" + raw + "\" into " + descriptor.getType().getSimpleName() + " for " + descriptor.getName();
                    sender.sendMessage(message);
                    return DirectorExecutionResult.failure(message);
                }
                value = result.value;
            }

            if (value == null && descriptor.isContextual()) {
                value = contexts.resolve(descriptor.getType(), invocation, contextMap).orElse(null);
            }

            if (value == null && descriptor.getDefaultValue() != null && !descriptor.getDefaultValue().trim().isEmpty()) {
                ValueResult defaultResult = parseValue(parameter, descriptor.getDefaultValue());
                if (!defaultResult.valid) {
                    String message = "Cannot parse default value for parameter " + descriptor.getName();
                    sender.sendMessage(message);
                    return DirectorExecutionResult.failure(message);
                }
                value = defaultResult.value;
            }

            if (descriptor.isRequired() && value == null) {
                String message = "Missing argument \"" + descriptor.getName() + "\" (" + descriptor.getType().getSimpleName() + ")";
                sender.sendMessage(message);
                return DirectorExecutionResult.failure(message);
            }

            params[i] = value;
        }

        Runnable invokeTask = () -> {
            invocationHook.beforeInvoke(invocation, node);
            try {
                invokeReflective(node, params);
            } finally {
                invocationHook.afterInvoke(invocation, node);
            }
        };

        try {
            dispatcher.dispatch(node.getDescriptor().getExecutionMode(), invokeTask);
        } catch (Throwable e) {
            String message = "Failed to execute command " + node.path() + ": " + safeMessage(e);
            sender.sendMessage(message);
            return DirectorExecutionResult.failure(message);
        }

        return DirectorExecutionResult.success();
    }

    private void invokeReflective(DirectorRuntimeNode node, Object[] params) {
        try {
            node.getMethod().setAccessible(true);
            node.getMethod().invoke(node.getInstance(), params);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException() == null ? e : e.getTargetException();
            throw new RuntimeException(safeMessage(cause), cause);
        } catch (Throwable e) {
            throw new RuntimeException(safeMessage(e), e);
        }
    }

    private MappingResult mapArguments(DirectorRuntimeNode node, List<String> args) {
        Map<DirectorRuntimeParameter, String> mapped = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> positional = new ArrayList<>();

        for (String token : args) {
            if (token == null) {
                continue;
            }

            int split = token.indexOf('=');
            if (split >= 0) {
                String key = token.substring(0, split).trim();
                String value = token.substring(split + 1).trim();
                DirectorRuntimeParameter parameter = findBestParameter(node.getParameters(), key, true);

                if (parameter != null) {
                    mapped.putIfAbsent(parameter, value);
                } else {
                    warnings.add("Unknown parameter key: " + key);
                    if (!value.isEmpty()) {
                        positional.add(value);
                    }
                }
            } else {
                positional.add(token);
            }
        }

        int position = 0;
        for (DirectorRuntimeParameter parameter : node.getParameters()) {
            if (mapped.containsKey(parameter)) {
                continue;
            }

            if (parameter.getDescriptor().isContextual()) {
                continue;
            }

            if (position < positional.size()) {
                mapped.put(parameter, positional.get(position++));
            }
        }

        for (int i = position; i < positional.size(); i++) {
            warnings.add("Unknown argument: " + positional.get(i));
        }

        return new MappingResult(mapped, warnings);
    }

    private ValueResult parseValue(DirectorRuntimeParameter parameter, String raw) {
        DirectorParameterDescriptor descriptor = parameter.getDescriptor();
        Class<?> type = descriptor.getType();

        DecreeParameterHandler<?> customHandler = parameter.getCustomHandlerOrNull();
        if (customHandler != null) {
            try {
                return ValueResult.valid(customHandler.parse(raw, false));
            } catch (Throwable ignored) {
                return ValueResult.invalid();
            }
        }

        Optional<? extends DirectorParser<?>> parser = parsers.get(type);
        if (parser.isPresent()) {
            @SuppressWarnings("unchecked")
            DirectorValue<Object> result = (DirectorValue<Object>) parser.get().parse(raw);
            if (result != null && result.getConfidence() != DirectorConfidence.INVALID) {
                return ValueResult.valid(result.getValue());
            }
            return ValueResult.invalid();
        }

        if (type.isEnum()) {
            Object resolved = parseEnum(type, raw);
            return resolved == null ? ValueResult.invalid() : ValueResult.valid(resolved);
        }

        DecreeParameterHandler<?> legacyHandler = resolveLegacyHandler(type);
        if (legacyHandler != null) {
            try {
                return ValueResult.valid(legacyHandler.parse(raw, false));
            } catch (Throwable ignored) {
                return ValueResult.invalid();
            }
        }

        return ValueResult.invalid();
    }

    private Object parseEnum(Class<?> enumType, String input) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return null;
        }

        String in = input == null ? "" : input.trim();
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            if (name.equalsIgnoreCase(in)) {
                return constant;
            }
        }

        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            String loweredName = name.toLowerCase();
            String loweredInput = in.toLowerCase();
            if (loweredName.contains(loweredInput) || loweredInput.contains(loweredName)) {
                return constant;
            }
        }

        int bestDistance = Integer.MAX_VALUE;
        Object best = null;
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            int distance = levenshtein(name.toLowerCase(), in.toLowerCase());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = constant;
            }
        }

        int threshold = Math.max(1, in.length() / 3);
        return bestDistance <= threshold ? best : null;
    }

    private Traversal traverseForExecution(DirectorRuntimeNode start, List<String> args) {
        DirectorRuntimeNode cursor = start;
        List<String> remaining = new ArrayList<>(args);

        while (!remaining.isEmpty()) {
            if (cursor.isInvocable()) {
                break;
            }

            String head = remaining.get(0);
            DirectorRuntimeNode match = findBestNode(cursor.getChildren(), head, true);
            if (match == null) {
                break;
            }

            remaining.remove(0);
            cursor = match;
        }

        return new Traversal(cursor, remaining);
    }

    private List<String> childNameSuggestions(DirectorRuntimeNode node, String partial) {
        Set<String> suggestions = new LinkedHashSet<>();
        String normalized = partial == null ? "" : partial.trim().toLowerCase();

        for (DirectorRuntimeNode child : node.getChildren()) {
            for (String name : child.allNames()) {
                if (normalized.isEmpty()) {
                    suggestions.add(name);
                    continue;
                }

                String lowered = name.toLowerCase();
                if (lowered.startsWith(normalized) || lowered.contains(normalized) || normalized.contains(lowered)) {
                    suggestions.add(name);
                }
            }
        }

        return suggestions.stream().sorted(String::compareToIgnoreCase).toList();
    }

    private List<String> methodSuggestions(DirectorRuntimeNode node, List<String> args) {
        if (node.getParameters().isEmpty()) {
            return List.of();
        }

        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> previous = args.size() <= 1 ? List.of() : args.subList(0, args.size() - 1);

        Set<DirectorRuntimeParameter> consumed = new LinkedHashSet<>();
        for (String token : previous) {
            int split = token.indexOf('=');
            if (split < 0) {
                continue;
            }
            String key = token.substring(0, split);
            DirectorRuntimeParameter parameter = findBestParameter(node.getParameters(), key, true);
            if (parameter != null) {
                consumed.add(parameter);
            }
        }

        Set<String> suggestions = new LinkedHashSet<>();

        int split = last.indexOf('=');
        if (split >= 0) {
            String key = last.substring(0, split).trim();
            String valuePrefix = last.substring(split + 1).trim();
            DirectorRuntimeParameter parameter = findBestParameter(node.getParameters(), key, true);
            if (parameter == null || parameter.getDescriptor().isContextual()) {
                return List.of();
            }

            for (String value : parameterValueSuggestions(parameter, valuePrefix)) {
                suggestions.add(parameter.getDescriptor().getName() + "=" + value);
            }

            if (suggestions.isEmpty()) {
                suggestions.add(parameter.getDescriptor().getName() + "=");
            }

            return suggestions.stream().sorted(String::compareToIgnoreCase).toList();
        }

        for (DirectorRuntimeParameter parameter : node.getParameters()) {
            if (consumed.contains(parameter) || parameter.getDescriptor().isContextual()) {
                continue;
            }

            String suggestion = parameter.getDescriptor().getName() + "=";
            if (last.isEmpty()) {
                suggestions.add(suggestion);
                continue;
            }

            String lowered = suggestion.toLowerCase();
            String loweredLast = last.toLowerCase();
            if (lowered.startsWith(loweredLast) || lowered.contains(loweredLast) || loweredLast.contains(lowered)) {
                suggestions.add(suggestion);
            }
        }

        DirectorRuntimeParameter nextUnconsumed = null;
        for (DirectorRuntimeParameter parameter : node.getParameters()) {
            if (!consumed.contains(parameter) && !parameter.getDescriptor().isContextual()) {
                nextUnconsumed = parameter;
                break;
            }
        }

        if (nextUnconsumed != null) {
            for (String value : parameterValueSuggestions(nextUnconsumed, last)) {
                suggestions.add(value);
            }
        }

        return suggestions.stream().sorted(String::compareToIgnoreCase).toList();
    }

    private List<String> parameterValueSuggestions(DirectorRuntimeParameter parameter, String input) {
        DirectorParameterDescriptor descriptor = parameter.getDescriptor();
        String normalized = input == null ? "" : input.trim().toLowerCase();
        Set<String> suggestions = new LinkedHashSet<>();

        DecreeParameterHandler<?> customHandler = parameter.getCustomHandlerOrNull();
        if (customHandler != null) {
            try {
                for (Object possibility : customHandler.getPossibilities(input)) {
                    String rendered = customHandler.toStringForce(possibility);
                    if (rendered != null && !rendered.trim().isEmpty()) {
                        suggestions.add(rendered);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        Class<?> type = descriptor.getType();
        if (suggestions.isEmpty()) {
            DecreeParameterHandler<?> legacyHandler = resolveLegacyHandler(type);
            if (legacyHandler != null) {
                try {
                    for (Object possibility : legacyHandler.getPossibilities(input)) {
                        String rendered = legacyHandler.toStringForce(possibility);
                        if (rendered != null && !rendered.trim().isEmpty()) {
                            suggestions.add(rendered);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    suggestions.add(((Enum<?>) constant).name().toLowerCase());
                }
            }
        } else if (type == Boolean.class || type == boolean.class) {
            suggestions.add("true");
            suggestions.add("false");
        }

        if (normalized.isEmpty()) {
            return suggestions.stream().sorted(String::compareToIgnoreCase).toList();
        }

        return suggestions.stream()
                .filter(s -> {
                    String lowered = s.toLowerCase();
                    return lowered.startsWith(normalized) || lowered.contains(normalized) || normalized.contains(lowered);
                })
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    private DecreeParameterHandler<?> resolveLegacyHandler(Class<?> type) {
        for (DecreeParameterHandler<?> handler : legacyHandlers) {
            try {
                if (handler.supports(type)) {
                    return handler;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private DirectorRuntimeNode findBestNode(List<DirectorRuntimeNode> nodes, String input, boolean fuzzy) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String trimmed = input.trim();
        DirectorRuntimeNode exact = null;
        for (DirectorRuntimeNode node : nodes) {
            for (String name : node.allNames()) {
                if (name.equalsIgnoreCase(trimmed)) {
                    if (exact == null || node.getDescriptor().getName().compareToIgnoreCase(exact.getDescriptor().getName()) < 0) {
                        exact = node;
                    }
                }
            }
        }

        if (exact != null || !fuzzy) {
            return exact;
        }

        return nodes.stream()
                .map(node -> new ScoredNode(node, bestScore(node.allNames(), trimmed)))
                .filter(scored -> scored.score < Integer.MAX_VALUE)
                .sorted(Comparator
                        .comparingInt((ScoredNode scored) -> scored.score)
                        .thenComparing(scored -> scored.node.getDescriptor().getName(), String::compareToIgnoreCase))
                .map(scored -> scored.node)
                .findFirst()
                .orElse(null);
    }

    private DirectorRuntimeParameter findBestParameter(List<DirectorRuntimeParameter> parameters, String input, boolean fuzzy) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String trimmed = input.trim();
        DirectorRuntimeParameter exact = null;
        for (DirectorRuntimeParameter parameter : parameters) {
            if (parameterNameEquals(parameter, trimmed)) {
                if (exact == null || parameter.getDescriptor().getName().compareToIgnoreCase(exact.getDescriptor().getName()) < 0) {
                    exact = parameter;
                }
            }
        }

        if (exact != null || !fuzzy) {
            return exact;
        }

        return parameters.stream()
                .map(parameter -> new ScoredParameter(parameter, bestScore(parameterNames(parameter), trimmed)))
                .filter(scored -> scored.score < Integer.MAX_VALUE)
                .sorted(Comparator
                        .comparingInt((ScoredParameter scored) -> scored.score)
                        .thenComparing(scored -> scored.parameter.getDescriptor().getName(), String::compareToIgnoreCase))
                .map(scored -> scored.parameter)
                .findFirst()
                .orElse(null);
    }

    private boolean parameterNameEquals(DirectorRuntimeParameter parameter, String input) {
        for (String name : parameterNames(parameter)) {
            if (name.equalsIgnoreCase(input)) {
                return true;
            }
        }

        return false;
    }

    private List<String> parameterNames(DirectorRuntimeParameter parameter) {
        List<String> names = new ArrayList<>();
        names.add(parameter.getDescriptor().getName());
        names.addAll(parameter.getDescriptor().getAliases());
        return names;
    }

    private int bestScore(List<String> names, String input) {
        int best = Integer.MAX_VALUE;
        String loweredInput = input.toLowerCase();
        int threshold = Math.max(1, loweredInput.length() / 3);

        for (String candidate : names) {
            String loweredCandidate = candidate.toLowerCase();
            if (loweredCandidate.equals(loweredInput)) {
                return 0;
            }

            if (loweredCandidate.startsWith(loweredInput)) {
                best = Math.min(best, 5 + (loweredCandidate.length() - loweredInput.length()));
                continue;
            }

            if (loweredCandidate.contains(loweredInput) || loweredInput.contains(loweredCandidate)) {
                best = Math.min(best, 10 + Math.abs(loweredCandidate.length() - loweredInput.length()));
                continue;
            }

            int distance = levenshtein(loweredCandidate, loweredInput);
            if (distance <= threshold) {
                best = Math.min(best, 20 + distance);
            }
        }

        return best;
    }

    private int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost
                );
            }
        }

        return d[a.length()][b.length()];
    }

    private List<String> tokenize(List<String> args, boolean trim) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }

        return DirectorTokenizationSupport.tokenize(args.toArray(String[]::new), trim);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private record Traversal(DirectorRuntimeNode node, List<String> remainingArgs) {
    }

    private record MappingResult(Map<DirectorRuntimeParameter, String> mappedValues, List<String> warnings) {
    }

    private record ValueResult(boolean valid, Object value) {
        static ValueResult valid(Object value) {
            return new ValueResult(true, value);
        }

        static ValueResult invalid() {
            return new ValueResult(false, null);
        }
    }

    private record ScoredNode(DirectorRuntimeNode node, int score) {
    }

    private record ScoredParameter(DirectorRuntimeParameter parameter, int score) {
    }
}
