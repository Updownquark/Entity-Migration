package org.migration.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** A custom implementation of a parameterized type */
class ParameterizedTypeImpl implements ParameterizedType {
    private final Type theRawType;

    private Type[] theTypeArguments;

    ParameterizedTypeImpl(Type rawType, Type... typeArgs) {
        theRawType = rawType;
        theTypeArguments = typeArgs;
    }

    @Override
    public Type getRawType() {
        return theRawType;
    }

    @Override
    public Type[] getActualTypeArguments() {
        return theTypeArguments.clone();
    }

    @Override
    public Type getOwnerType() {
        return null; // Assume top-level type
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParameterizedTypeImpl)) {
            return false;
        }
        ParameterizedTypeImpl pt = (ParameterizedTypeImpl) o;
        if (!theRawType.equals(pt.theRawType)) {
            return false;
        }
        if (theTypeArguments.length != pt.theTypeArguments.length) {
            return false;
        }
        for (int i = 0; i < theTypeArguments.length; i++) {
            if (!theTypeArguments[i].equals(pt.theTypeArguments[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return theRawType.hashCode() * 13 + org.qommons.ArrayUtils.hashCode(theTypeArguments);
    }

    @Override
    public String toString() {
        return PersistenceUtils.toString(this);
    }
}
