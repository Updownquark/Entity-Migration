package org.migration.migrators;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/** This migrator replaces entities with new entities whose type is a super-type of the original entities' type */
public class AscendToSuperType extends JavaMigrator {
    private String theSuperType;

	public String getSuperType() {
		return theSuperType;
	}

    @Override
    public JavaMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        super.init(entity, config, migration, factory);
        theSuperType = config.getAttributeValue("super-type");
        return this;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        EntityType superType = allEntities.getCurrentTypes().getEntityType(theSuperType);
        if (superType == null)
            throw new IllegalArgumentException("No such type " + theSuperType);
        if (!superType.isAssignableFrom(oldVersionEntity.getCurrentType()))
            throw new IllegalArgumentException(
                    "Type " + oldVersionEntity.getCurrentType().getName() + " is not a sub-type of " + theSuperType);

        GenericEntity newEntity = allEntities.addEntity(theSuperType);
        /* This is relying on the fact that setting the ID of an entity does not check whether an entity of a sub-type with the same ID
         * exists.  If the GenericEntitySet.idChanged() method checked this, this would fail.  In fact, this operation is safe because the
         * replacement operation will immediately delete the sub-typed entity that would cause the collision.
         * If that constraint is ever checked, we'll have to do some fancy magic to get around it here. */
        for (EntityField field : superType)
            newEntity.set(field.getName(), oldVersionEntity.get(field.getName()));
        return newEntity; // Replace with the new entity
    }
}
