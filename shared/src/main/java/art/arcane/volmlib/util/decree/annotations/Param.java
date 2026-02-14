package art.arcane.volmlib.util.decree.annotations;

import art.arcane.volmlib.util.decree.DecreeParameterHandlerType;
import art.arcane.volmlib.util.decree.specialhandlers.NoParameterHandler;

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

    Class<? extends DecreeParameterHandlerType> customHandler() default NoParameterHandler.class;
}
