package org.migration.migrators;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;
import org.migration.util.PersistenceUtils;
import org.qommons.BiTuple;

/** Adds new entity data */
public class NewDataMigrator implements CustomMigrator {
    private Element theConfig;

    @Override
    public CustomMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        theConfig = config;
        return this;
    }

    @Override
    public String getEntityName() {
        return null;
    }

    @Override
    public Element serialize() {
        return theConfig;
    }

    @Override
    public GenericEntity migrate(GenericEntity nothing, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
		Map<BiTuple<String, String>, GenericEntity> iddObjects = new HashMap<>();
        for (Element content : serialize().getChildren()) {
			try {
                addContent(content, allEntities, iddObjects, dissecter);
            } catch (Exception e) {
                e.printStackTrace();
            }
		}
        return nothing;
    }

	private GenericEntity addContent(Element entityEl, GenericEntitySet allEntities, Map<BiTuple<String, String>, GenericEntity> iddObjects,
            TypeSetDissecter dissecter) {
        EntityType type = allEntities.getCurrentTypes().getEntityType(entityEl.getName());
        if (type == null) {
			throw new IllegalStateException("Unrecognized type: " + entityEl.getName());
		}
        return getEntity(type, entityEl, allEntities, iddObjects, dissecter);
    }

    private GenericEntity getEntity(EntityType type, Element entityEl, GenericEntitySet allEntities,
			Map<BiTuple<String, String>, GenericEntity> iddObjects, TypeSetDissecter dissecter) {
        checkAttributes(entityEl);
        if ("true".equalsIgnoreCase(entityEl.getAttributeValue("null"))) {
			return null;
		}
        String store = entityEl.getAttributeValue("store");
        String retrieve = entityEl.getAttributeValue("retrieve");
        if (store != null && retrieve != null) {
			throw new IllegalStateException("store and retrieve cannot both be defined for an entity");
		}
        if (retrieve != null && !entityEl.getChildren().isEmpty()) {
			throw new IllegalStateException("Field elements may not be present in a reference (" + entityEl.getName() + ", retrieve="
                    + retrieve + ")");
		}

		BiTuple<String, String> key;
        if (store != null) {
			key = new BiTuple<>(type.getName(), store);
		} else if (retrieve != null) {
			key = new BiTuple<>(type.getName(), retrieve);
		} else {
			key=null;
		}
        if ("true".equalsIgnoreCase(entityEl.getAttributeValue("search"))) {
            if (retrieve != null) {
				throw new IllegalStateException("retrieve attribute incompatible with search=true");
			}
            GenericEntity ret = findEntity(type, entityEl, allEntities, iddObjects, dissecter);
            if (store != null) {
                if (iddObjects.containsKey(key)) {
					throw new IllegalStateException("A " + type.getName() + " with key " + store
                            + " already exists.  Found entity may not be assigned to this key");
				}
                iddObjects.put(key, ret);
            }
            return ret;
        }
        GenericEntity ret;
        if (retrieve != null) {
            ret = iddObjects.get(key);
            if (ret == null) {
				throw new IllegalStateException("No " + type.getName() + " stored as " + retrieve);
			}
            return ret;
        }

        if (entityEl.getChildren().isEmpty()) {
			throw new IllegalStateException("Field elements must be specified for entity definitions (" + entityEl.getName()
                    + (store == null ? "" : ", store=" + store) + ")");
		}

        ret = allEntities.addEntity(type.getName());
        if (entityEl.getChild(type.getIdField().getName()) == null) {
			if (type.getIdField().getType() == Integer.TYPE || type.getIdField().getType() == Integer.class) {
                int newId = 0;
                for (GenericEntity entity : allEntities.get(type.getName())) {
                    if (entity == ret) {
						continue;
					}
                    if (entity.getInt(type.getIdField().getName()) >= newId) {
						newId = entity.getInt(type.getIdField().getName()) + 1;
					}
                }
                ret.set(type.getIdField().getName(), newId);
            } else if (type.getIdField().getType() == Long.TYPE || type.getIdField().getType() == Long.class) {
                long newId = 0;
                for (GenericEntity entity : allEntities.get(type.getName())) {
                    if (entity == ret) {
						continue;
					}
                    if (entity.getLong(type.getIdField().getName()) >= newId) {
						newId = entity.getLong(type.getIdField().getName()) + 1;
					}
                }
                ret.set(type.getIdField().getName(), newId);
            } else {
				throw new IllegalStateException("No " + type.getIdField().getName() + " value provided for " + type.getName());
			}
		}
        for (Element fieldEl : entityEl.getChildren()) {
            EntityField field = type.getField(fieldEl.getName());
            if (field == null) {
				throw new IllegalStateException("No such field " + fieldEl.getName() + " for entity " + type.getName());
			}
            ret.set(field.getName(), deserializeField(field, field.getType(), fieldEl, allEntities, iddObjects, dissecter));
        }
        if (key != null) {
			iddObjects.put(key, ret);
		}
        return ret;
    }

    private GenericEntity findEntity(EntityType type, Element entityEl, GenericEntitySet allEntities,
			Map<BiTuple<String, String>, GenericEntity> iddObjects, TypeSetDissecter dissecter) {
        Map<String, Object> searchFields = new LinkedHashMap<>();
        for (Element fieldEl : entityEl.getChildren()) {
            EntityField field = type.getField(fieldEl.getName());
            if (field == null) {
				throw new IllegalStateException("No such field " + fieldEl.getName() + " for entity " + type.getName());
			}
            EntityType fieldType;
            if (field.getType() instanceof EntityType) {
				fieldType = (EntityType) field.getType();
			} else if (field.getType() instanceof Class) {
				fieldType = allEntities.getCurrentTypes().getEntityType((Class<?>) field.getType());
			} else {
				fieldType = null;
			}
            if (fieldType != null && fieldEl.getAttributeValue("retrieve") == null
                    && !"true".equalsIgnoreCase(fieldEl.getAttributeValue("search"))) {
				throw new IllegalStateException("New entities may not be defined within an element search: " + entityEl.getName() + "/"
                        + fieldEl.getName());
			}
            searchFields.put(field.getName(), deserializeField(field, field.getType(), fieldEl, allEntities, iddObjects, dissecter));
        }
        for (GenericEntity entity : allEntities.get(type.getName())) {
            boolean matches = true;
            for (Map.Entry<String, Object> searchField : searchFields.entrySet()) {
                if (!matches) {
					break;
				}
                Object entityFieldVal = entity.get(searchField.getKey());
                if (entityFieldVal instanceof GenericEntity && searchField.getValue() instanceof GenericEntity) {
                    EntityType fieldType = ((GenericEntity) entityFieldVal).getCurrentType();
                    matches &= fieldType.getName().equals(((GenericEntity) searchField.getValue()).getCurrentType().getName());
                    Object id1 = ((GenericEntity) entityFieldVal).get(fieldType.getIdField().getName());
                    Object id2 = ((GenericEntity) searchField.getValue()).get(fieldType.getIdField().getName());
                    matches &= Objects.equals(id1, id2);
                } else {
					matches &= Objects.equals(entityFieldVal, searchField.getValue());
				}
            }
            if (matches) {
				return entity;
			}
        }
        if ("true".equalsIgnoreCase(entityEl.getAttributeValue("null-ok"))) {
			return null;
		} else {
			throw new IllegalStateException("No "+type.getName()+" entity with the given field values could be found");
		}
    }

    private Object deserializeField(EntityField field, Type type, Element fieldEl, GenericEntitySet allEntities,
			Map<BiTuple<String, String>, GenericEntity> iddObjects, TypeSetDissecter dissecter) {
        checkAttributes(fieldEl);
        if ("true".equalsIgnoreCase(fieldEl.getAttributeValue("null"))) {
			return null;
		}
        if (field.getType() instanceof Class) {
            EntityType entityType = allEntities.getCurrentTypes().getEntityType((Class<?>) field.getType());
            if (entityType != null) {
				return getEntity(entityType, fieldEl, allEntities, iddObjects, dissecter);
			}
        } else if (field.getType() instanceof EntityType) {
			return getEntity((EntityType) field.getType(), fieldEl, allEntities, iddObjects, dissecter);
		}
        if (fieldEl.getAttributeValue("store") != null || fieldEl.getAttributeValue("retrieve") != null
                || fieldEl.getAttributeValue("search") != null) {
			throw new IllegalStateException("store/retrieve/search attributes are not valid for non-entity types");
		}
        if (field.getType() instanceof Class) {
            Class<?> clazz = (Class<?>) field.getType();
            SimpleFormat format = dissecter.getFormat(clazz);
            if (format != null) {
				return format.parse(clazz, fieldEl.getTextNormalize());
			} else {
				throw new IllegalStateException(PersistenceUtils.toString(field.getType()) + " is not a recognized type (property "
                        + fieldEl.getName() + ", entity type " + field.entity.getName() + ")");
			}
        } else if (field.getType() instanceof ParameterizedType && ((ParameterizedType) field.getType()).getRawType() instanceof Class) {
            ParameterizedType pt = (ParameterizedType) field.getType();
            Class<?> raw = (Class<?>) pt.getRawType();
            if (Collection.class.isAssignableFrom(raw)) {
                Type typeArg = pt.getActualTypeArguments()[0];
                while (!(typeArg instanceof Class) && !(typeArg instanceof EntityType)) {
					if (typeArg instanceof ParameterizedType) {
						typeArg = ((ParameterizedType) typeArg).getRawType();
					} else {
						throw new IllegalStateException("Collection property " + fieldEl.getName() + ", entity type "
                                + field.entity.getName() + " does not have a definite element type (" + PersistenceUtils.toString(typeArg)
                                + " cannot be resolved)");
					}
				}
                Collection<?> value;
                if (SortedSet.class.equals(raw)) {
					value = new TreeSet<>();
				} else if (Set.class.equals(raw)) {
					value = new LinkedHashSet<>();
				} else if (List.class.equals(raw)) {
					value = new ArrayList<>();
				} else if (Collection.class.equals(raw)) {
					value = new ArrayList<>();
				} else {
					throw new IllegalStateException("The collection type " + PersistenceUtils.toString(type) + " cannot be persisted"
                            + ", entity type " + field.entity.getName());
				}
                EntityType argEntityType;
                if (typeArg instanceof EntityType) {
					argEntityType = (EntityType) typeArg;
				} else if (typeArg instanceof Class) {
					argEntityType= allEntities.getCurrentTypes().getEntityType((Class<?>) typeArg);
				} else {
					argEntityType=null;
				}
                String elementName = PersistenceUtils.singularize(fieldEl.getName());
                Collection<Object> coll = (Collection<Object>) value;
                for (Element el : fieldEl.getChildren(elementName)) {
                    Object element;
                    if (argEntityType != null) {
						element = getEntity(argEntityType, el, allEntities, iddObjects, dissecter);
					} else {
						element = deserializeField(field, typeArg, el, allEntities, iddObjects, dissecter);
					}
                    coll.add(element);
                }
                return coll;
            } else if (Map.class.isAssignableFrom(raw)) {
                Type keyPT = pt.getActualTypeArguments()[0];
                while (!(keyPT instanceof Class)) {
					if (keyPT instanceof ParameterizedType) {
						keyPT = ((ParameterizedType) keyPT).getRawType();
					} else {
						throw new IllegalStateException("Map property " + fieldEl.getName() + ", entity type " + field.entity.getName()
                                + " does not have a definite key type (" + keyPT + " cannot be resolved)");
					}
				}
                Type valPT = pt.getActualTypeArguments()[1];
                while (!(valPT instanceof Class)) {
					if (valPT instanceof ParameterizedType) {
						valPT = ((ParameterizedType) valPT).getRawType();
					} else {
						throw new IllegalStateException("Map property " + fieldEl.getName() + ", entity type " + field.entity.getName()
                                + " does not have a definite value type (" + valPT + " cannot be resolved)");
					}
				}
                Class<?> keyType = (Class<?>) keyPT;
                Class<?> valType = (Class<?>) valPT;
                EntityType keyEntityType = allEntities.getCurrentTypes().getEntityType(keyType);
                EntityType valEntityType = allEntities.getCurrentTypes().getEntityType(valType);
                Map<?, ?> value;
                if (SortedMap.class.equals(raw)) {
					value = new TreeMap<>();
				} else if (Map.class.equals(raw)) {
					value = new LinkedHashMap<>();
				} else {
					throw new IllegalStateException("The map type " + PersistenceUtils.toString(field.getType()) + " cannot be persisted"
                            + ", entity type " + field.entity.getName());
				}
                String elementName = PersistenceUtils.singularize(fieldEl.getName());
                Map<Object, Object> map = (Map<Object, Object>) value;
                for (Element entryEl : fieldEl.getChildren(elementName)) {
                    Object key;
                    if (keyEntityType != null) {
						key = getEntity(keyEntityType, entryEl.getChild("key"), allEntities, iddObjects, dissecter);
					} else {
						key = deserializeField(field, keyType, entryEl.getChild("key"), allEntities, iddObjects, dissecter);
					}
                    Object val;
                    if (valEntityType != null) {
						val = getEntity(valEntityType, entryEl.getChild("value"), allEntities, iddObjects, dissecter);
					} else {
						val = deserializeField(field, keyType, entryEl.getChild("value"), allEntities, iddObjects, dissecter);
					}
                    map.put(key, val);
                }
                return map;
            } else {
				throw new IllegalStateException("Unrecognized field type: " + field);
			}
        } else {
			throw new IllegalStateException("Unrecognized field type: " + field);
		}
    }

    private static final Set<String> ACCEPTED_ATTRIBUTES;

    static {
        Set<String> attrs = new LinkedHashSet<>();
        attrs.add("null");
        attrs.add("null-ok");
        attrs.add("search");
        attrs.add("store");
        attrs.add("retrieve");
        ACCEPTED_ATTRIBUTES = Collections.unmodifiableSet(attrs);
    }

    private static void checkAttributes(Element el) {
        for (org.jdom2.Attribute attr : el.getAttributes()) {
			if (!ACCEPTED_ATTRIBUTES.contains(attr.getName())) {
				throw new IllegalStateException("Unrecognized attribute " + el.getName() + " " + attr.getName() + "=" + attr.getValue());
			}
		}
    }
}
