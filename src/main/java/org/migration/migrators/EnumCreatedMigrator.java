package org.migration.migrators;

import org.migration.generic.EnumType;

/** Represents the initial creation of an enum */
public class EnumCreatedMigrator extends EnumTypeModificationMigrator {
    /** The enum that was created */
    public EnumType enumType;

    /**
     * @param e
     *            The enum that was created
     */
    public EnumCreatedMigrator(EnumType e) {
        super(e.getName(), EnumTypeModification.creation);
        enumType = e;
    }

    @Override
    public String toString() {
        return "Created " + getEntityName();
    }
}
