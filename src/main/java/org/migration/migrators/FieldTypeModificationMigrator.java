package org.migration.migrators;


/** A modification to an entity type involving a specific field */
public abstract class FieldTypeModificationMigrator extends EntityTypeModificationMigrator {
    /** The name of the field */
    public final String field;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param modType
     *            The modification type of this migration
     * @param fieldName
     *            The name of the field
     */
    protected FieldTypeModificationMigrator(String entity, EntityTypeModification modType, String fieldName) {
        super(entity, modType);
        field = fieldName;
        if (fieldName == null)
            throw new NullPointerException("Field name is null");
    }
}
