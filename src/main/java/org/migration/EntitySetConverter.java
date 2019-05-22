package org.migration;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.EnumType;
import org.migration.generic.EnumValue;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.util.PersistenceUtils;


/** Converts between {@link EntitySet}s of POJO entities and {@link GenericEntitySet}s of {@link GenericEntity GenericEntities} */
public class EntitySetConverter {
    private final TypeSetDissecter theDissecter;
    private final Set<Class<?>> theEntityClasses;
    private final Predicate<Type> REAL_TYPE_TEST;
    private final Predicate<Type> GENERIC_TYPE_TEST;
    private Predicate<Object> theEntityFilter;

    /**
     * @param dissecter
     *            The dissecter to understand data types
     * @param entityClasses
     *            The entity classes to convert entities for
     */
    public EntitySetConverter(TypeSetDissecter dissecter, Collection<Class<?>> entityClasses) {
        theDissecter = dissecter;
        theEntityClasses = new LinkedHashSet<>(entityClasses);
        REAL_TYPE_TEST = type -> theEntityClasses.contains(type) || (type instanceof Class && Enum.class.isAssignableFrom((Class<?>) type));
        GENERIC_TYPE_TEST = type -> type instanceof EntityType;
    }

    /**
     * @param filter
     *            The filter to use to exclude entities from the converted data
     * @return This converter, for chaining
     */
    public EntitySetConverter setFilter(Predicate<Object> filter) {
        theEntityFilter = filter;
        return this;
    }

    /**
     * @param entities
     *            The real entities to export
     * @return The exported generic entity set
     */
    public GenericEntitySet exportEntities(EntitySet entities) {
        EntityTypeSet types = EntityTypeSet.createTypesForClasses(theEntityClasses, theDissecter);

        GenericEntitySet entitySet = new GenericEntitySet(types);

        duplicate(entities, type -> {
            EntityType genericType = types.getEntityType(type);
            if (genericType == null) {
				throw new IllegalStateException("No generic type mapped to " + type.getName());
			}
            return new ValueDissecter() {
                @Override
                public TypedField[] getFields() {
                    ArrayList<TypedField> ret = new ArrayList<>();
                    for (EntityField field : genericType) {
                        TypedField.Builder builder = TypedField.builder(type, field.getName(), field.getType());
                        builder.id(field.isId()).nullable(field.isNullable()).mapping(field.getMappingField()).ordering(field.getSorting());
                        ret.add(builder.build());
                    }
                    return ret.toArray(new TypedField[ret.size()]);
                }

                @Override
                public Object getFieldValue(Object entity, String field) {
                    return ((GenericEntity) entity).get(field);
                }

                @Override
                public Object createWith(Map<String, Object> fieldValues) {
                    GenericEntity ret = entitySet.addEntity(genericType.getName());
                    for (Map.Entry<String, Object> field : fieldValues.entrySet()) {
						ret.set(field.getKey(), field.getValue());
					}
                    return ret;
                }

                @Override
                public void setFieldValue(Object entity, String field, Object fieldValue) {
                    if (fieldValue instanceof Enum) {
                        EnumValue enumValue = types.getEnumType(fieldValue.getClass()).getValue(((Enum<?>) fieldValue).name());
                        if (enumValue == null) {
							throw new IllegalArgumentException(
                                    "Unrecognized enum value " + types.getEnumType(fieldValue.getClass()) + "." + fieldValue);
						}
                        fieldValue = enumValue;
                    }
                    ((GenericEntity) entity).set(field, fieldValue);
                }
            };
        }, null, GENERIC_TYPE_TEST, false, true);
        return entitySet;
    }

