package org.migration.migrators;

import java.util.List;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/** Sets the value of a particular field of an entity */
public abstract class FieldValueMigrator implements CustomMigrator {
    private String theEntityName;

    /** The name of the field to set the value of */
	private String theFieldPathStr;

    /** Whether to set the value for the field if the field's value is already set for an entity instance */
    private boolean isForced;

	private List<EntityField> theFieldPath;

    /** Default constructor. Fields will be set with {@link #init(String, Element, MigrationSet, MigratorFactory)} */
    public FieldValueMigrator() {
    }

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param field
     *            The name of the field to set the value of
     * @param forced
     *            Whether to set the value for the field if the field's value is already set for an entity instance
     */
    public FieldValueMigrator(String entity, String field, boolean forced) {
        theEntityName = entity;
		theFieldPathStr = field;
        isForced = forced;
    }

    @Override
    public CustomMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        theEntityName = entity;
		theFieldPathStr = config.getAttributeValue("field");
        isForced = "true".equalsIgnoreCase(config.getAttributeValue("force"));
        return this;
    }

    @Override
	public CustomMigrator init(EntityTypeSet entities, TypeSetDissecter dissecter) {
		CustomMigrator.super.init(entities, dissecter);
		theFieldPath = ValuePullMigrator.getFieldPath(entities.getEntityType(theEntityName), theFieldPathStr);
		return this;
	}

	@Override
    public String getEntityName() {
        return theEntityName;
    }

    /** @return The name of the field that this migrator sets */
    public String getField() {
		return theFieldPathStr;
    }

    /** @return Whether the value is forced on entities that already have a value in the field */
    public boolean isForced() {
        return isForced;
    }

    /**
     * @param oldVersionEntity
     *            The old version of an entity
     * @param field
     *            The field to get the new value for
     * @param allEntities
     *            The set of all entities, some migrated, some not
     * @param dissecter
     *            The dissecter to understand data types
     * @return The new value for this migrator's field for the given entity
     */
    protected abstract Object getFieldValue(GenericEntity oldVersionEntity, EntityField field, GenericEntitySet allEntities,
            TypeSetDissecter dissecter);

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
		GenericEntity container = (GenericEntity) ValuePullMigrator.evaluateFieldPath(oldVersionEntity, theFieldPath, true);
		EntityField lastField = theFieldPath.get(theFieldPath.size() - 1);
		if (isForced || container.get(lastField.getName()) == null) {
			container.set(lastField.getName(), getFieldValue(container, lastField, allEntities, dissecter));
		}
        return oldVersionEntity;
    }

    @Override
    public Element serialize() {
		return new Element("set").setAttribute("field", theFieldPathStr).setAttribute("force", "" + isForced);
    }

    @Override
    public String toString() {
		return (isForced ? "Force" : "Set") + " " + getEntityName() + "." + theFieldPathStr;
    }
}
