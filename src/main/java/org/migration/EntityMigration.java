package org.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.migration.generic.EntityDifference;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.EntityVersionSupport;
import org.migration.generic.EnumDifference;
import org.migration.generic.EnumType;
import org.migration.generic.GenericEntity;
import org.migration.generic.SimpleGenericEntitySet;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;
import org.migration.util.HibernateExtractionUtil;
import org.migration.util.PersistenceUtils;
import org.qommons.io.HierarchicalResourceReader;
import org.qommons.io.HierarchicalResourceWriter;

/**
 * <p>
 * A utility that makes it easier to use the migration framework.
 * </p>
 * 
 * <p>
 * Whether importing or exporting, the migrator must be populated with
 * <ul>
 * <li>A {@link #setDissecter(TypeSetDissecter) dissecter} to understand the data types being used</li>
 * <li>A set of {@link #addEntityTypes(Collection) entity classes} for which data may be exported or imported</li>
 * <li>A {@link #setTypeGetter(TypeGetter) type getter} if any non-entity custom types are used which are not accessible to this library's
 * classpath</li>
 * </ul>
 * </p>
 * 
 * <p>
 * To export data to XML,
 * <ol>
 * <li>Use {@link #extract(Function, Consumer, Consumer)} to populate the migrator with real entity data. If the data is coming from
 * hibernate, the {@link HibernateExtractionUtil} may be of help.</li>
 * <li>Use {@link #exportGenericEntities()} to export the real entity data to generic entity data.</li>
 * <li>Use {@link #saveGenericEntityData(HierarchicalResourceWriter, Consumer, Consumer)} to save the generic entity data to serial
 * persistence.</li>
 * </ol>
 * </p>
 * 
 * <p>
 * To import data from XML, the {@link #setMigrationFile(ErroringSupplier) migration file} and
 * {@link #setMigrationStorage(DataSetMigrationStorage) migration storage} must also be set. Then,
 * <ol>
 * <li>Use {@link #parse(HierarchicalResourceReader)} to parse generic entity data from serial persistence.</li>
 * <li>Use {@link #importRealEntities(boolean, boolean)} to import the generic entity data into real entities.</li>
 * <li>Retrieve the entity data with {@link #getRealEntities()}.
 * </ol>
 * </p>
 */
public class EntityMigration {
    /**
     * Like {@link java.util.function.Supplier}, but allows the {@link #get()} method to throw an exception.
     * @param <T> The type of data that this supplier supplies
     * @param <E> The type of exception that may be thrown when attempting to supply the data
     */
    @FunctionalInterface
    public interface ErroringSupplier<T, E extends Throwable> {
        /**
         * @return The supplied data
         * @throws E
         *             If the data cannot be supplied
         */
        T get() throws E;
    }

    /** Allows previously-applied migration data to be stored persistently with the rest of the data */
    public interface DataSetMigrationStorage {
        /**
         * @param entitySet
         *            The entity set being migrated
         * @return All migrations that the entity set has already had applied
         */
		List<MigrationSet> getLoggedMigrations(GenericEntitySet entitySet);

        /**
         * @param entitySet
         *            The entity set being migrated
         * @param migration
         *            The migration to save to the entity set
         */
		void logMigration(GenericEntitySet entitySet, MigrationSet migration);
    }

    private final Set<String> theDataSetTags;
    private TypeSetDissecter theDissecter;
	private EntitySetPersistence thePersistence;
    private TypeGetter theTypeGetter;
    private ErroringSupplier<InputStream, IOException> theMigrationFile;
    private DataSetMigrationStorage theMigrationStorage;
    private List<Class<?>> theEntityTypes;
    private EntityVersionSupport theVersion;
    private Predicate<Object> theEntityFilter;
    private EntitySet theRealEntities;
	private GenericEntitySet theGenericEntities;

