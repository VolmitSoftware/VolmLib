package art.arcane.volmlib.util.director.compat;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.parse.DirectorParserRegistry;
import art.arcane.volmlib.util.director.parse.DirectorStandardParsers;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public final class DirectorEngineFactory {
    private DirectorEngineFactory() {
    }

    public static DirectorRuntimeEngine create(Object root) {
        return create(root, DirectorEngineOptions.builder().build());
    }

    public static DirectorRuntimeEngine create(Object root, DirectorEngineOptions options) {
        if (root == null) {
            throw new IllegalArgumentException("Root command object cannot be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("Director engine options cannot be null");
        }

        DirectorParserRegistry parserRegistry = options.getParsers();
        DirectorStandardParsers.registerDefaults(parserRegistry);

        DirectorRuntimeNode rootNode = buildTree(root, null);
        return new DirectorRuntimeEngine(rootNode, options);
    }

    private static DirectorRuntimeNode buildTree(Object instance, DirectorRuntimeNode parent) {
        Class<?> type = instance.getClass();
        DirectorNodeDescriptor groupDescriptor = DirectorAnnotationCompatibility.fromType(type)
                .orElseThrow(() -> new IllegalStateException("Command type is missing @Director annotation: " + type.getName()));

        DirectorRuntimeNode node = new DirectorRuntimeNode(groupDescriptor, parent, instance, null, List.of());

        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers) || Modifier.isVolatile(modifiers)) {
                continue;
            }

            if (!field.getType().isAnnotationPresent(Director.class)) {
                continue;
            }

            Object child = readOrCreateChild(instance, field);
            node.addChild(buildTree(child, node));
        }

        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }

            if (!method.isAnnotationPresent(Director.class)) {
                continue;
            }

            node.addChild(buildMethodNode(instance, method, node));
        }

        return node;
    }

    private static DirectorRuntimeNode buildMethodNode(Object instance, Method method, DirectorRuntimeNode parent) {
        DirectorNodeDescriptor descriptor = DirectorAnnotationCompatibility.fromMethod(method)
                .orElseThrow(() -> new IllegalStateException("Method is missing @Director annotation: " + method.getName()));

        List<DirectorRuntimeParameter> parameters = new ArrayList<>();
        Parameter[] reflected = method.getParameters();
        List<DirectorParameterDescriptor> described = descriptor.getParameters();

        if (reflected.length != described.size()) {
            throw new IllegalStateException("Parameter descriptor mismatch for " + method.getDeclaringClass().getName() + "#" + method.getName());
        }

        for (int i = 0; i < reflected.length; i++) {
            Parameter parameter = reflected[i];
            Param annotation = parameter.getDeclaredAnnotation(Param.class);
            if (annotation == null) {
                throw new IllegalStateException(
                        "Parameter " + parameter.getName() + " in " + method.getDeclaringClass().getName()
                                + "#" + method.getName() + " is missing @Param");
            }

            parameters.add(new DirectorRuntimeParameter(described.get(i), parameter, annotation));
        }

        return new DirectorRuntimeNode(descriptor, parent, instance, method, parameters);
    }

    private static Object readOrCreateChild(Object parent, Field field) {
        try {
            field.setAccessible(true);
            Object value = field.get(parent);
            if (value != null) {
                return value;
            }

            Object created = field.getType().getConstructor().newInstance();
            field.set(parent, created);
            return created;
        } catch (Throwable e) {
            throw new IllegalStateException(
                    "Failed to initialize @Director field " + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }
}
