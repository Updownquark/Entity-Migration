package org.migration.migrators;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EnumType;
import org.migration.generic.EnumValue;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/**
 * Assigns an enum field's name to another field or takes an enum name from one field and puts the corresponding enum value in another field
 */
public class EnumStringMigrator extends JavaMigrator {
    private String theFromField;
    private String theToField;

    @Override
    public JavaMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        super.init(entity, config, migration, factory);
        theFromField = config.getAttributeValue("from");
        theToField = config.getAttributeValue("to");
        return this;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        EntityField fromField = oldVersionEntity.getCurrentType().getField(theFromField);
        if (fromField == null)
            throw new IllegalStateException("No such field " + getEntityName() + "." + theFromField);
        EntityField toField = oldVersionEntity.getCurrentType().getField(theToField);
        if (toField == null)
            throw new IllegalStateException("No such field " + getEntityName() + "." + theToField);
        if (String.class == fromField.getType()) {
            if (!(toField.getType() instanceof EnumType))
                throw new IllegalStateException("If " + getEntityName() + "." + theFromField + " is a String, " + getEntityName() + "."
                        + theToField + " must be an enum");
            oldVersionEntity.set(theToField, parseEnumValue((EnumType) toField.getType(), (String) oldVersionEntity.get(theFromField)));
        } else if (String.class == toField.getType()) {
            if (!(fromField.getType() instanceof EnumType))
                throw new IllegalStateException("If " + getEntityName() + "." + theToField + " is a String, " + getEntityName() + "."
                        + theFromField + " must be an enum");
            EnumValue fieldVal = (EnumValue) oldVersionEntity.get(theFromField);
            oldVersionEntity.set(theToField, fieldVal == null ? null : fieldVal.getName());
        } else {
            throw new IllegalStateException(
                    getEntityName() + "." + theFromField + " or " + getEntityName() + "." + theToField + " must be of type String");
        }
        return oldVersionEntity;
    }

    private EnumValue parseEnumValue(EnumType type, String name) {
        if (name == null)
            return null;
        EnumValue value = type.getValue(name);
        if (value == null)
            throw new IllegalStateException("No such enum value " + type + "." + name);
        return value;
    }

    @Override
    public String toString() {
        return theFromField + "->" + theToField + " by name";
    }
}
