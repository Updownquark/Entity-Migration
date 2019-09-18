package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** A migrator that doesn't do anything */
public class NullMigrator implements EntityMigrator {
    private String theEntityName;

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     */
    public NullMigrator(String entity) {
        theEntityName = entity;
    }

    @Override
    public String getEntityName() {
        return theEntityName;
    }

    @Override
	public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        return oldVersionEntity;
    }

    @Override
    public String toString() {
        return "x";
    }
}
