package org.migration.migrators;

import org.migration.generic.EntityType;

/** Represents the initial creation of an entity */
public class EntityCreatedMigrator extends EntityTypeModificationMigrator {
    /** The entity that was created */
    public EntityType entity;

    /**
     * @param e
     *            The entity that was created
     */
    public EntityCreatedMigrator(EntityType e) {
        super(e.getName(), EntityTypeModification.creation);
        entity = e;
    }

    @Override
    public String toString() {
        return "Created " + getEntityName();
    }
}
