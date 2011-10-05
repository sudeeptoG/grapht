package org.grouplens.inject.graph;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

/**
 * A concrete type. It has a set of dependencies which must be satisfied in
 * order to instantiate it. It can also be viewed as an instantiable extension
 * of {@link Type}.
 * 
 * <p>
 * Nodes are expected to provide a reasonable implementation of
 * {@link #equals(Object)} and {@link #hashCode()} so that they can be
 * de-duplicated, etc.
 * 
 * @author Michael Ekstrand <ekstrand@cs.umn.edu>
 * 
 */
public interface Node {
    /**
     * Get this node's dependencies.
     * 
     * @return A list of dependencies which must be satisfied in order to
     *         instantiate this node.
     */
    List<Desire> getDependencies();
    
    /**
     * Create an instance of the type satisfied by this node.
     * 
     * @param dependencies A map of desires to providers of those desires, used
     *        to instantiate the object.
     * @return A new instance of the type represented by this node, with the
     *         specified dependencies injected.
     */
    Object createInstance(Map<Desire, Provider<? extends Object>> dependencies);
}