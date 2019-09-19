package org.migration.generic;

import java.lang.reflect.Type;
import java.util.Map;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeGetter;
import org.migration.migrators.AscendToSuperType;
import org.migration.migrators.CustomMigrator;
import org.migration.migrators.DefaultFieldValueMigrator;
import org.migration.migrators.DescendToSubType;
import org.migration.migrators.EntityCreatedMigrator;
import org.migration.migrators.EntityMigrator;
import org.migration.migrators.EntityRemovedMigrator;
import org.migration.migrators.EntityRenameMigrator;
import org.migration.migrators.EnumCreatedMigrator;
import org.migration.migrators.EnumRemovedMigrator;
import org.migration.migrators.EnumRenameMigrator;
import org.migration.migrators.EnumValueAddedMigrator;
import org.migration.migrators.EnumValueRemovedMigrator;
import org.migration.migrators.EnumValueRenameMigrator;
import org.migration.migrators.FieldAddedMigrator;
import org.migration.migrators.FieldRemovedMigrator;
import org.migration.migrators.FieldRenameMigrator;
import org.migration.migrators.FieldValueMap;
import org.migration.migrators.NullMigrator;
import org.migration.migrators.NullabilityMigrator;
import org.migration.migrators.ReplaceSuperMigrator;
import org.migration.migrators.SwitchMigrator;
import org.migration.migrators.ValuePullMigrator;
import org.migration.migrators.ValuePushMigrator;
import org.migration.util.PersistenceUtils;

/** Serializes migrators and back */
public class MigratorFactory {
	private final TypeGetter theTypeGetter;

	public MigratorFactory(TypeGetter typeGetter) {
		theTypeGetter = typeGetter;
	}

