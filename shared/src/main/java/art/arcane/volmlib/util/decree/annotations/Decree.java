package art.arcane.volmlib.util.decree.annotations;

import art.arcane.volmlib.util.decree.DecreeOrigin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Decree {
    String DEFAULT_DESCRIPTION = "No Description Provided";

    String name() default "";

    boolean studio() default false;

    boolean sync() default false;

    String description() default DEFAULT_DESCRIPTION;

    DecreeOrigin origin() default DecreeOrigin.BOTH;

    String[] aliases() default "";
}
