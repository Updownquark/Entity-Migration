package org.migration.migrators;

/** Represents the addition of a constant value to an enum type */
public class EnumValueAddedMigrator extends EnumValueModificationMigrator {
    /**
     * @param enumType
     *            The name of the enum that this migrator operates on
     * @param value
     *            The name of the value to add
     */
    public EnumValueAddedMigrator(String enumType, String value) {
        super(enumType, EnumTypeModification.valueAddition, value);
    }

    @Override
    public String toString() {
        return "Add " + getEntityName() + "." + value;
    }
}
