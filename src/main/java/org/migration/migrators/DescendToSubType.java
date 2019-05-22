package org.migration.migrators;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/** This migrator replaces entities with new entities whose type is a sub-type of the original entities' type */
public class DescendToSubType extends JavaMigrator {
    private String theSubType;

	public String getSubType() {
		return theSubType;
	}

	@Override
    public JavaMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        super.init(entity, config, migration, factory);
        theSubType = config.getAttributeValue("sub-type");
        return this;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        EntityType subType = allEntities.getCurrentTypes().getEntityType(theSubType);
        if (subType == null)
            throw new IllegalArgumentException("No such type " + theSubType);
        if (!oldVersionEntity.getCurrentType().isAssignableFrom(subType))
            throw new IllegalArgumentException(
                    "Type " + oldVersionEntity.getCurrentType().getName() + " is not a super-type of " + theSubType);

        GenericEntity newEntity = allEntities.addEntity(theSubType);
        /* This is relying on the fact that setting the ID of an entity does not check whether an entity of a super-type with the same ID
         * exists.  If the GenericEntitySet.idChanged() method checked this, this would fail.  In fact, this operation is safe because the
         * replacement operation will immediately delete the super-typed entity that would cause the collision.
         * If that constraint is ever checked, we'll have to do some fancy magic to get around it here. */
		for (EntityField field : getEntity())
            newEntity.set(field.getName(), oldVersionEntity.get(field.getName()));
        return newEntity; // Replace with the new entity
    }
}
