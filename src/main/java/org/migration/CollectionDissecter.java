package org.migration;

import java.lang.reflect.Type;
import java.util.Collection;

/** Understands how to assemble and disassemble a collection type */
public interface CollectionDissecter extends Dissecter {
    /** @return The type of elements in instances of this dissecter's type */
    Type getComponentType();

    /**
     * @param collection
     *            The collection to get the elements of
     * @return An iterable over the elements of the collection
     */
    Iterable<?> getElements(Object collection);

    /** @return A name for elements of this collection */
    String getElementName();

    /**
     * @param elements
     *            The elements to assemble into a collection of this dissecter's type
     * @param field
     *            The field that this collection type belongs to. May affect ordering.
     * @return The assembled collection
     */
    Object createFrom(Collection<?> elements, TypedField field);
}
