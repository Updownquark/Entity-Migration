package org.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.migration.generic.EntityVersionSupport;
import org.migration.migrators.EntityMigrator;
import org.migration.migrators.EntityTypeModificationMigrator;
import org.migration.migrators.EnumTypeModificationMigrator;
import org.qommons.Sealable;

/** A set of configured migrations */
public class MigrationSet extends MigrationDef implements Sealable {
    /** A reference to another migration set */
    public static class MigrationRef extends MigrationDef {
        private final boolean isRequired;

        /**
         * @param author
         *            The name of the author who wrote the migration
         * @param date
         *            When the migration was created
         * @param required
         *            True if the referenced migration set must be present in order to apply this migration set, or false if the referenced
         *            migration set must <b>NOT</b> be present in order to apply this migration set
         */
        public MigrationRef(String author, Date date, boolean required) {
            super(author, date);
            isRequired = required;
        }

        /**
         * @return True if the referenced migration set must be present in order to apply this migration set, or false if the referenced
         *         migration set must <b>NOT</b> be present in order to apply this migration set
         */
        public boolean isRequired() {
            return isRequired;
        }
    }

    private final String theDescription;

    private List<MigrationRef> theReferences;

    private List<EntityMigrator> theMigrations;

    private Set<String> theIncludedTags;
    private Set<String> theExcludedTags;

    private boolean isSealed;

    /**
     * @param author
     *            The name of the author who wrote this migration
     * @param date
     *            When this migration was created
     * @param description
     *            The author's description of this migration's purpose
     */
    public MigrationSet(String author, Date date, String description) {
        super(author, date);
        theDescription = description;
        theReferences = new ArrayList<>(2);
        theMigrations = new ArrayList<>();
        theIncludedTags = new LinkedHashSet<>();
        theExcludedTags = new LinkedHashSet<>();
    }


    /** @return The author's description of this migration's purpose */
    public String getDescription() {
        return theDescription;
    }

    /** @return The migration references that must be checked before this migration set is applied */
    public List<MigrationRef> getReferences() {
        return theReferences;
    }

    /** @return The migrations in this set */
    public List<EntityMigrator> getMigrators() {
        return theMigrations;
    }

    /**
     * @return {@link EntityVersionSupport#getDataSetTags() Data set tags} that a data set must be tagged with for this migration to apply
     */
    public Set<String> getIncludedTags() {
        return theIncludedTags;
    }

    /**
     * @return {@link EntityVersionSupport#getDataSetTags() Data set tags} that a data set may not be tagged with for this migration to
     *         apply
     */
    public Set<String> getExcludedTags() {
        return theExcludedTags;
    }

    /** @return Whether this migration set contains any type modifications */
    public boolean hasTypeMigrations() {
        for (EntityMigrator migrator : theMigrations) {
            if (migrator instanceof EntityTypeModificationMigrator || migrator instanceof EnumTypeModificationMigrator) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param version
     *            The version set to check
     * @return Whether this migration should be applied to the version based on this migration's references
     */
    public boolean shouldApply(EntityVersionSupport version) {
        if (!version.getDataSetTags().containsAll(theIncludedTags)) {
			return false;
		}
        for (String tag : theExcludedTags) {
			if (version.getDataSetTags().contains(tag)) {
				return false;
			}
		}
        for (MigrationRef ref : theReferences) {
			if ((version.findMigration(ref) != null) != ref.isRequired()) {
				return false;
			}
		}
        return true;
    }

    @Override
    public boolean isSealed() {
        return isSealed;
    }

    @Override
    public void seal() {
        if (isSealed) {
			return;
		}
        isSealed = true;
        theReferences = Collections.unmodifiableList(theReferences);
        theMigrations = Collections.unmodifiableList(theMigrations);
        theIncludedTags = Collections.unmodifiableSet(theIncludedTags);
        theExcludedTags = Collections.unmodifiableSet(theExcludedTags);
    }

    @Override
    public String toString() {
        return super.toString() + ": " + theDescription;
    }
}