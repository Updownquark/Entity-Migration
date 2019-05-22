package org.migration.migrators;

/** Represents a name change to an entity type */
public class EntityRenameMigrator extends EntityTypeModificationMigrator {
    /** The name of the entity after the change */
    public final String afterName;

    /**
     * @param from
     *            The name of the entity before the change
     * @param to
     *            The name of the entity after the change
     */
    public EntityRenameMigrator(String from, String to) {
        super(from, EntityTypeModification.rename);
        afterName = to;
    }

    @Override
    public String toString() {
        return "Rename " + getEntityName() + " to " + afterName;
    }
}