    /**
     * Creates the migrator utility
     * 
     * @param dataSetTags
     *            The tags describing this data set, affecting whether an {@link MigrationSet#getIncludedTags() inclusively-} or
     *            {@link MigrationSet#getExcludedTags() exclusively-}tagged set of migrations will be applied to the data
     */
    public EntityMigration(String... dataSetTags) {
        theEntityTypes = new ArrayList<>();
        Set<String> tags=new LinkedHashSet<>(dataSetTags.length*3/2);
        for(String tag : dataSetTags) {
			tags.add(tag);
		}
        theDataSetTags = Collections.unmodifiableSet(tags);
    }

    /** @return The tags describing this data set */
    public String[] getDataSetTags() {
        return theDataSetTags.toArray(new String[theDataSetTags.size()]);
    }

    /** @return The real entity set that has been populated in this migrator, if any */
    public EntitySet getRealEntities() {
        return theRealEntities;
    }

    /** @return The generic entity set that has been populated in this migrator, if any */
	public GenericEntitySet getGenericEntities() {
        return theGenericEntities;
    }

    /**
     * @return The version support that has been parsed by this migrator, if any. The version support is parsed by
     *         {@link #parse(HierarchicalResourceReader)}.
     */
    public EntityVersionSupport getVersion() {
        return theVersion;
    }

    /** @return The entity types that have been set in this utility */
    public List<Class<?>> getEntityTypes() {
        return theEntityTypes;
    }

    /**
     * @param dissecter
     *            The dissecter to understand data types in the data to be migrated
     * @return This migrator, for chaining
     */
    public EntityMigration setDissecter(TypeSetDissecter dissecter) {
        theDissecter = dissecter;
        return this;
    }

	/**
	 * @param persistence
	 *            The persistence scheme to use for entity data
	 * @return This migrator, for chaining
	 */
	public EntityMigration setPersistence(EntitySetPersistence persistence) {
		thePersistence = persistence;
		return this;
	}

    /**
     * @param typeGetter
     *            The type getter to allow injection of types not accessible here
     * @return This migrator, for chaining
     */
    public EntityMigration setTypeGetter(TypeGetter typeGetter) {
        theTypeGetter = typeGetter;
        return this;
    }

    /**
     * @param entityTypes
     *            The entity types to allow migration for
     * @return This migrator, for chaining
     */
    public EntityMigration addEntityTypes(Class<?>... entityTypes) {
        return addEntityTypes(Arrays.asList(entityTypes));
    }

    /**
     * @param entityTypes
     *            The entity types to allow migration for
     * @return This migrator, for chaining
     */
    public EntityMigration addEntityTypes(Collection<Class<?>> entityTypes) {
        theEntityTypes.addAll(entityTypes);
        return this;
    }

    /**
     * @param migrationFile
     *            A reader to get the streamed migration file
     * @return This migrator, for chaining
     */
    public EntityMigration setMigrationFile(ErroringSupplier<InputStream, IOException> migrationFile) {
        theMigrationFile = migrationFile;
        return this;
    }

    /** @return The migration file set with {@link #setMigrationFile(ErroringSupplier)} */
    public ErroringSupplier<InputStream, IOException> getMigrationFile() {
        return theMigrationFile;
    }

    /**
     * @param migrationStorage
     *            The storage to tell this migrator which migrations a data set has already experienced and to log new migrations to the
     *            data set
     * @return This migrator, for chaining
     */
    public EntityMigration setMigrationStorage(DataSetMigrationStorage migrationStorage) {
        theMigrationStorage = migrationStorage;
        return this;
    }

    /**
     * @param entityFilter
     *            The filter to use to exclude entities and/or dependencies during {@link #extract(Function, Consumer, Consumer) extraction}
     *            /{@link #addEntity(Object...) addition}
     * @return This migrator, for chaining
     */
    public EntityMigration filter(Predicate<Object> entityFilter) {
        theEntityFilter = entityFilter;
        return this;
    }

    /**
     * Validates the entity classes set added to this migrator
     * 
     * @return This migrator, for chaining
     */
    public EntityMigration validateEntityClasses() {
        String[] errors = getEntityErrors();
        if (errors != null && errors.length > 0) {
			throw new IllegalStateException("Set of entity types is not valid:\n" + join(errors));
		}
        return this;
    }

