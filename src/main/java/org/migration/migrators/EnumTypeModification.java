package org.migration.migrators;

/** A type of modification to an enum type */
public enum EnumTypeModification {
    /** Represents the creation of an enum type */
    creation(EnumCreatedMigrator.class),
    /** Represents the deletion of an enum type */
    deletion(EnumRemovedMigrator.class),
    /** Represents the renaming of an enum type */
    rename(EnumRenameMigrator.class),
    /** Represents the renaming of a constant in an enum type */
    valueRename(EnumValueRenameMigrator.class),
    /** Represents the addition of a constant in an enum type */
    valueAddition(EnumValueAddedMigrator.class),
    /** Represents the removal of a constant in an enum type */
    valueRemoval(EnumValueRemovedMigrator.class);

    /** The type of migrator that performs modifications of this type */
    public final Class<? extends EnumTypeModificationMigrator> migrator;

    private EnumTypeModification(Class<? extends EnumTypeModificationMigrator> migratorType) {
        migrator = migratorType;
    }
}
