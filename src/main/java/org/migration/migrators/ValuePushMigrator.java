package org.migration.migrators;

import java.util.Arrays;
import java.util.List;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/** Moves a value from an entity to one of an entity's related entities */
public class ValuePushMigrator extends JavaMigrator {
	private String theFieldName;
	private String theFieldPathStr;

	private EntityField theField;
	private List<EntityField> theFieldPath;

    @Override
    public JavaMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        super.init(entity, config, migration, factory);
		theFieldName = config.getAttributeValue("field");
		theFieldPathStr = config.getTextNormalize();
        return this;
    }

    @Override
	public JavaMigrator init(EntityTypeSet entities, TypeSetDissecter dissecter) {
		super.init(entities, dissecter);
		theField = getEntity().getField(theFieldName);
		if (theField == null)
			throw new IllegalArgumentException("Unrecognized field " + getEntity() + "." + theFieldName);
		theFieldPath = ValuePullMigrator.getFieldPath(getEntity(), theFieldPathStr);
		EntityField pathTerminus = theFieldPath.get(theFieldPath.size() - 1);
        ValuePullMigrator.checkPotentialAssignment(theField, pathTerminus);
		return this;
	}

	@Override
	public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
		ValuePullMigrator.assign(oldVersionEntity, Arrays.asList(theField), theFieldPath, allEntities, dissecter);
		return oldVersionEntity;
    }
}
