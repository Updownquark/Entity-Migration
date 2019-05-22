package org.migration.migrators;

/** A type of modification to an entity type */
public enum EntityTypeModification {
    /** Represents the creation of an entity type */
    creation(EntityCreatedMigrator.class),
    /** Represents the deletion of an entity type */
    deletion(EntityRemovedMigrator.class),
    /** Represents the renaming of an entity type */
    rename(EntityRenameMigrator.class),
    /** Represents the replacement of an entity type's super-type */
    replaceSuper(ReplaceSuperMigrator.class),
    /** Represents the renaming of a field in an entity type */
    fieldRename(FieldRenameMigrator.class),
    /** Represents the addition of a field in an entity type */
    fieldAddition(FieldAddedMigrator.class),
    /** Represents the removal of a field in an entity type */
    fieldRemoval(FieldRemovedMigrator.class),
    /** Represents a change to the nullability of a field */
    fieldNullability(NullabilityMigrator.class);

    /** The type of migrator that performs modifications of this type */
    public final Class<? extends EntityTypeModificationMigrator> migrator;

    private EntityTypeModification(Class<? extends EntityTypeModificationMigrator> migratorType) {
        migrator = migratorType;
    }
}
