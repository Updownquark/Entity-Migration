package org.migration.migrators;

/** Represents a name change to an enum type */
public class EnumRenameMigrator extends EnumTypeModificationMigrator {
    /** The name of the enum after the change */
    public final String afterName;

    /**
     * @param from
     *            The name of the entity before the change
     * @param to
     *            The name of the entity after the change
     */
    public EnumRenameMigrator(String from, String to) {
        super(from, EnumTypeModification.rename);
        afterName = to;
    }

    @Override
    public String toString() {
        return "Rename " + getEntityName() + " to " + afterName;
    }
}