    /**
     * Duplicates a set of entities in some custom way
     * 
     * @param entities
     *            The entities to duplicate
     * @param entityTypeTest
     *            Knows which types are entity types
     * @param creator
     *            Supplies dissecters that know how to put together custom entities for each real entity type
     * @param directMap
     *            An optional function that will generate a value that this method will use as the duplicate of the given value instead of
     *            performing the duplication operation on it
     * @param withMappedCollections
     *            Whether to hook up mapped collections in the duplicates
     * @param withIds
     *            Whether to populate the duplicated values with IDs
     * @return The entity map with the duplicated values mapped by the corresponding original real entities
     */
    public <T> EntityMap<T> duplicate(EntitySet entities, Function<Class<?>, ValueDissecter> creator, Function<Object, T> directMap,
            Predicate<Type> entityTypeTest, boolean withMappedCollections, boolean withIds) {
        EntityMap<T> entitiesById = new EntityMap<>();

        // Create generic entities and populate with non-entity fields
        for (Class<?> clazz : theEntityClasses) {
            ValueDissecter dissecter = (ValueDissecter) theDissecter.getDissecter(clazz).dissect(clazz, null);
            ValueDissecter typeCreator = creator.apply(clazz);
            Map<String, Object> fields = new LinkedHashMap<>();
            for (Object entity : entities.get(clazz, true)) {
                if (entity.getClass() != clazz && theEntityClasses.contains(entity.getClass()))
				 {
					continue; // Do this with the subclass's type
				}

                if (entitiesById.containsKey(entity)) {
                    System.out.println(clazz.getName() + " " + entity + " supplied multiple times");
                    continue;
                }
                if (directMap != null) {
                    T reuse = directMap.apply(entity);
                    if (reuse != null) {
                        entitiesById.put(entity, reuse);
                        continue;
                    }
                }
                for (TypedField field : dissecter.getFields()) {
                    if (!withMappedCollections && field.mapping != null) {
                        continue;
                    }
                    Object value = dissecter.getFieldValue(entity, field.name);
                    if (!withIds && field.id) {
						continue;
					}
                    if (value == null) {
						continue;
					}
                    if (PersistenceUtils.hasEntityType(field.type, theDissecter, REAL_TYPE_TEST)) {
						continue;
					}

                    fields.put(field.name, value);
                }
                T gen = (T) typeCreator.createWith(fields);
                entitiesById.put(entity, gen);
                fields.clear();
            }
        }

        // Populate entity dependencies
        EntityMapCopyGetter<T> copyGetter = new EntityMapCopyGetter<>(entitiesById, theEntityFilter);
        for (Class<?> clazz : theEntityClasses) {
            ValueDissecter dissecter = (ValueDissecter) theDissecter.getDissecter(clazz).dissect(clazz, null);
            ValueDissecter typeCreator = creator.apply(clazz);
            Map<String, TypedField> dupFields = Arrays.asList(typeCreator.getFields()).stream()
                    .collect(Collectors.toMap(f -> f.name, f -> f));
            for (Object entity : entities.get(clazz, false)) {
                T gen = entitiesById.get(entity);
                for (TypedField field : dissecter.getFields()) {
                    if (!withMappedCollections && field.mapping != null) {
						continue;
					}
                    if (!PersistenceUtils.hasEntityType(field.type, theDissecter, REAL_TYPE_TEST))
					 {
						continue; // Already done
					}
                    TypedField dupField = dupFields.get(field.name);

                    Object value = linkEntityDependencies(dissecter.getFieldValue(entity, field.name), dupField.type, entityTypeTest,
                            copyGetter, dupField);
                    typeCreator.setFieldValue(gen, field.name, value);
                }
            }
        }
        return entitiesById;
    }

    /**
     * @param entitySet
     *            The generic entities to import into real entities
     * @param withMappedCollections
     *            Whether to realize mapped collections (not needed for hibernate persistence, for example)
     * @param withIds
     *            Whether to set the IDs on the new entities (this has implications for hibernate)
     * @return The imported real entity set
     */
    public EntitySet importEntities(GenericEntitySet entitySet, boolean withMappedCollections, boolean withIds) {
        EntitySet entityTree = new EntitySet();
        // First, create all the entity values using the values of all non-entity fields
        for (EntityType type : entitySet.getCurrentTypes()) {
            Class<?> clazz = entitySet.getCurrentTypes().getMappedEntity(type);
            entityTree.addClass(clazz);
            ValueDissecter dissecter = (ValueDissecter) theDissecter.getDissecter(clazz).dissect(clazz, null);
            Map<String, Object> fields = new LinkedHashMap<>();
            for (GenericEntity entity : entitySet.get(type.getName())) {
                if (!entity.getCurrentType().equals(type))
				 {
					continue; // Sub-type
				}
                for (TypedField field : dissecter.getFields()) {
                    if (PersistenceUtils.hasEntityType(field.type, theDissecter, REAL_TYPE_TEST)) {
						continue;
					}
                    if (!withIds && field.id) {
						continue;
					}
                    if (field.type instanceof EnumType) {
						fields.put(field.name, getEnumValue(entitySet.getCurrentTypes(), (EnumValue) entity.get(field.name)));
					} else {
						fields.put(field.name, entity.get(field.name));
					}
                }
                Object realEntity = dissecter.createWith(fields);
                fields.clear();
                entityTree.addForId(realEntity, entity.getIdentity());
            }
        }

        // Link entity fields
        RealEntityCopyGetter copyGetter = new RealEntityCopyGetter(entitySet.getCurrentTypes(), entityTree);
        for (EntityType type : entitySet.getCurrentTypes()) {
            Class<?> clazz = entitySet.getCurrentTypes().getMappedEntity(type);
            ValueDissecter dissecter = (ValueDissecter) theDissecter.getDissecter(clazz).dissect(clazz, null);

            // Get the list of fields we'll link for this type
            List<TypedField> fields = new ArrayList<>();
            for (TypedField field : dissecter.getFields()) {
                if (!withMappedCollections && field.mapping != null) {
					continue;
				}
                if (!PersistenceUtils.hasEntityType(field.type, theDissecter, REAL_TYPE_TEST))
				 {
					continue; // Already handled
				}
                fields.add(field);
            }

            // Need the typed fields for collection sorting mostly
            Map<String, TypedField> typedFields = new LinkedHashMap<>();
            for (TypedField f : dissecter.getFields()) {
				typedFields.put(f.name, f);
			}

            for (GenericEntity genericEntity : entitySet.get(type.getName())) {
                if (!genericEntity.getCurrentType().equals(type))
				 {
					continue; // Sub-type
				}
                Object realEntity = entityTree.get(clazz, genericEntity.getIdentity());
                for (TypedField field : fields) {
                    try {
                        Object fieldValue = linkEntityDependencies(genericEntity.get(field.name), field.type, REAL_TYPE_TEST, copyGetter,
                                field);
                        dissecter.setFieldValue(realEntity, field.name, fieldValue);
                    } catch (RuntimeException e) {
                        System.err.println("Could not set field " + field + " on value " + realEntity + " in entity type "
                                + clazz.getName());
                        e.printStackTrace();
                    }
                }
            }
        }

        return entityTree;
    }