    /**
     * @param migrator
     *            The migrator to serialize
     * @return The XML representation of the migrator
     */
    public Element serialize(EntityMigrator migrator) {
        if (migrator instanceof EntityCreatedMigrator) {
			return new Element("created").addContent(((EntityCreatedMigrator) migrator).entity.toElement());
		} else if (migrator instanceof EntityRemovedMigrator) {
			return new Element("removed").addContent(((EntityRemovedMigrator) migrator).entity.toElement());
		} else if (migrator instanceof EntityRenameMigrator) {
            Element ret = new Element("entity-rename").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("to", ((EntityRenameMigrator) migrator).afterName);
            return ret;
        } else if (migrator instanceof FieldAddedMigrator) {
            FieldAddedMigrator addMig = (FieldAddedMigrator) migrator;
            Element ret = new Element("field-added").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("field", addMig.field);
            ret.setAttribute("type", PersistenceUtils.toString(addMig.type));
            if (addMig.map != null) {
				ret.setAttribute("map", "" + addMig.map);
			}
            if (addMig.sorting.length > 0) {
				ret.setAttribute("sorting", join(addMig.sorting));
			}
            return ret;
        } else if (migrator instanceof FieldRemovedMigrator) {
            FieldRemovedMigrator remMig = (FieldRemovedMigrator) migrator;
            Element ret = new Element("field-removed").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("field", remMig.field);
            ret.setAttribute("type", PersistenceUtils.toString(remMig.type));
            if (remMig.map != null) {
				ret.setAttribute("map", "" + remMig.map);
			}
            if (remMig.sorting.length > 0) {
				ret.setAttribute("sorting", join(remMig.sorting));
			}
            return ret;
        } else if (migrator instanceof FieldRenameMigrator) {
            Element ret = new Element("field-rename").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("from", ((FieldRenameMigrator) migrator).beforeName);
            ret.setAttribute("to", ((FieldRenameMigrator) migrator).afterName);
            return ret;
        } else if (migrator instanceof ReplaceSuperMigrator) {
            Element ret = new Element("replace-super").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("super", ((ReplaceSuperMigrator) migrator).newSuperType.getName());
            return ret;
        } else if (migrator instanceof DefaultFieldValueMigrator) {
            Element ret = new Element("field-value").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("field", ((DefaultFieldValueMigrator) migrator).getField());
            ret.setAttribute("value", ((DefaultFieldValueMigrator) migrator).getValue());
            ret.setAttribute("force", "" + ((DefaultFieldValueMigrator) migrator).isForced());
            return ret;
        } else if (migrator instanceof FieldValueMap) {
            Element ret = new Element("field-map").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("field", ((FieldValueMap) migrator).getField());
            for (Map.Entry<String, String> valueMap : ((FieldValueMap) migrator).getValueMap().entrySet()) {
				ret.addContent(new Element("map").setAttribute("from", valueMap.getKey()).setAttribute("to", valueMap.getValue()));
			}
            return ret;
        } else if (migrator instanceof NullabilityMigrator) {
            Element ret = new Element("nullability").setAttribute("entity", migrator.getEntityName());
            ret.setAttribute("field", ((NullabilityMigrator) migrator).field);
            ret.setAttribute("nullable", "" + ((NullabilityMigrator) migrator).nullable);
            return ret;
        } else if (migrator instanceof NullMigrator) {
			return new Element("null").setAttribute("entity", migrator.getEntityName());
		} else if (migrator instanceof EnumCreatedMigrator) {
            return new Element("created").addContent(((EnumCreatedMigrator) migrator).enumType.toElement());
        } else if (migrator instanceof EnumRemovedMigrator) {
            return new Element("removed").addContent(((EnumRemovedMigrator) migrator).enumType.toElement());
        } else if (migrator instanceof EnumRenameMigrator) {
            Element ret = new Element("enum-rename").setAttribute("enum", migrator.getEntityName());
            ret.setAttribute("to", ((EnumRenameMigrator) migrator).afterName);
            return ret;
        } else if (migrator instanceof EnumValueAddedMigrator) {
            EnumValueAddedMigrator addMig = (EnumValueAddedMigrator) migrator;
            Element ret = new Element("value-added").setAttribute("enum", migrator.getEntityName());
            ret.setAttribute("field", addMig.value);
            return ret;
        } else if (migrator instanceof FieldRemovedMigrator) {
            FieldRemovedMigrator remMig = (FieldRemovedMigrator) migrator;
            Element ret = new Element("value-removed").setAttribute("enum", migrator.getEntityName());
            ret.setAttribute("field", remMig.field);
            return ret;
        } else if (migrator instanceof FieldRenameMigrator) {
            Element ret = new Element("value-rename").setAttribute("enum", migrator.getEntityName());
            ret.setAttribute("from", ((FieldRenameMigrator) migrator).beforeName);
            ret.setAttribute("to", ((FieldRenameMigrator) migrator).afterName);
            return ret;
        } else if (migrator instanceof CustomMigrator) {
            Element ret = new Element("custom").setAttribute("type", migrator.getClass().getName());
            String entity = migrator.getEntityName();
            if (entity != null) {
				ret.setAttribute("entity", entity);
			}
            Element config = ((CustomMigrator) migrator).serialize();
            if (config != null) {
				ret.addContent(config);
			}
            return ret;
        } else {
			throw new IllegalArgumentException("Unrecognized entity migrator implementation: " + migrator.getClass().getName()
                    + ". For custom migrators, implement " + CustomMigrator.class.getName());
		}
    }

    /**
     * @param sorting
     *            The comma-delimited string to split
     * @return The trimmed string values in the comma-delimited joined value
     */
	public static String[] split(String sorting) {
        if (sorting == null || sorting.length() == 0) {
			return new String[0];
		}
        String[] sortCols = sorting.split(",");
        for (int i = 0; i < sortCols.length; i++) {
			sortCols[i] = sortCols[i].trim();
		}
		return sortCols;
    }