    /** @return Error messages that resulted from config entities not being valid */
    public String[] getEntityErrors() {
        if (theEntityTypes.isEmpty()) {
			throw new IllegalStateException("No entity types have been added.  Use addEntityTypes(Collection).");
		}
        if (theDissecter == null) {
			throw new IllegalStateException("No dissecter set.  Use setDissecter(TypeSetDissecter).");
		}

        List<String> ret = new ArrayList<>();
        for (Class<?> entityClass : theEntityTypes) {
			validateEntityClass(entityClass, ret);
		}
        return ret.toArray(new String[ret.size()]);
    }

    /**
     * @param entities
     *            The entities to add for export/migration
     * @return This migrator, for chaining
     */
    public EntityMigration addEntity(Object... entities) {
        validateEntityClasses();

        if (theRealEntities == null) {
			theRealEntities = new EntitySet();
		}
        LinkedList<Object> path = new LinkedList<>();
        for (Object entity : entities) {
			addEntityWithDepends(path, entity, entity.getClass());
		}

        return this;
    }

    /**
     * Generates version support from the entity classes and migration file
     * 
     * @return This migrator, for chaining
     */
    public EntityMigration genVersionFromClasses() {
        validateEntityClasses();
        theVersion = new EntityVersionSupport(getDataSetTags());
        EntityTypeSet types = EntityTypeSet.createTypesForClasses(theEntityTypes, theDissecter);
        theVersion = new EntityVersionSupport(types, getDataSetTags());
        return this;
    }

    /**
     * @param entityGetter
     *            The function to supply all the entities for migration
     * @param inProgressMonitor
     *            Notified when extraction begins for a type
     * @param finishedMonitor
     *            Notified when extraction finishes for a type
     * @return This migrator, for chaining
     */
    public EntityMigration extract(Function<Class<?>, ? extends Iterable<?>> entityGetter, Consumer<Class<?>> inProgressMonitor,
            Consumer<Class<?>> finishedMonitor) {
        validateEntityClasses();

        if (theRealEntities == null) {
			theRealEntities = new EntitySet();
		}
        LinkedList<Object> path = new LinkedList<>();
        for (Class<?> entityClass : theEntityTypes) {
            theRealEntities.addClass(entityClass);
            if (inProgressMonitor != null) {
				inProgressMonitor.accept(entityClass);
			}
            for (Object entity : entityGetter.apply(entityClass))
			 {
				addEntityWithDepends(path, entity, entityClass); // Adds dependencies as well
			}
            if (finishedMonitor != null) {
				finishedMonitor.accept(entityClass);
			}
        }
        return this;
    }

    /**
     * @param reader
     *            The serialized persistence reader to use to save the generic entity data
     * @return This migrator, for chaining
     */
    public EntityMigration parse(HierarchicalResourceReader reader) {
        validateEntityClasses();

        try {
            theVersion = readVersionSupport(new InputStreamReader(reader.readResource("Entity Versions.xml")), theTypeGetter,
                    getDataSetTags());
        } catch (IOException e) {
            System.err.println("Could not read version file");
            e.printStackTrace();
            return null;
        }

		GenericEntitySet entitySet = new SimpleGenericEntitySet(theVersion.getCurrentTypeSet().clone());
		EntitySetPersister persister = new EntitySetPersister(thePersistence);
        boolean success = persister.read(entitySet, reader);
        if (!success) {
			System.err.println("Parsing of entity XML files was not fully successful.  See above errors for details.");
		}

        theGenericEntities = entitySet;
        return this;
    }

    /**
     * Takes real entity data populated with {@link #extract(Function, Consumer, Consumer)} and exports it to generic entity data,
     * retrievable with {@link #getGenericEntities()}.
     * 
     * @return This migrator, for chaining
     */
    public EntityMigration exportGenericEntities() {
        if (theRealEntities == null) {
			throw new IllegalStateException("Entity data must be extracted before importing to real data. Use extract(Function).");
		}

        EntitySetConverter converter = new EntitySetConverter(theDissecter, theEntityTypes).setFilter(theEntityFilter);
		theGenericEntities = converter.exportEntities(theRealEntities, SimpleGenericEntitySet::new);
        return this;
    }

