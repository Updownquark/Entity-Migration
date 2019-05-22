package org.migration;

/** Retrieves types by name. Crucial if any non-entity types in data to be imported are not accessible from the classpath of this library. */
@FunctionalInterface
public interface TypeGetter {
    /**
     * @param name
     *            The fully-qualified java name of the class
     * @return The class, if it is known
     * @throws ClassNotFoundException
     *             If the class cannot be found from this type getter
     */
    Class<?> getType(String name) throws ClassNotFoundException;
}
