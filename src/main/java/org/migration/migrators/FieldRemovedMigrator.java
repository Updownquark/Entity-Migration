package org.migration.migrators;

import java.lang.reflect.Type;

/** Represents the removal of a field from an entity type */
public class FieldRemovedMigrator extends FieldTypeModificationMigrator {
    /** The type of the field */
    public final Type type;
    /** The mapping field on the target entity */
    public final String map;
    /** The columns that the field's collection value is sorted by */
    public final String[] sorting;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param fieldName
     *            The name of the field to remove
     * @param fieldType
     *            The type of the removed field
     * @param fieldMap
     *            Whether the field to add is mapped
     * @param fieldSorting
     *            The columns that the field's collection value is sorted by
     */
	public FieldRemovedMigrator(String entity, String fieldName, Type fieldType, String fieldMap,
            String[] fieldSorting) {
        super(entity, EntityTypeModification.fieldRemoval, fieldName);
        type = fieldType;
        map = fieldMap;
        sorting = fieldSorting;
    }

    @Override
    public String toString() {
        return "Remove " + getEntityName() + "." + field;
    }
}
