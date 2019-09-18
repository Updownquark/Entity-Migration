	package org.migration.generic;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.migration.MigrationDef;
import org.migration.MigrationSet;
import org.migration.TypeGetter;
import org.migration.migrators.EntityMigrator;
import org.migration.migrators.EntityTypeModificationMigrator;
import org.migration.migrators.EnumTypeModificationMigrator;

/**
 * Supports versions of entity beans, allowing serialized entity data to be imported even if the entity types have been changed since the
 * data was exported
 */
public class EntityVersionSupport {
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("ddMMMyyyy HH:mm:ss.SSS");
	private static SimpleDateFormat LOCAL_DATE_FORMAT = new SimpleDateFormat("ddMMMyyyy HH:mm:ss.SSS z");
	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
		LOCAL_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

    private EntityTypeSet theCurrentTypes;

    private TreeSet<MigrationDef> theMigrationSets;
    private SortedSet<MigrationSet> theExposedMigrationSets;
    private SortedSet<MigrationSet> theExposedReverseMigrationSets;
    private final Set<String> theDataSetTags;

    /**
     * Creates an empty version support
     * 
     * @param dataSetTags
     *            The tags describing this data set
     */
    public EntityVersionSupport(String... dataSetTags) {
        this(new EntityTypeSet(new Date(0)), dataSetTags);
    }

    /**
     * Creates version support for the given types
     * 
     * @param types
     *            The entity types to create version support for
     * @param dataSetTags
     *            The tags describing this data set
     */
    public EntityVersionSupport(EntityTypeSet types, String... dataSetTags) {
        theCurrentTypes = types;
        theMigrationSets = new TreeSet<>();
        Set<String> tagSet = new LinkedHashSet<>();
        for (String dataSet : dataSetTags) {
			tagSet.add(dataSet);
		}
        theDataSetTags = Collections.unmodifiableSet(tagSet);
        /* theMigrationSets is typed MigrationDef because there's no other way to take advantage of the binary storage structure when
         * searching for a MigrationDef.  But the list will only ever have MigrationSet instances in it, so these horrific generic hacks
         * are safe at run time. */
        theExposedMigrationSets = (SortedSet<MigrationSet>) (SortedSet<?>) Collections.unmodifiableSortedSet(theMigrationSets);
        theExposedReverseMigrationSets = (SortedSet<MigrationSet>) (SortedSet<?>) Collections
                .unmodifiableSortedSet(theMigrationSets.descendingSet());
    }

    /**
     * Creates a version support, importing data from an XML file that was written with {@link EntityTypeSet#save(Writer)}
     *
     * @param in
     *            The reader to read version support data from
     * @param typeGetter
     *            The type getter to allow injection of types not accessible here
     * @param dataSetTags
     *            The tags describing this data set
     * @throws IOException
     *             If an error occurs reading the file
     * @throws JDOMException
     *             If an error occurs parsing the file
     */
    public EntityVersionSupport(Reader in, TypeGetter typeGetter, String... dataSetTags) throws IOException, JDOMException {
        this(dataSetTags);
        theCurrentTypes.read(in, typeGetter);
    }

    /** @return The version date of the current entity type set */
    public Date getCurrentVersionDate() {
        return theCurrentTypes.getVersionDate();
    }

    /** @return The type set at the head of this version set */
    public EntityTypeSet getCurrentTypeSet() {
        return theCurrentTypes;
    }

    /** @return The tags describing this data set */
    public Set<String> getDataSetTags() {
        return theDataSetTags;
    }

    /** @return All migration sets in this version set, sorted from oldest to most recent */
    public SortedSet<MigrationSet> getMigrationSets() {
        return theExposedMigrationSets;
    }

    /** @return All migration sets in this version set, sorted from most recent to oldest */
    public SortedSet<MigrationSet> getDescendingMigrationSets() {
        return theExposedReverseMigrationSets;
    }

    /**
     * @param mig
     *            The migration definition to search for
     * @return The migration set in this version set matching the given definition, or null if no such migration set exists in this version
     *         set
     */
    public MigrationSet findMigration(MigrationDef mig) {
        MigrationSet migSet = (MigrationSet) theMigrationSets.floor(mig);
        return mig.equals(migSet) ? migSet : null;
    }

    /**
     * @param migration
     *            The migration set to add to this version support
     */
    public void addMigrationSet(MigrationSet migration) {
        migration.seal();
        theMigrationSets.add(migration);
        for (EntityMigrator migrator : migration.getMigrators()) {
			if (migrator instanceof EntityTypeModificationMigrator) {
				theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
			}
		}
        theCurrentTypes.setVersionDate(theMigrationSets.last().getDate());
    }

    /**
	 * @param migrationEl
	 *            The XML element representing a migration set
	 * @param factory
	 *            The migrator factory to use to deserialize the migrators
	 * @return The migration set deserialized from the XML, or null if the migration was already present in this version support
	 */
	public MigrationSet addMigrationSet(Element migrationEl, MigratorFactory factory) {
		MigrationSet ret = deserializeMigration(migrationEl, factory);
        if (ret != null) {
            ret.seal();
            theMigrationSets.add(ret);
            theCurrentTypes.setVersionDate(theMigrationSets.last().getDate());
        }
        return ret;
    }

