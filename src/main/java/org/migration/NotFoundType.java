package org.migration;

import java.lang.reflect.Type;

/** Represents a class that can no longer be found by the class loader */
public class NotFoundType implements Type {
    /** The name of the class */
    public final String name;

    /**
     * @param typeName
     *            The name of the class
     */
    public NotFoundType(String typeName) {
        name = typeName;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + 13;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NotFoundType && ((NotFoundType) o).name.equals(name);
    }

    @Override
    public String toString() {
        return name + " (not found)";
    }
}