package org.migration.migrators;

import java.lang.reflect.Type;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** Represents the addition of a field to an entity type */
public class FieldAddedMigrator extends FieldTypeModificationMigrator {
    /** The type of the field */
    public final Type type;
    /** Whether the field is nullable */
    public final String map;
    /** The columns that this field's collection value is sorted by */
    public final String[] sorting;
	/** String representation of the initial value for the new field */
	public final String initialValue;

    /**
	 * @param entity The name of the entity that this migrator operates on
	 * @param fieldName The name of the field to add
	 * @param fieldType The type of the field to add
	 * @param fieldMap The mapping field on the target entity
	 * @param fieldSorting The columns that this field's collection value is sorted by
	 * @param initialValue The initial value for the field
	 */
	public FieldAddedMigrator(String entity, String fieldName, Type fieldType, String fieldMap, String[] fieldSorting,
		String initialValue) {
        super(entity, EntityTypeModification.fieldAddition, fieldName);
        type = fieldType;
        map = fieldMap;
        sorting = fieldSorting;
		this.initialValue = initialValue;
    }

    @Override
	public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
		super.migrate(oldVersionEntity, allEntities, dissecter);
		Object initV = DefaultFieldValueMigrator.createDefaultValue(type, allEntities, dissecter, initialValue);
		oldVersionEntity.set(field, initV);
		return oldVersionEntity;
	}

	@Override
    public String toString() {
        return "Add " + getEntityName() + "." + field;
    }
}
