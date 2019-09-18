package org.migration.migrators;

import java.lang.reflect.Type;

/** Represents the addition of a field to an entity type */
public class FieldAddedMigrator extends FieldTypeModificationMigrator {
    /** The type of the field */
    public final Type type;
    /** Whether the field is nullable */
    public final String map;
    /** The columns that this field's collection value is sorted by */
    public final String[] sorting;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param fieldName
     *            The name of the field to add
     * @param fieldType
     *            The type of the field to add
     * @param fieldMap
     *            The mapping field on the target entity
     * @param fieldSorting
     *            The columns that this field's collection value is sorted by
     */
	public FieldAddedMigrator(String entity, String fieldName, Type fieldType, String fieldMap, String[] fieldSorting) {
        super(entity, EntityTypeModification.fieldAddition, fieldName);
        type = fieldType;
        map = fieldMap;
        sorting = fieldSorting;
    }

    @Override
    public String toString() {
        return "Add " + getEntityName() + "." + field;
    }
}