    /**
     * @param sorting
     *            The set of values to join
     * @return The set of values, joined in a comma-delimited string
     */
    public static String join(String[] sorting) {
        StringBuilder ret = new StringBuilder();
        for (String sort : sorting) {
			ret.append(',').append(sort);
		}
        ret.delete(0, 1);
        return ret.toString();
    }

    /**
     * @param xml
     *            The XML representation of a migrator
     * @param types
     *            The entity types available
     * @param migration
     *            The migration set that the migrator is a part of
     * @return The deserialized migrator
     */
	public EntityMigrator deserialize(Element xml, EntityTypeSet types, MigrationSet migration) {
        String entityName = xml.getAttributeValue("entity");
        String enumName = xml.getAttributeValue("enum");

        EntityType entity = entityName == null ? null : types.getEntityType(entityName);
        EnumType enumType = enumName == null ? null : types.getEnumType(enumName);

        switch (xml.getName()) {
        case "created":
            if (entityName != null) {
                if (entity != null) {
					throw new IllegalArgumentException("create specified for " + entityName + ", but entity already exists");
				}
                entity = new EntityType(null, entityName);
				entity.populateFields(xml, types, theTypeGetter);
                for (EntityField field : entity) {
					PersistenceUtils.getMappedField(field, types);
				}
                return new EntityCreatedMigrator(entity);
            } else if (enumName != null) {
                if (enumType != null) {
					throw new IllegalArgumentException("create specified for " + enumName + ", but enum already exists");
				}
                enumType = new EnumType(enumName);
                enumType.populateValues(xml);
                return new EnumCreatedMigrator(enumType);
            } else {
				throw new IllegalArgumentException("No entity or enum specified for migration " + xml.getName());
			}
        case "removed":
            if (entityName != null) {
                if (entity == null) {
					throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
				}
                return new EntityRemovedMigrator(entity);
            } else if (enumName != null) {
                if (enumType == null) {
					throw new IllegalArgumentException("No such enum " + enumName + " for migration " + xml.getName());
				}
                return new EnumRemovedMigrator(enumType);
            } else {
				throw new IllegalArgumentException("No entity or enum specified for migration " + xml.getName());
			}
        case "entity-renamed":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            return new EntityRenameMigrator(entityName, xml.getAttributeValue("to"));
        case "enum-renamed":
            if (enumName == null) {
				throw new IllegalArgumentException("No enum specified for migration " + xml.getName());
			}
            if (enumType == null) {
				throw new IllegalArgumentException("No such enum " + enumName + " for migration " + xml.getName());
			}
            return new EnumRenameMigrator(enumName, xml.getAttributeValue("to"));
        case "field-added":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            String fieldName = xml.getAttributeValue("field");
			Type fieldType = PersistenceUtils.getType(xml.getAttributeValue("type"), types, theTypeGetter);
            String map = xml.getAttributeValue("map");
            PersistenceUtils.getMappedField(
				new EntityField(entity, fieldName, fieldType, map, split(xml.getAttributeValue("sorting"))), types);
			return new FieldAddedMigrator(entityName, fieldName, fieldType, map, split(xml.getAttributeValue("sorting")),
				xml.getTextTrim());
        case "value-added":
            if (enumName == null) {
				throw new IllegalArgumentException("No enum specified for migration " + xml.getName());
			}
            if (enumType == null) {
				throw new IllegalArgumentException("No such enum " + enumName + " for migration " + xml.getName());
			}
            String valueName = xml.getAttributeValue("value");
            return new EnumValueAddedMigrator(enumName, valueName);
        case "field-removed":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            fieldName = xml.getAttributeValue("field");
            EntityField field = entity.getField(fieldName);
            if (field == null) {
				throw new IllegalArgumentException("No such field " + entityName + "." + fieldName);
			}
			return new FieldRemovedMigrator(entityName, fieldName, field.getType(), field.getMappingField(), field.getSorting());
        case "value-removed":
            if (enumName == null) {
				throw new IllegalArgumentException("No enum specified for migration " + xml.getName());
			}
            if (enumType == null) {
				throw new IllegalArgumentException("No such enum " + enumName + " for migration " + xml.getName());
			}
            valueName = xml.getAttributeValue("value");
            return new EnumValueRemovedMigrator(enumName, valueName);
        case "field-renamed":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            return new FieldRenameMigrator(entityName, xml.getAttributeValue("from"), xml.getAttributeValue("to"));
        case "value-renamed":
            if (enumName == null) {
				throw new IllegalArgumentException("No enum specified for migration " + xml.getName());
			}
            if (enumType == null) {
				throw new IllegalArgumentException("No such enum " + enumName + " for migration " + xml.getName());
			}
            return new EnumValueRenameMigrator(enumName, xml.getAttributeValue("from"), xml.getAttributeValue("to"));
        case "replace-super":
			return new ReplaceSuperMigrator(types.getEntityType(entityName),
					xml.getAttributeValue("super").equals("none") ? null : types.getEntityType(xml.getAttributeValue("super")));
        case "field-value":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            DefaultFieldValueMigrator dfv = new DefaultFieldValueMigrator(entityName, xml.getAttributeValue("field"),
                    xml.getAttributeValue("value"), "true".equalsIgnoreCase(xml.getAttributeValue("force")));
			dfv.init(entityName, xml, migration, this);
            return dfv;
        case "field-map":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            FieldValueMap fvm = new FieldValueMap();
			fvm.init(entityName, xml, migration, this);
            return fvm;
        case "nullability":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            return new NullabilityMigrator(entityName, xml.getAttributeValue("field"), "true".equalsIgnoreCase(xml
                    .getAttributeValue("nullable")));
        case "null":
            return new NullMigrator(entityName);
        case "default-value":
            if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
            if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
            fieldName = xml.getAttributeValue("field");
            return new DefaultFieldValueMigrator(entityName, fieldName, xml.getValue(),
                    "true".equalsIgnoreCase(xml.getAttributeValue("force")));
		case "pull":
			if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
			if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
			fieldName = xml.getAttributeValue("field");
			field = entity.getField(fieldName);
			if (field == null) {
				throw new IllegalArgumentException("No such field " + entityName + "." + fieldName);
			}
			return new ValuePullMigrator().init(entityName, xml, migration, this);
		case "push":
			if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
			if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
			fieldName = xml.getAttributeValue("field");
			field = entity.getField(fieldName);
			if (field == null) {
				throw new IllegalArgumentException("No such field " + entityName + "." + fieldName);
			}
			return new ValuePushMigrator().init(entityName, xml, migration, this);
		case "ascend":
			if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
			if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
			return new AscendToSuperType().init(entityName, xml, migration, this);
		case "descend":
			if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
			if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
			return new DescendToSubType().init(entityName, xml, migration, this);
		case "switch":
			if (entityName == null) {
				throw new IllegalArgumentException("No entity specified for migration " + xml.getName());
			}
			if (entity == null) {
				throw new IllegalArgumentException("No such entity " + entityName + " for migration " + xml.getName());
			}
			return new SwitchMigrator().init(entityName, xml, migration, this);
        case "custom":
            Class<? extends CustomMigrator> type;
            try {
				if (theTypeGetter != null) {
					type = theTypeGetter.getType(xml.getAttributeValue("type")).asSubclass(CustomMigrator.class);
				} else {
					type = Class.forName(xml.getAttributeValue("type")).asSubclass(CustomMigrator.class);
				}
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Custom migrator class " + xml.getAttributeValue("type") + " not found", e);
            }
            Element config = xml;
            try {
				return type.newInstance().init(entityName, config, migration, this);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
                throw new IllegalStateException("Custom migrator class " + xml.getAttributeValue("type") + " could not be instantiated", e);
            }
        }
        throw new IllegalArgumentException("Unrecognized serialized migrator element: " + xml.getName());
    }
}
