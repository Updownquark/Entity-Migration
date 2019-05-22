package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** A migrator representing a change that directly affects an entity's type */
public abstract class EntityTypeModificationMigrator implements EntityMigrator {
    private final String theEntity;

    private final EntityTypeModification theType;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param type
     *            The modification type of this migration
     */
    protected EntityTypeModificationMigrator(String entity, EntityTypeModification type) {
        theEntity = entity;
        theType = type;
        if (!theType.migrator.isInstance(this)) {
            throw new IllegalStateException("Unrecognized type modification migration type: "+getClass().getName());
        }
    }

    @Override
    public String getEntityName() {
        return theEntity;
    }

    /** @return The modification type of this migration */
    public EntityTypeModification getType() {
        return theType;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        return oldVersionEntity;
    }
}
