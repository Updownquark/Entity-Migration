package org.migration.migrators;

/** A modification to an enum type involving a specific constant value */
public abstract class EnumValueModificationMigrator extends EnumTypeModificationMigrator {
    /** The name of the constant */
    public final String value;

    /**
     * @param enumType
     *            The name of the entity that this migrator operates on
     * @param modType
     *            The modification type of this migration
     * @param valueName
     *            The name of the constant
     */
    protected EnumValueModificationMigrator(String enumType, EnumTypeModification modType, String valueName) {
        super(enumType, modType);
        value = valueName;
        if (valueName == null)
            throw new NullPointerException("Enum constant name is null");
    }
}
