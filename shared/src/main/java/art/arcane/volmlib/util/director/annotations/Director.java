package art.arcane.volmlib.util.director.annotations;

import art.arcane.volmlib.util.director.DirectorOrigin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Director {
    String DEFAULT_DESCRIPTION = "No Description Provided";

    String name() default "";

    boolean studio() default false;

    boolean sync() default false;

    String description() default DEFAULT_DESCRIPTION;

    DirectorOrigin origin() default DirectorOrigin.BOTH;

    String[] aliases() default "";
}
