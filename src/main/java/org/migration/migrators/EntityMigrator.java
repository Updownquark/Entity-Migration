package org.migration.migrators;

import org.migration.TypeSetDissecter;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

/** Represents a modification to an entity (or enum, in the case of {@link EntityTypeModificationMigrator}s) */
public interface EntityMigrator {
    /** @return The name of the entity (or enum, in the case of {@link EntityTypeModificationMigrator}s) that this migrator operates on */
    String getEntityName();
    
    /**
     * @param oldVersionEntity
     *            The entity instance's value before the migration
     * @param allEntities
     *            All entities being migrated, some already migrated, some not
     * @param dissecter
     *            The dissecter to understand data types
     * @return The entity instance's value after the migration, or null if the instance should be deleted as a result of this migration. The
     *         new entity can be the modified <code>oldVersionEntity</code> or a new entity to replace the argument.
     */
	GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter);
}
