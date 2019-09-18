package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** Represents a change in the nullability of a column */
public class NullabilityMigrator extends FieldTypeModificationMigrator {
    /** Whether the field is nullable after this change */
    public final boolean nullable;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param fieldName
     *            The name of the field to modify
     * @param newNullable
     *            Whether the field is nullable after this change
     */
    public NullabilityMigrator(String entity, String fieldName, boolean newNullable) {
        super(entity, EntityTypeModification.fieldNullability, fieldName);
        nullable = newNullable;
    }

    @Override
	public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        if (!nullable && oldVersionEntity.get(field) == null)
            return null;
        else
            return oldVersionEntity;
    }

    @Override
    public String toString() {
        return "Set " + getEntityName() + "." + field + (nullable ? "" : " NOT") + " NULLABLE";
    }
}
