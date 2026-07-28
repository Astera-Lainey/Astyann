package afb.astyann.codegeneration.exception;

import afb.astyann.codegeneration.domain.CodeLayer;

/**
 * Thrown by {@code approve()} when a layer still contains un-implemented stub methods
 * (methods throwing {@code UnsupportedOperationException} the AI logic-injection pass never
 * filled). Stub bodies compile, so this is the guard that stops a silently-incomplete backend
 * from being approved. Can be relaxed with {@code codegen.approve.require-complete=false}.
 */
public class CodeNotImplementedException extends RuntimeException {
    public CodeNotImplementedException(CodeLayer layer, int stubMethodsRemaining) {
        super(layer + " has " + stubMethodsRemaining + " un-implemented stub method(s); "
                + "regenerate or fix the logic before approving "
                + "(or set codegen.approve.require-complete=false to override).");
    }
}
