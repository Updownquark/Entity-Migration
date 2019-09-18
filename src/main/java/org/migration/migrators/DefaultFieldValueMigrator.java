package org.migration.migrators;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

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
import org.migration.util.PersistenceUtils;

/** Represents the setting of a field value */
public class DefaultFieldValueMigrator extends FieldValueMigrator {
    /** The serialized value to set for the field */
    private String theValue;

    /** Default constructor. Field will be set with {@link #init(String, Element, MigrationSet, MigratorFactory)} */
    public DefaultFieldValueMigrator() {
    }

    /**
     * @param entity
     *            The name of the entity that this migrator operates on
     * @param aField
     *            The name of the field to set the value of
     * @param aValue
     *            The serialized value to set for the field
     * @param isForced
     *            Whether to set the value for the field if the field's value is already set for an entity instance
     */
    public DefaultFieldValueMigrator(String entity, String aField, String aValue, boolean isForced) {
        super(entity, aField, isForced);
        theValue = aValue;
    }

    @Override
    public CustomMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        super.init(entity, config, migration, factory);
        theValue = config.getTextTrim();
        return this;
    }

    /** @return The string representation of the value to set for the field */
    public String getValue() {
        return theValue;
    }

    @Override
	protected Object getFieldValue(GenericEntity oldVersionEntity, EntityField field, GenericEntitySet allEntities,
		TypeSetDissecter dissecter) {
		return createDefaultValue(field.getType(), allEntities, dissecter, theValue);
    }

    @Override
    public Element serialize() {
        return super.serialize().setText(theValue);
    }

    @Override
    public String toString() {
        return super.toString() + " to " + theValue;
    }

	public static Object createDefaultValue(Type type, GenericEntitySet allEntities, TypeSetDissecter dissecter, String value) {
		if (type instanceof EntityType) {
			if (allEntities == null) {
				throw new IllegalStateException("Cannot parse values for entity types in this context");
			}
			if (value == null || value.length() == 0) {
				throw new IllegalStateException("Identity must be specified for entity types");
			}
			if ("new".equals(value)) {
				return allEntities.addEntity(((EntityType) type).getName());
			}
			EntityField idField = ((EntityType) type).getIdField();
			SimpleFormat idFormat = dissecter.getFormat((Class<?>) idField.getType());
			Object idValue;
			try {
				idValue = idFormat.parse((Class<?>) idField.getType(), value);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Could not parse " + idField + " from " + value);
			}
			GenericEntity ret = allEntities.queryById((EntityType) type, idValue);
			if (ret == null) {
				throw new IllegalStateException("No such " + type + " with " + idField.getName() + " " + idValue);
			}
			return ret;
		} else if (type instanceof EnumType) {
			if (value == null || value.length() == 0) {
				throw new IllegalStateException("Value must be specified for enum types");
			}
			EnumValue enumValue = ((EnumType) type).getValue(value);
			if (enumValue == null) {
				throw new IllegalStateException("No such enum value " + type + "." + value);
			}
			return enumValue;
		} else if (type instanceof Class) {
			if (value == null || value.length() == 0) {
				throw new IllegalStateException("Value must be specified for type " + ((Class<?>) type).getName());
			}
			Class<?> clazz = (Class<?>) type;
			SimpleFormat format = dissecter.getFormat(clazz);
			if (format != null) {
				return format.parse(clazz, value);
			}
		}
		Class<?> raw = PersistenceUtils.getRawType(type);
		if (Collection.class.isAssignableFrom(raw)) {
			if (value != null && value.length() != 0) {
				throw new IllegalStateException("Cannot parse collection instances--can only instantiate empty ones.  Leave text blank.");
			}
			if (SortedSet.class.equals(raw)) {
				return new TreeSet<>();
			} else if (Set.class.equals(raw)) {
				return new LinkedHashSet<>();
			} else if (List.class.equals(raw)) {
				return new ArrayList<>();
			} else if (Collection.class.equals(raw)) {
				return new ArrayList<>();
			} else {
				throw new IllegalStateException("Unrecognized collection type: " + raw.getName());
			}
		} else if (Map.class.isAssignableFrom(raw)) {
			if (value != null && value.length() != 0) {
				throw new IllegalStateException("Cannot parse map instances--can only instantiate empty ones.  Leave text blank.");
			}
			if (SortedMap.class.equals(raw)) {
				return new TreeMap<>();
			} else if (Map.class.equals(raw)) {
				return new LinkedHashMap<>();
			} else {
				throw new IllegalStateException("Unrecognized collection type: " + raw.getName());
			}
		} else {
			throw new IllegalStateException("Default values not supported for fields of type " + PersistenceUtils.toString(type));
		}
	}
}