    /**
     * Takes generic entity data (populated with {@link #parse(HierarchicalResourceReader)} or {@link #exportGenericEntities()}) and imports
     * it as real entity data.
     * 
     * @param withMappedCollections
     *            Whether to populate mapped entity data (unnecessary for hibernate persistence, for example)
     * @param withIds
     *            Whether to set the IDs on the entities. This has implications for hibernate.
     * @return This migrator, for chaining
     */
    public EntityMigration importRealEntities(boolean withMappedCollections, boolean withIds) {
        if (theGenericEntities == null) {
			throw new IllegalStateException("Generic entity data must be created before importing to real data");
		}
        if (theMigrationFile == null) {
			throw new IllegalStateException(
                    "The migration file must be set before importing real entity data from generic entities.  Use setMigrationFile().");
		}
        if (theMigrationStorage == null) {
			throw new IllegalStateException(
                    "The migration storage must be set before importing real entity data from generic entities.  Use setMigrationStorage().");
		}
        validateEntityClasses();

        for (MigrationSet migration : theMigrationStorage.getLoggedMigrations(theGenericEntities)) {
			theVersion.addMigrationSet(migration);
		}

        MigrationSet[] newMigrations = updateVersionSupport();
        populateMappedData(theGenericEntities);
        // Migrate the data
        for (MigrationSet migration : newMigrations) {
            System.out.println("Migrating data set with: " + migration);
            theGenericEntities.migrate(migration, theDissecter);
            theMigrationStorage.logMigration(theGenericEntities, migration);
        }

        for (Map.Entry<Class<?>, EntityType> entry : theVersion.getCurrentTypeSet().getEntityMappings()) {
			theGenericEntities.getTypes().map(entry.getKey(), entry.getValue());
		}
        for (Map.Entry<Class<? extends Enum<?>>, EnumType> entry : theVersion.getCurrentTypeSet().getEnumMappings()) {
			theGenericEntities.getTypes().map(entry.getKey(), entry.getValue());
		}

        EntitySetConverter converter = new EntitySetConverter(theDissecter, theEntityTypes);
        theRealEntities = converter.importEntities(theGenericEntities, withMappedCollections, withIds);
        return this;
    }

    /**
     * Takes generic entity data (populated with {@link #parse(HierarchicalResourceReader)} or {@link #exportGenericEntities()}) and writes
     * it to XML
     * 
     * @param writer
     *            The writer to write the data to
     * @param inProgressMonitor
     *            Notified when persistence begins for a type
     * @param finishedMonitor
     *            Notified when persistence finishes for a type
     * @return This migrator, for chaining
     * @throws IOException
     *             If an error occurs writing the data
     */
    public EntityMigration saveGenericEntityData(HierarchicalResourceWriter writer, Consumer<EntityType> inProgressMonitor,
            Consumer<EntityType> finishedMonitor) throws IOException {
        if (theGenericEntities == null) {
			throw new IllegalStateException("Generic entity data must be created before attempting to save it");
		}

		EntitySetPersister persister = new EntitySetPersister(thePersistence);
        try {
            writeVersion(theGenericEntities.getTypes(), new OutputStreamWriter(writer.writeResource("Entity Versions.xml")));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Could not save types", e);
        }
        if (persister.save(theGenericEntities, writer, inProgressMonitor, finishedMonitor)) {
			System.out.println("\nExported entities saved successfully");
		} else {
			System.out.println("\nEntity export was unsuccessful. See above errors.");
		}
        return this;
    }

