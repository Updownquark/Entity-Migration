package org.migration.migrators;

/** Represents the removal of a constant value from an enum type */
public class EnumValueRemovedMigrator extends EnumValueModificationMigrator {
    /**
     * @param enumType
     *            The name of the enum that this migrator operates on
     * @param value
     *            The name of the constant to remove
     */
    public EnumValueRemovedMigrator(String enumType, String value) {
        super(enumType, EnumTypeModification.valueRemoval, value);
    }

    @Override
    public String toString() {
        return "Remove " + getEntityName() + "." + value;
    }
}
