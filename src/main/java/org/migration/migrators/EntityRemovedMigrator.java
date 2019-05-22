package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.EntityType;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** Represents the deletion of an entity type */
public class EntityRemovedMigrator extends EntityTypeModificationMigrator {
    /** The entity that was removed */
    public final EntityType entity;

    /**
     * @param e
     *            The entity that was removed
     */
    public EntityRemovedMigrator(EntityType e) {
        super(e.getName(), EntityTypeModification.deletion);
        entity = e;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        return null;
    }

    @Override
    public String toString() {
        return "Removed " + getEntityName();
    }
}
