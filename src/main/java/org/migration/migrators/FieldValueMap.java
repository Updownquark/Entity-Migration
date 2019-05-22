package org.migration.migrators;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.SimpleFormat;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EnumType;
import org.migration.generic.EnumValue;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/** Allows mapping the values of a simple field to different values */
public class FieldValueMap extends JavaMigrator {
    private final Map<String, String> theMappedValues = new LinkedHashMap<>();
    private String theField;

    @Override
    public JavaMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        theField = config.getAttributeValue("field");
        for (Element mapEl : config.getChildren("map")) {
            theMappedValues.put(mapEl.getAttributeValue("from"), mapEl.getAttributeValue("to"));
        }
        return super.init(entity, config, migration, factory);
    }

    /** @return The name of the field to modify */
    public String getField() {
        return theField;
    }

    /** @return The map of values to switch out */
    public Map<String, String> getValueMap() {
        return theMappedValues;
    }

    @Override
    public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
        EntityField field = oldVersionEntity.getCurrentType().getField(theField);
        if (field == null) {
			throw new IllegalStateException("No such field " + oldVersionEntity.getCurrentType() + "." + theField);
		}
        if (field.getType() instanceof EntityType) {
			throw new IllegalStateException("Cannot map entity fields: " + field);
		}

		if (field.getType() instanceof Class) {
			SimpleFormat format = dissecter.getFormat((Class<?>) field.getType());
			String oldValueTxt = format.format(oldVersionEntity.get(theField));
			String newValueTxt = theMappedValues.get(oldValueTxt);
			if (newValueTxt != null) {
				oldVersionEntity.set(theField, format.parse((Class<?>) field.getType(), newValueTxt));
			}
		} else if (field.getType() instanceof EnumType) {
			EnumValue oldValue = oldVersionEntity.getEnum(theField);
			if (oldValue != null) {
				String newValueTxt = theMappedValues.get(oldValue.getName());
				if (newValueTxt != null) {
					EnumValue newValue = ((EnumType) field.getType()).getValue(newValueTxt);
					if (newValue == null) {
						throw new IllegalStateException("No such " + field.getType() + " value " + newValueTxt);
					}
					oldVersionEntity.set(theField, newValue);
				}
			}
		} else {
			throw new IllegalStateException("Only simple-typed fields may be mapped: " + field);
		}
        return oldVersionEntity;
    }
}