    /**
     * Creates a full copy of the entity set in this migrator (populated elsewhere)
     * 
     * @param reuse
     *            A function that instructs this method which entities to re-use and not copy
     * @return The copied entity set, stored by the existing entity set's IDs (the IDs of the entities in the copy are not populated)
     */
    public EntityMap<Object> copy(Predicate<Object> reuse) {
        return new EntitySetConverter(theDissecter, theEntityTypes).setFilter(theEntityFilter).duplicate(theRealEntities,
                type -> (ValueDissecter) theDissecter.getDissecter(type).dissect(type, null),
                reuse == null ? null : value -> reuse.test(value) ? value : null, type -> theEntityTypes.contains(type), true, false);
    }

    /**
     * @param versionFile
     *            The version file reader to read the version support data from
     * @param typeGetter
     *            The type getter to allow injection of types not accessible here
     * @param dataSetTags
     *            The tags describing this data set
     * @return The version support configured in the given file
     */
    public static EntityVersionSupport readVersionSupport(Reader versionFile, TypeGetter typeGetter, String... dataSetTags) {
        try {
            return new EntityVersionSupport(versionFile, typeGetter, dataSetTags);
        } catch (IOException | JDOMException e) {
            throw new IllegalStateException("Could not read version support file", e);
        }
    }

    /**
     * @param types
     *            The type set to export to file
     * @param writer
     *            The writer to write the version file to
     */
    public static void writeVersion(EntityTypeSet types, Writer writer) {
        try {
            types.save(writer);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write version support file", e);
        }
    }

    /**
     * Compares the given version support with the given entity classes and, if possible, migrates the version support to be consistent with
     * the entity types.
     *
     * @return The new migrations to apply to the entities
     */
    private MigrationSet[] updateVersionSupport() {
        ArrayList<MigrationSet> ret = new ArrayList<>();
        try (InputStream in = theMigrationFile.get()) {
			MigratorFactory factory = new MigratorFactory(theTypeGetter);
            Element pendingRoot = new SAXBuilder().build(in).getRootElement();
            for (Element migSetEl : pendingRoot.getChildren("migration")) {
				MigrationSet migSet = theVersion.addMigrationSet(migSetEl, factory);
                if (migSet != null && migSet.shouldApply(theVersion)) {
					ret.add(migSet);
				}
            }
        } catch (Exception e) {
            throw new IllegalStateException("ERROR: Bad Migrations.xml file", e);
        }

        StringBuilder diffStr = new StringBuilder();
        Set<String> entityTypes = new HashSet<>();
        Set<String> enumTypes = new HashSet<>();

        String persistedVersionName = "XML data";
        String codeVersionName = "entity code";

        List<Object> diffs=new LinkedList<>();
        EntityTypeSet classVersion = EntityTypeSet.createTypesForClasses(theEntityTypes, theDissecter);
        for (EntityType classType : classVersion) {
            Class<?> clazz = classVersion.getMappedEntity(classType);
            EntityType versionType = theVersion.getCurrentTypeSet().getEntityType(classType.getName());
            EntityDifference diff = new EntityDifference(versionType, classType);
            if (versionType != null) {
                theVersion.getCurrentTypeSet().map(clazz, versionType);
                entityTypes.add(versionType.getName());
            }
            if (diff.isDifferent()){
            	diffs.add(diff);
                diffStr.append('\n').append(diff.toString(persistedVersionName, codeVersionName));
            }
        }
        for (EnumType enumType : classVersion.enums()) {
            Class<? extends Enum<?>> clazz = classVersion.getMappedEnum(enumType);
            EnumType versionType = theVersion.getCurrentTypeSet().getEnumType(enumType.getName());
            EnumDifference diff = new EnumDifference(versionType, enumType);
            if (versionType != null) {
                theVersion.getCurrentTypeSet().map(clazz, versionType);
                enumTypes.add(versionType.getName());
                // For enums, don't print the difference if the persisted data is missing the enum.
                // No harm in having extra enums around. Doesn't affect the data.
                if (diff.isDifferent()){
                	diffs.add(diff);
                    diffStr.append('\n').append(diff.toString(persistedVersionName, codeVersionName));
                }
            }
        }

        for (EntityType type : theVersion.getCurrentTypeSet())
		 {
			if (!entityTypes.contains(type.getName())){
            	EntityDifference diff=new EntityDifference(type, null);
            	diffs.add(diff);
                diffStr.append('\n').append(diff.toString(persistedVersionName, codeVersionName));
            }
        // Don't print enums that are missing in code. The references to the enums may have been migrated away.
		}
        
        if (!diffs.isEmpty()) {
            diffStr.delete(0, 1);
            throw new IllegalStateException("This utility does not support migration creation.  Enter migrations in XML first:\n" + diffStr);
        }
        return ret.toArray(new MigrationSet[ret.size()]);
    }

