package art.arcane.volmlib.util.director.annotations;

import art.arcane.volmlib.util.director.DirectorParameterHandlerType;
import art.arcane.volmlib.util.director.specialhandlers.NoParameterHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String DEFAULT_DESCRIPTION = "No Description Provided";

    String name() default "";

    String description() default DEFAULT_DESCRIPTION;

    String defaultValue() default "";

    String[] aliases() default "";

    boolean contextual() default false;

    Class<? extends DirectorParameterHandlerType> customHandler() default NoParameterHandler.class;
}
