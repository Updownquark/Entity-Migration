package org.migration.migrators;

import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** A migrator that synthesizes new data for an entity type */
public interface DataPopulator extends CustomMigrator {
    /** Allows creation of entities */
    public interface EntityCreator {
        /**
         * @param type
         *            The type of the entity to create
         * @return The new entity
         */
        GenericEntity create(String type);
    }

    /**
     * @param allEntities
     *            All entities in the configuration
     * @param creator
     *            The entity creator to use to create the entities
     */
	void populateData(GenericEntitySet allEntities, EntityCreator creator);
}