    /**
     * @param entities
     *            The entity set to populate the mapping values and collections of
     */
	public static void populateMappedData(GenericEntitySet entities) {
        System.out.println("\nLinking up mapped entities");
        for (EntityType type : entities.getTypes()) {
			for (EntityField field : type) {
                if (field.getDeclaringType() != type || field.getMappingField() == null) {
					continue;
				}
                EntityField refField = PersistenceUtils.getMappedField(field, entities.getTypes());
                System.out.println("\tLinking " + refField.entity + " instances into " + field.entity + "." + field.getName());
                if (field.getType() instanceof EntityType) {
                    boolean allFound = true;
					for (GenericEntity entity : entities.queryAll(type.getName())) {
                        boolean found = false;
						for (GenericEntity refEntity : entities.queryAll(refField.entity.getName())) {
							if (refEntity.get(refField.getName()) == entity) {
                                entity.set(field.getName(), refEntity);
                                found = true;
                                break;
                            }
						}
                        if (!found) {
							allFound = false;
						}
                    }
                    if (!allFound && !field.isNullable()) {
						System.err.println("Some entities referenced by " + field + "'s mapping field " + field.getMappingField()
                                + " could not be linked");
					}
                } else {
					for (GenericEntity entity : entities.queryAll(type.getName())) {
                        Collection<GenericEntity> collection = (Collection<GenericEntity>) entity.get(field.getName());
                        if (collection == null) {
                            Type raw = ((ParameterizedType) field.getType()).getRawType();
                            if (!(raw instanceof Class)) {
								throw new IllegalStateException("Collection type for field " + field + " could not be instantiated");
							}
                            Class<?> rawClass = (Class<?>) raw;
                            if ((rawClass.getModifiers() & Modifier.ABSTRACT) == 0) {
								try {
                                    collection = (Collection<GenericEntity>) rawClass.newInstance();
                                } catch (Exception e) {
                                    throw new IllegalStateException("Collection type for field " + field + " could not be instantiated", e);
                                }
							} else if (SortedSet.class.isAssignableFrom(rawClass)) {
								collection = new TreeSet<>();
							} else if (Set.class.isAssignableFrom(rawClass)) {
								collection = new LinkedHashSet<>();
							} else if (List.class.isAssignableFrom(rawClass)) {
								collection = new ArrayList<>();
							} else if (rawClass.isAssignableFrom(ArrayList.class)) {
								collection = new ArrayList<>();
							} else {
								throw new IllegalStateException("Collection type for field " + field + " could not be instantiated");
							}
                            entity.set(field.getName(), collection);
                        }

						for (GenericEntity refEntity : entities.queryAll(refField.entity.getName())) {
							if (refEntity.get(refField.getName()) == entity) {
								collection.add(refEntity);
							}
						}

                        if (collection instanceof List && field.getSorting().length > 0) {
							Collections.sort((List<GenericEntity>) collection, new PersistenceUtils.OrderedFieldSorter(field));
						}
                    }
				}
            }
		}
    }

