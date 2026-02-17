package art.arcane.volmlib.util.director.specialhandlers;

import art.arcane.volmlib.util.director.DirectorParameterHandlerType;

/**
 * Shared sentinel type used by {@code @Param} to indicate auto handler resolution.
 */
public final class NoParameterHandler implements DirectorParameterHandlerType {
    private NoParameterHandler() {
    }
}
