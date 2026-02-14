package art.arcane.volmlib.util.decree.specialhandlers;

import art.arcane.volmlib.util.decree.DecreeParameterHandlerType;

/**
 * Shared sentinel type used by {@code @Param} to indicate auto handler resolution.
 */
public final class NoParameterHandler implements DecreeParameterHandlerType {
    private NoParameterHandler() {
    }
}
