package org.migration.migrators;

/** Represents a name change to a constant value in an enum type */
public class EnumValueRenameMigrator extends EnumValueModificationMigrator {
    /** The name of the value before the rename */
    public final String beforeName;

    /** The name of the value after the rename */
    public final String afterName;

    /**
     * @param enumType
     *            The name of the enum that this migrator operates on
     * @param from
     *            The name of the value to rename
     * @param to
     *            The new name for the value
     */
    public EnumValueRenameMigrator(String enumType, String from, String to) {
        super(enumType, EnumTypeModification.valueRename, from);
        beforeName = from;
        afterName = to;
    }

    @Override
    public String toString() {
        return "Rename " + getEntityName() + "." + beforeName + " to " + afterName;
    }
}
