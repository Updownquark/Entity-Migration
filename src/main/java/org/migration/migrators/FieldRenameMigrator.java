package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** Represents a name change to a field in an entity type */
public class FieldRenameMigrator extends FieldTypeModificationMigrator {
    /** The name of the field before the rename */
    public final String beforeName;

    /** The name of the field after the rename */
    public final String afterName;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param from
     *            The name of the field to rename
     * @param to
     *            The new name for the field
     */
    public FieldRenameMigrator(String entity, String from, String to) {
        super(entity, EntityTypeModification.fieldRename, from);
        beforeName = from;
        afterName = to;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        oldVersionEntity.set(afterName, oldVersionEntity.get(beforeName));
        return oldVersionEntity;
    }

    @Override
    public String toString() {
        return "Rename " + getEntityName() + "." + beforeName + " to " + afterName;
    }
}