    /**
	 * Imports migrations from an XML file
	 *
	 * @param in
	 *            The file to read
	 * @param factory
	 *            The migrator factory to use to deserialize migrators
	 * @throws IOException
	 *             If an error occurs reading the file
	 * @throws JDOMException
	 *             If an error occurs parsing the file
	 */
	public void importMigrations(Reader in, MigratorFactory factory)
			throws IOException, JDOMException {
        theMigrationSets.clear();
        Element root = new SAXBuilder().build(in).getRootElement();
        if (!root.getName().equals("entity-versions") && !root.getName().equals("entity-migrations")) {
			throw new IllegalArgumentException("File " + in + " is not an entity migration file");
		}
        for (Element migrationSetEl : root.getChild("migrations").getChildren("migration")) {
			theMigrationSets.add(deserializeMigration(migrationSetEl, factory));
		}
    }

    /**
	 * Migrates this version support. This is different from {@link #importMigrations(Reader, MigratorFactory, TypeGetter)} in that this
	 * method actually migrates this version support's types. importMigrations imports migrations that are assumed to be prior to the
	 * existing entities' versions.
	 *
	 * @param in
	 *            The reader to read the migrations from
	 * @param factory
	 *            The migration factory to use to deserialize the migrations
	 * @param past
	 *            The date past which to import migrations for, or null to remove the lower date bound
	 * @param until
	 *            The date up to which to import migrations for, or null to remove the upper date bound
	 * @throws IOException
	 *             If an error occurs reading the file
	 * @throws JDOMException
	 *             If an error occurs parsing the file
	 */
	public void migrate(Reader in, MigratorFactory factory, Date past, Date until)
			throws IOException, JDOMException {
        Element root = new SAXBuilder().build(in).getRootElement();
        if (!root.getName().equals("entity-migrations") && !root.getName().equals("entity-versions")) {
			throw new IllegalArgumentException("File " + in + " is not an entity migration file");
		}
        if (root.getChild("migrations") == null) {
			return;
		}
        for (Element migrationSetEl : root.getChild("migrations").getChildren("migration")) {
			MigrationSet migSet = deserializeMigration(migrationSetEl, factory);
            if (migSet == null) {
				continue;
			}
            if (past != null && migSet.getDate().compareTo(past) <= 0) {
				continue;
			}
            if (until != null && migSet.getDate().compareTo(until) > 0) {
				continue;
			}
            addMigrationSet(migSet);
        }
    }

	private MigrationSet deserializeMigration(Element migrationSetEl, MigratorFactory factory) {
        String author = migrationSetEl.getAttributeValue("author");
        if (author == null) {
			throw new IllegalStateException("author attribute missing for migration");
		}
        Date date;
        try {
            date = DATE_FORMAT.parse(migrationSetEl.getAttributeValue("date"));
        } catch (NullPointerException e) {
            throw new IllegalStateException("date attribute missing for migration by " + author);
        } catch (ParseException e) {
			try {
				date = LOCAL_DATE_FORMAT.parse(migrationSetEl.getAttributeValue("date"));
			} catch (ParseException e2) {
            throw new IllegalStateException(
                    "date attribute malformatted for migration by " + author + ": " + migrationSetEl.getAttributeValue("date"), e);
        }
        }
        MigrationDef preSet = theMigrationSets.floor(new MigrationDef(author, date));
        if (preSet != null && preSet.getDate().equals(date) && preSet.getAuthor().equals(author)) {
			return null;
		}
        String descrip = migrationSetEl.getAttributeValue("description");
        MigrationSet migSet = new MigrationSet(author, date, descrip);
        String excludedTags = migrationSetEl.getAttributeValue("exclude-tags");
        if (excludedTags != null) {
            for (String tag : excludedTags.split(",")) {
				migSet.getExcludedTags().add(tag);
			}
        }
        String includedTags = migrationSetEl.getAttributeValue("include-tags");
        if (includedTags != null) {
            for (String tag : includedTags.split(",")) {
				migSet.getIncludedTags().add(tag);
			}
        }
        if (migrationSetEl.getChild("references") != null) {
            for (Element refEl : migrationSetEl.getChild("references").getChildren()) {
                String refAuthor = refEl.getAttributeValue("author");
                Date refDate;
                try {
                    refDate = DATE_FORMAT.parse(refEl.getAttributeValue("date"));
                } catch (NullPointerException e) {
                    throw new IllegalStateException("date attribute missing for reference to migration by " + author);
                } catch (ParseException e) {
                    throw new IllegalStateException(
                            "date attribute malformatted for reference to migration by " + author + ": " + refEl.getAttributeValue("date"),
                            e);
                }
                boolean required;
                if ("true".equals(refEl.getAttributeValue("required"))) {
					required = true;
				} else if ("false".equals(refEl.getAttributeValue("required"))) {
					required = false;
				} else {
					throw new IllegalStateException("Attribute \"required\" missing or malformatted for migration reference " + author + "/"
                            + refEl.getAttributeValue("date"));
				}
                migSet.getReferences().add(new MigrationSet.MigrationRef(refAuthor, refDate, required));
            }
        }
        boolean shouldApply = migSet.shouldApply(this);
        try {
            for (Element migrationEl : migrationSetEl.getChildren()) {
                if (migrationEl.getName().equals("references")) {
					continue;
				}
				EntityMigrator migrator = factory.deserialize(migrationEl, theCurrentTypes, migSet);
                migSet.getMigrators().add(migrator);
                if (migrator instanceof EntityTypeModificationMigrator && shouldApply) {
                    theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
                } else if (migrator instanceof EnumTypeModificationMigrator && shouldApply) {
                    theCurrentTypes.migrate((EnumTypeModificationMigrator) migrator, true);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            IllegalStateException copy = new IllegalStateException(migSet + ": " + e.getMessage(), e.getCause());
            copy.setStackTrace(e.getStackTrace());
            throw copy;
        }
        migSet.seal();
        return migSet;
    }
}
