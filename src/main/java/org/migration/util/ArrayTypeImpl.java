package org.migration.util;

import java.lang.reflect.Type;

class ArrayTypeImpl implements Type {
    private final Type theComponentType;

    ArrayTypeImpl(Type componentType) {
        theComponentType = componentType;
    }

    public Type getComponentType() {
        return theComponentType;
    }

    @Override
    public String toString() {
        return theComponentType + "[]";
    }
}
