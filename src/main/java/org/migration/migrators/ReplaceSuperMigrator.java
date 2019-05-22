package org.migration.migrators;

import org.migration.generic.EntityType;

/** Replaces an entity type's super-type */
public class ReplaceSuperMigrator extends EntityTypeModificationMigrator {
    /** The entity type to modify */
    public final EntityType entity;
    /** The new super-type for the entity */
    public final EntityType newSuperType;

    /**
     * @param entity
     *            The entity type to modify
     * @param newSuper
     *            The new super-type for the entity
     */
    public ReplaceSuperMigrator(EntityType entity, EntityType newSuper) {
        super(entity.getName(), EntityTypeModification.replaceSuper);
        this.entity = entity;
        newSuperType = newSuper;
    }

	@Override
    public String toString() {
		return "Changes super-type of " + getEntityName() + " to " + (newSuperType == null ? "none" : newSuperType.getName());
    }
}
