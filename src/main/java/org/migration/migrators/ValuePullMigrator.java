package org.migration.migrators;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;
import org.migration.util.PersistenceUtils;

/** Moves a value from one of an entity's related entities to the entity itself */
public class ValuePullMigrator extends JavaMigrator {
	private String theTargetFieldName;
	private String theFieldPathStr;

	private EntityField theTargetField;
	private List<EntityField> theFieldPath;

    @Override
    public ValuePullMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        super.init(entity, config, migration, factory);
		theTargetFieldName = config.getAttributeValue("field");
		theFieldPathStr = config.getTextNormalize();
        return this;
    }

    @Override
	public JavaMigrator init(EntityTypeSet entities, TypeSetDissecter dissecter) {
		super.init(entities, dissecter);
		theTargetField = getEntity().getField(theTargetFieldName);
		if (theTargetField == null) {
			throw new IllegalArgumentException("Unrecognized field " + getEntity() + "." + theTargetFieldName);
		}
		theFieldPath = getFieldPath(getEntity(), theFieldPathStr);
		EntityField pathTerminus = theFieldPath.get(theFieldPath.size() - 1);
        checkPotentialAssignment(pathTerminus, theTargetField);
		return this;
	}

	@Override
	public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
		assign(oldVersionEntity, theFieldPath, Arrays.asList(theTargetField), allEntities, dissecter);
        return oldVersionEntity;
    }

	public static List<EntityField> getFieldPath(EntityType sourceType, String path) {
		if (path == null) {
			throw new IllegalArgumentException("No field path specified");
		}
		String[] split = path.split("\\.");
		if (split.length == 0) {
			throw new IllegalArgumentException("Illegal field path: " + path);
		}
		List<EntityField> fieldPath = new ArrayList<>(split.length);
		Type type = sourceType;
		for (String f : split) {
			if (!(type instanceof EntityType)) {
				throw new IllegalArgumentException(
						"Non-terminal element of field path, " + fieldPath.get(fieldPath.size() - 1) + " is not an entity type: " + path);
			}
			EntityField field = ((EntityType) type).getField(f);
			if (field == null) {
				throw new IllegalArgumentException("Unrecognized field " + type + "." + f + " in path " + path);
			}
			fieldPath.add(field);
			type = field.getType();
		}
		return Collections.unmodifiableList(fieldPath);
	}

    public static void checkPotentialAssignment(EntityField from, EntityField to) {
        Type fromType = from.getType();
        Type toType = to.getType();
        if (PersistenceUtils.isConvertible(fromType, toType)) {
			return;
		}
        if (!(fromType instanceof ParameterizedType) || !(toType instanceof ParameterizedType)) {
			throw new IllegalArgumentException("Value of field " + from + " cannot be assigned to " + to);
		}
        Class<?> fromRaw = (Class<?>) ((ParameterizedType) fromType).getRawType();
        Class<?> toRaw = (Class<?>) ((ParameterizedType) toType).getRawType();
        if (Collection.class.isAssignableFrom(fromRaw) && Collection.class.isAssignableFrom(toRaw)) {
            Type fromParam = ((ParameterizedType) fromType).getActualTypeArguments()[0];
            Type toParam = ((ParameterizedType) toType).getActualTypeArguments()[0];
            // This allows the values in fields that *might* be instances of the target type to be assigned
            // An exception will be thrown at run time if the actual value type is incompatible
            if (PersistenceUtils.isConvertible(fromParam, toParam) || PersistenceUtils.isConvertible(toParam, fromParam)) {
            } else {
				throw new IllegalArgumentException("Value of field " + from + " cannot be assigned to " + to);
			}
        } else if (Map.class.isAssignableFrom(fromRaw) && Map.class.isAssignableFrom(toRaw)) {
            Type fromKey = ((ParameterizedType) fromType).getActualTypeArguments()[0];
            Type toKey = ((ParameterizedType) toType).getActualTypeArguments()[0];
            Type fromValue = ((ParameterizedType) fromType).getActualTypeArguments()[1];
            Type toValue = ((ParameterizedType) toType).getActualTypeArguments()[1];
            // This allows the values in fields that *might* be instances of the target type to be assigned
            // An exception will be thrown at run time if the actual value type is incompatible
            if ((PersistenceUtils.isConvertible(fromKey, toKey) || PersistenceUtils.isConvertible(toKey, fromKey))//
                    && (PersistenceUtils.isConvertible(fromValue, toValue) || PersistenceUtils.isConvertible(toValue, fromValue))) {
            } else {
				throw new IllegalArgumentException("Value of field " + from + " cannot be assigned to " + to);
			}
        } else {
			throw new IllegalArgumentException("Value of field " + from + " cannot be assigned to " + to);
		}
    }

	public static void assign(GenericEntity entity, List<EntityField> source, List<EntityField> dest, GenericEntitySet entities,
			TypeSetDissecter dissecter) {
		GenericEntity container = (GenericEntity) evaluateFieldPath(entity, dest, true);
		EntityField targetField = dest.get(dest.size() - 1);
		Object value = evaluateFieldPath(entity, source, false);
		if (targetField.getType() instanceof ParameterizedType
				&& ((ParameterizedType) targetField.getType()).getRawType() instanceof Class) {
			Class<?> rawType = (Class<?>) ((ParameterizedType) targetField.getType()).getRawType();
			if (Collection.class.isAssignableFrom(rawType)) {
				Collection<Object> collect = (Collection<Object>) container.get(targetField.getName());
				if (collect == null) {
					collect = (Collection<Object>) DefaultFieldValueMigrator.createDefaultValue(targetField.getType(), entities, dissecter,
							null);
					container.set(targetField.getName(), collect);
				} else {
					collect.clear();
				}
                Type targetType = ((ParameterizedType) targetField.getType()).getActualTypeArguments()[0];
                ((Collection<?>) value).forEach(v -> check(targetType, v));
				collect.addAll((Collection<?>) value);
			} else if (Map.class.isAssignableFrom(rawType)) {
				Map<Object, Object> map = (Map<Object, Object>) container.get(targetField.getName());
				if (map == null) {
					map = (Map<Object, Object>) DefaultFieldValueMigrator.createDefaultValue(targetField.getType(), entities, dissecter,
							null);
					container.set(targetField.getName(), map);
				} else {
					map.clear();
				}
                Type targetKeyType = ((ParameterizedType) targetField.getType()).getActualTypeArguments()[0];
                Type targetValueType = ((ParameterizedType) targetField.getType()).getActualTypeArguments()[1];
                ((Map<?, ?>) value).entrySet().forEach(entry -> {
                    check(targetKeyType, entry.getKey());
                    check(targetValueType, entry.getValue());
                });
				map.putAll((Map<?, ?>) value);
			} else {
				container.set(targetField.getName(), value);
			}
		} else {
			container.set(targetField.getName(), value);
		}
	}

    public static Object evaluateFieldPath(GenericEntity entity, List<EntityField> fieldPath, boolean penUltimate) {
		Object value = entity;
		int limit = penUltimate ? fieldPath.size() - 1 : fieldPath.size();
		for (int i = 0; i < limit; i++) {
			value = ((GenericEntity) value).get(fieldPath.get(i).getName());
		}
		return value;
	}

    private static void check(Type targetType, Object value) {
        if (value == null) {
            if (targetType instanceof Class && ((Class<?>) targetType).isPrimitive()) {
				throw new IllegalArgumentException(
                        "Null value cannot be assigned to primitive type " + PersistenceUtils.toString(targetType));
			}
        } else if (targetType instanceof EntityType) {
            if (!(value instanceof GenericEntity)) {
				throw new IllegalArgumentException(
                        "Value of type " + value.getClass().getName() + " cannot be assigned to a field of type " + targetType);
			}
            if (!((EntityType) targetType).isAssignableFrom(((GenericEntity) value).getType())) {
				throw new IllegalArgumentException(
                        "Value of type " + PersistenceUtils.toString(value.getClass()) + " cannot be assigned to a field of type "
                                + targetType);
			}
        } else if (value instanceof GenericEntity) {
            throw new IllegalArgumentException("Value of type " + ((GenericEntity) value).getType()
                    + " cannot be assigned to a field of type " + PersistenceUtils.toString(targetType));
        } else if (!PersistenceUtils.isConvertible(value.getClass(), targetType)) {
            throw new IllegalArgumentException("Value of type " + PersistenceUtils.toString(value.getClass())
                    + " cannot be assigned to a field of type " + PersistenceUtils.toString(targetType));
        }
    }
}
