package org.migration.migrators;

import org.migration.generic.EnumType;

/** Represents the deletion of an enum type */
public class EnumRemovedMigrator extends EnumTypeModificationMigrator {
    /** The enum that was removed */
    public final EnumType enumType;

    /**
     * @param e
     *            The entity that was removed
     */
    public EnumRemovedMigrator(EnumType e) {
        super(e.getName(), EnumTypeModification.deletion);
        enumType = e;
    }

    @Override
    public String toString() {
        return "Removed " + getEntityName();
    }
}
