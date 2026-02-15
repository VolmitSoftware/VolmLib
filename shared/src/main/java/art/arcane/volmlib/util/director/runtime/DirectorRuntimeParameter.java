package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.annotations.Param;
import art.arcane.volmlib.util.decree.specialhandlers.NoParameterHandler;

import java.lang.reflect.Parameter;

public final class DirectorRuntimeParameter {
    private final DirectorParameterDescriptor descriptor;
    private final Parameter parameter;
    private final Param annotation;
    private volatile DecreeParameterHandler<?> customHandler;
    private volatile boolean customHandlerInitialized;

    public DirectorRuntimeParameter(DirectorParameterDescriptor descriptor, Parameter parameter, Param annotation) {
        this.descriptor = descriptor;
        this.parameter = parameter;
        this.annotation = annotation;
    }

    public DirectorParameterDescriptor getDescriptor() {
        return descriptor;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public Param getAnnotation() {
        return annotation;
    }

    public DecreeParameterHandler<?> getCustomHandlerOrNull() {
        if (customHandlerInitialized) {
            return customHandler;
        }

        synchronized (this) {
            if (customHandlerInitialized) {
                return customHandler;
            }

            try {
                Class<?> handlerType = annotation.customHandler();
                if (handlerType == null || handlerType == NoParameterHandler.class) {
                    customHandler = null;
                } else {
                    Object instance = handlerType.getConstructor().newInstance();
                    if (instance instanceof DecreeParameterHandler<?> decreeHandler) {
                        customHandler = decreeHandler;
                    } else {
                        customHandler = null;
                    }
                }
            } catch (Throwable ignored) {
                customHandler = null;
            } finally {
                customHandlerInitialized = true;
            }
        }

        return customHandler;
    }
}
