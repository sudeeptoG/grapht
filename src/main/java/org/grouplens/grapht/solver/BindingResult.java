package org.grouplens.grapht.solver;

import javax.annotation.Nullable;

import org.grouplens.grapht.spi.Desire;

public class BindingResult {
    private final Desire desire;
    private final boolean defer;
    private final boolean terminate;
    
    // FIXME: it might make more sense to have this Desire be not null,
    // and return a null BindingResult when no binding exists
    public BindingResult(@Nullable Desire desire, boolean defer, boolean terminate) {
        this.desire = desire;
        this.defer = defer;
        this.terminate = terminate;
    }
    
    public @Nullable Desire getDesire() {
        return desire;
    }
    
    public boolean isDeferred() {
        return defer;
    }
    
    public boolean terminates() {
        return terminate;
    }
}