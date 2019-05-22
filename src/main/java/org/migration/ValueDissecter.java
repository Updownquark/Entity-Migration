package org.migration;

import java.util.Map;

/**
 * An interface that knows how to pull apart the value of one or more types and create new instances of those types given their field values
 */
public interface ValueDissecter extends Dissecter {
    /**
     * 
     * @param type
     *            The type to get the fields for
     * @return Fields to be used to persist the internal value of values if the given type. If only a single field of
     *         {@link TypeSetDissecter#RECOGNIZED_TYPES recognized} type is needed, the name of the field may optionally be null. If
     *         multiple fields are specified, all of the must have unique, non-null names.
     */
    TypedField[] getFields();

    /**
     * @param entity
     *            The value object to get the field value of
     * @param field
     *            The field to get the value of
     * @return The value of the field in the value
     */
    Object getFieldValue(Object entity, String field);

    /**
     * Creates a new value, given its type and all non-entity field values
     * 
     * @param type
     *            The type of the value to create
     * @param fieldValues
     *            The field values for the new object
     * @return The new object
     */
    Object createWith(Map<String, Object> fieldValues);

    /**
     * @param entity
     *            The entity to set the field value of
     * @param field
     *            The field to set the value of
     * @param fieldValue
     *            The value to set in the field
     */
    void setFieldValue(Object entity, String field, Object fieldValue);
}
