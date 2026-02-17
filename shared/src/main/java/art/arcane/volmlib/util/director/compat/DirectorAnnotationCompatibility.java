package art.arcane.volmlib.util.director.compat;

import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DirectorAnnotationCompatibility {
    private DirectorAnnotationCompatibility() {
    }

    public static Optional<DirectorNodeDescriptor> fromType(Class<?> type) {
        Director director = type.getDeclaredAnnotation(Director.class);
        if (director == null) {
            return Optional.empty();
        }

        String name = director.name().isEmpty() ? type.getSimpleName() : director.name();
        return Optional.of(new DirectorNodeDescriptor(
                name,
                resolveDescription(director.description()),
                readAliases(director.aliases()),
                director.origin(),
                director.sync() ? DirectorExecutionMode.SYNC : DirectorExecutionMode.ASYNC,
                true,
                List.of()
        ));
    }

    public static Optional<DirectorNodeDescriptor> fromMethod(Method method) {
        Director director = method.getDeclaredAnnotation(Director.class);
        if (director == null) {
            return Optional.empty();
        }

        List<DirectorParameterDescriptor> parameters = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            Param param = parameter.getDeclaredAnnotation(Param.class);
            if (param == null) {
                throw new IllegalStateException(
                        "Parameter " + parameter.getName() + " in " + method.getDeclaringClass().getName()
                                + "#" + method.getName() + " is missing @Param");
            }

            String paramName = param.name().isEmpty() ? parameter.getName() : param.name();
            String defaultValue = param.defaultValue().trim();
            parameters.add(new DirectorParameterDescriptor(
                    paramName,
                    resolveDescription(param.description()),
                    parameter.getType(),
                    defaultValue.isEmpty(),
                    param.contextual(),
                    defaultValue,
                    readAliases(param.aliases())
            ));
        }

        String name = director.name().isEmpty() ? method.getName() : director.name();
        DirectorOrigin origin = director.origin();

        return Optional.of(new DirectorNodeDescriptor(
                name,
                resolveDescription(director.description()),
                readAliases(director.aliases()),
                origin,
                director.sync() ? DirectorExecutionMode.SYNC : DirectorExecutionMode.ASYNC,
                false,
                parameters
        ));
    }

    private static List<String> readAliases(String[] aliases) {
        List<String> out = new ArrayList<>();
        if (aliases == null) {
            return out;
        }

        for (String alias : aliases) {
            if (alias != null && !alias.trim().isEmpty()) {
                out.add(alias);
            }
        }

        return out;
    }

    private static String resolveDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return Director.DEFAULT_DESCRIPTION;
        }

        return description;
    }
}
