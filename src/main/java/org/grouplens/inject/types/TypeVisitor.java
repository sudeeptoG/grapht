package org.grouplens.inject.types;

import com.google.common.base.Function;

import java.lang.reflect.*;

/**
 * Visit a type, invoking a method appropriate based on its subtype.
 *
 * @author Michael Ekstrand
 */
public abstract class TypeVisitor<T> implements Function<Type, T> {
    protected final boolean defaultToNull;

    /**
     * Construct a new type visitor, controlling whether it will return null.
     * 
     * @param defaultNull If {@code true}, return {@code null} in
     *            {@link #visitDefault(Type)}; otherwise, throw
     *            {@link IllegalArgumentException}.
     */
    protected TypeVisitor(boolean defaultNull) {
        defaultToNull = defaultNull;
    }

    /**
     * Construct a new type visitor that returns null in
     * {@link #visitDefault(Type)}.
     */
    protected TypeVisitor() {
        this(true);
    }

    /**
     * Apply this visitor to a type, invoking the proper method based on the
     * type's actual class.
     * 
     * @param type The type to visit.
     * @return The return value of the visit method.
     */
    @Override
    public final T apply(Type type) {
        if (type instanceof Class) {
            return visitClass((Class<?>) type);
        } else if (type instanceof TypeVariable) {
            return visitTypeVariable((TypeVariable<?>) type);
        } else if (type instanceof WildcardType) {
            return visitWildcard((WildcardType) type);
        } else if (type instanceof ParameterizedType) {
            return visitParameterizedType((ParameterizedType) type);
        } else if (type instanceof GenericArrayType) {
            return visitGenericArrayType((GenericArrayType) type);
        } else {
            throw new IllegalArgumentException("unknown type of type");
        }
    }

    /**
     * Default vist method, called by all type-specific visit methods in the
     * base class. The base implementation of this method returns <tt>null</tt>
     * or throws {@link IllegalArgumentException}, depending on how the visitor
     * was constructed.
     * 
     * @param type The type being visited.
     * @return The default computation result.
     */
    public T visitDefault(Type type) {
        if (defaultToNull) {
            return null;
        } else {
            throw new IllegalArgumentException("unsupported type " + type.toString());
        }
    }

    public T visitClass(Class<?> cls) {
        return visitDefault(cls);
    }

    public T visitTypeVariable(TypeVariable<?> var) {
        return visitDefault(var);
    }
    
    public T visitWildcard(WildcardType var) {
        return visitDefault(var);
    }
    
    public T visitParameterizedType(ParameterizedType type) {
        return visitDefault(type);
    }
    
    public T visitGenericArrayType(GenericArrayType type) {
        return visitDefault(type);
    }
}