    private <E extends Enum<E>> E getEnumValue(EntityTypeSet entityTypes, EnumValue value) {
        if (value == null) {
			return null;
		}
        Class<E> enumClazz = (Class<E>) entityTypes.getMappedEnum(value.getEnumType());
        return Enum.valueOf(enumClazz, value.getName());
    }

    private Object linkEntityDependencies(Object value, Type type, Predicate<Type> entityTypeTester, Function<Object, ?> entityCopyGetter,
            TypedField field) {
        if (value == null) {
			return null;
		}
        if (entityTypeTester.test(type)) {
            if (theEntityFilter != null && !theEntityFilter.test(value)) {
				return null;
			}
            Object ret = entityCopyGetter.apply(value);
            if (ret != null) {
				return ret;
			}
        } else if (!PersistenceUtils.hasEntityType(type, theDissecter, entityTypeTester)) {
			return value;
		}
        Class<?> raw = PersistenceUtils.getRawType(type);
        DissecterGenerator gen = theDissecter.getDissecter(raw);
        if (gen == null) {
			throw new IllegalStateException("Unrecognized type " + PersistenceUtils.toString(type));
		}
        Dissecter dissecter = gen.dissect(type, gen.getSubType(value.getClass()));
        if (dissecter instanceof ValueDissecter) {
            ValueDissecter vd = (ValueDissecter) dissecter;
            TypedField[] fields = vd.getFields();
            Map<String, Object> fieldValues = new LinkedHashMap<>();
            for (TypedField f : fields) {
				fieldValues.put(field.name,
                        linkEntityDependencies(vd.getFieldValue(value, f.name), f.type, entityTypeTester, entityCopyGetter, null));
			}
            return vd.createWith(fieldValues);
        } else if (dissecter instanceof CollectionDissecter) {
            CollectionDissecter cd = (CollectionDissecter) dissecter;
            Type compType = cd.getComponentType();
            Collection<Object> elements = new ArrayList<>();
            for (Object el : cd.getElements(value)) {
				elements.add(linkEntityDependencies(el, compType, entityTypeTester, entityCopyGetter, null));
			}
            return cd.createFrom(elements, field);
        }
        throw new IllegalStateException("Unrecognized entity type " + PersistenceUtils.toString(type));
    }

    private static class EntityMapCopyGetter<T> implements Function<Object, T> {
        private final EntityMap<T> theEntities;
        private final Predicate<Object> theFilter;

        EntityMapCopyGetter(EntityMap<T> entities, Predicate<Object> filter) {
            theEntities = entities;
            theFilter = filter;
        }

        @Override
        public T apply(Object value) {
            if (theFilter != null && !theFilter.test(value)) {
				return null;
			}
            T ret = theEntities.get(value);
            if (ret == null) {
				throw new IllegalStateException("No mapped entity for " + value);
			}
            return ret;
        }
    }

    private static class RealEntityCopyGetter implements Function<Object, Object> {
        private final EntityTypeSet theGenericTypes;
        private final EntitySet theRealEntities;

        RealEntityCopyGetter(EntityTypeSet genericTypes, EntitySet realEntities) {
            theGenericTypes = genericTypes;
            theRealEntities = realEntities;
        }

        @Override
        public Object apply(Object value) {
            if (value instanceof GenericEntity) {
                GenericEntity gen = (GenericEntity) value;
                Class<?> realType = theGenericTypes.getMappedEntity(gen.getCurrentType());
				if (realType == null) {
					throw new IllegalStateException("No entity class mapped for " + gen.getCurrentType());
				}
                Object ret = theRealEntities.get(realType, gen.getIdentity());
                if (ret == null) {
					throw new IllegalStateException("No real entity mapped for " + gen.getCurrentType() + ", ID " + gen.getIdentity());
				}
                return ret;
            } else if (value instanceof EnumValue) {
                return getEnumValue((EnumValue) value);
            } else {
				throw new IllegalStateException("Non-entity or -enum value: " + value.getClass().getName() + ": " + value);
			}
        }

        private <E extends Enum<E>> E getEnumValue(EnumValue value) {
            Class<E> enumType = (Class<E>) theGenericTypes.getMappedEnum(value.getEnumType());
            if (enumType == null) {
				throw new IllegalStateException("No enum class mapped for " + value.getEnumType());
			}
            return Enum.valueOf(enumType, value.getName());
        }
    }
}
