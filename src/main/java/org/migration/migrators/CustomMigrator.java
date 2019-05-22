package org.migration.migrators;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.MigratorFactory;
 
/**
 * <p>
 * Implements a custom entity migration that may have any effect on an entity's data or type information.
 * </p>
 * <p>
 * <b>IMPORTANT:</b> All concrete implementations of this interface must have a constructor that has a String (the entity name) and a single
 * {@link Element}-typed parameter
 * </p>
 */
public interface CustomMigrator extends EntityMigrator {
    /**
	 * Initializes this migrator
	 * 
	 * @param entity
	 *            The name of the entity that this migrator will be applied to
	 * @param config
	 *            The configuration element for the migrator
	 * @param migration
	 *            The migration set that the migrator is a part of
	 * @param factory
	 *            The factory to parse sub-migrations if needed
	 * @return This migrator
	 */
	CustomMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory);

	/**
	 * Initializes this migrator with the entity type set it will run against. Always called after
	 * {@link #init(String, Element, MigrationSet, MigratorFactory)}.
	 * 
	 * @param entities
	 *            The entity type set this migrator will run against
	 * @param dissecter TODO
	 * @return This migrator
	 */
	default CustomMigrator init(EntityTypeSet entities, TypeSetDissecter dissecter) {
		return this;
	}

    /**
     * Serializes this migrator to XML
     *
     * @return The XML serialization of this migrator
     */
    Element serialize();
}