    private void validateEntityClass(Class<?> entityClass, List<String> errors) {
        if (entityClass.getAnnotation(Entity.class) == null && entityClass.getAnnotation(MappedSuperclass.class) == null) {
			errors.add(entityClass.getName() + " is not annotated with @Entity or @MappedSuperclass");
		}
        ValueDissecter dissecter = (ValueDissecter) theDissecter.getDissecter(entityClass).dissect(entityClass, null);
        TypedField[] fields = dissecter.getFields();

        boolean hasId = false;
        for (TypedField field : fields) {
			if (field.id) {
                if (hasId) {
					throw new IllegalStateException("Multiple ID fields returned for type " + entityClass.getName());
				}
                hasId = true;
            }
		}
        if (!hasId) {
			throw new IllegalStateException("No id field on type definition for " + entityClass.getName());
		}

        LinkedList<TypedField> fieldPath = new LinkedList<>();
        for (TypedField field : fields) {
            fieldPath.add(field);
            checkFieldType(fieldPath, field.type, errors);
            fieldPath.removeLast();
        }
    }

    private void checkFieldType(LinkedList<TypedField> fieldPath, Type type, List<String> errors) {
        for (TypedField field : fieldPath)
		 {
			if (field.declaringType == type)
			 {
				return; // No cycles
			}
		}
        Class<?> raw = PersistenceUtils.getRawType(type);
        if (Enum.class.isAssignableFrom(raw)) {
			return;
		}
        if (theDissecter.isSimple(raw)) {
			return;
		}
        if (theEntityTypes.contains(raw)) {
			return;
		}
        DissecterGenerator gen = theDissecter.getDissecter(raw);
        if (gen == null) {
			throw new IllegalStateException("Unrecognized type: " + PersistenceUtils.toString(type));
		}
        Dissecter dissecter = gen.dissect(type, null);
        if (dissecter instanceof ValueDissecter) {
            TypedField[] fields = ((ValueDissecter) dissecter).getFields();

            for (TypedField field : fields) {
                fieldPath.add(field);
                checkFieldType(fieldPath, field.type, errors);
                fieldPath.removeLast();
            }
            return;
        } else if (dissecter instanceof CollectionDissecter) {
            checkFieldType(fieldPath, ((CollectionDissecter) dissecter).getComponentType(), errors);
            return;
        }
        errors.add("Unrecognized type " + PersistenceUtils.toString(type) + ": " + fieldPath);
    }

    private static String join(String[] errors) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < errors.length; i++) {
            if (i > 0) {
				ret.append('\n');
			}
            ret.append(errors[i]);
        }
        return ret.toString();
    }

    private void addEntityWithDepends(LinkedList<Object> path, Object entity, Type type) {
        if (entity == null) {
			return;
		}
        if (path.contains(entity)) {
			return;
		}
        if (theEntityTypes.contains(entity.getClass()) && theEntityFilter != null && !theEntityFilter.test(entity)) {
			return;
		}
        path.add(entity);
        try {
            Class<?> raw = PersistenceUtils.getRawType(type);
            if (Enum.class.isAssignableFrom(raw)) {
				return;
			}
            if (theDissecter.isSimple(raw)) {
				return;
			}
            if (theEntityTypes.contains(raw) && theRealEntities.add(entity) != null) {
				return;
			}
            DissecterGenerator gen = theDissecter.getDissecter(raw);
            if (gen == null) {
				throw new IllegalStateException("Unrecognized type: " + PersistenceUtils.toString(type));
			}
            String subType = gen.getSubType(entity.getClass());
            Dissecter dissecter = gen.dissect(type, subType);
            if (dissecter instanceof ValueDissecter) {
                ValueDissecter vd = (ValueDissecter) dissecter;
                TypedField[] fields = vd.getFields();
                for (TypedField field : fields) {
					addEntityWithDepends(path, vd.getFieldValue(entity, field.name), field.type);
				}
            } else if (dissecter instanceof CollectionDissecter) {
                CollectionDissecter cd = (CollectionDissecter) dissecter;
                Type componentType = cd.getComponentType();
                for (Object element : cd.getElements(entity)) {
					addEntityWithDepends(path, element, componentType);
				}
            } else {
				throw new IllegalStateException("Unrecognized type " + PersistenceUtils.toString(type));
			}
        } finally {
            path.removeLast();
        }
    }
}
