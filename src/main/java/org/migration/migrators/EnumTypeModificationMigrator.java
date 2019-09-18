package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** A migrator representing a change that directly affects an entity's type */
public abstract class EnumTypeModificationMigrator implements EntityMigrator {
    private final String theEntity;

    private final EnumTypeModification theType;

    /**
     * @param entity
     *            The name of the enum that this migrator operates on
     * @param type
     *            The modification type of this migration
     */
    protected EnumTypeModificationMigrator(String entity, EnumTypeModification type) {
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
    public EnumTypeModification getType() {
        return theType;
    }

    /**
     * Enum modifications do not get a chance to modify the entity set themselves, but are recognized and handled specifically by the entity
     * set
     */
    @Override
	public final GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        return oldVersionEntity;
    }
}
