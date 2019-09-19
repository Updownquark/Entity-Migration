package org.migration.util;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.OrderColumn;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.SAXHandler;
import org.migration.CollectionDissecter;
import org.migration.Dissecter;
import org.migration.DissecterGenerator;
import org.migration.NotFoundType;
import org.migration.TypeGetter;
import org.migration.TypeSetDissecter;
import org.migration.TypedField;
import org.migration.ValueDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.EnumType;
import org.migration.generic.EnumValue;
import org.migration.generic.GenericEntity;
import org.qommons.ArrayUtils;
import org.qommons.StringUtils;
import org.xml.sax.SAXException;

/** Utilities used by the s4.persistence package */
public class PersistenceUtils {
    /** A handler to use with {@link PersistenceUtils#parseSerial(Reader, ElementHandler)} to deal with the elements in an XML file */
    public static interface ElementHandler {
        /**
         * @param element
         *            The element to handle
         */
        void handle(Element element);
    }

    /** Sorts the list value of an entity field */
    public static class OrderedFieldSorter implements Comparator<GenericEntity> {
        private final EntityField theListField;

        /**
         * @param field
         *            The field to sort the value for
         */
        public OrderedFieldSorter(EntityField field) {
            theListField = field;
        }

        @Override
        public int compare(GenericEntity o1, GenericEntity o2) {
            for (String order : theListField.getSorting()) {
                EntityField sortField = o1.getType().getField(order);
                if (sortField == null) {
                    throw new IllegalStateException("Field " + sortField + " specified in ordering on field " + theListField
                            + " is not present in target entity type");
                }
                if (sortField.getType() instanceof Class
                        && (Comparable.class.isAssignableFrom((Class<?>) sortField.getType()) || ((Class<?>) sortField.getType())
                                .isPrimitive())) {
                    int compare = compareFieldValues(o1.get(order), o2.get(order));
                    if (compare != 0) {
                        return compare;
                    }
                } else if (sortField.getType() instanceof EntityType) {
                    EntityField idField = ((EntityType) sortField.getType()).getIdField();
                    if (idField.getType() instanceof Class
                            && (Comparable.class.isAssignableFrom((Class<?>) idField.getType()) || ((Class<?>) idField.getType())
                                    .isPrimitive())) {
                        int compare = compareFieldValues(o1.get(order), o2.get(order));
                        if (compare != 0) {
                            return compare;
                        }
                    } else {
                        throw new IllegalStateException("Column " + sortField + " specified in ordering on field " + theListField
                                + " is of a non sortable type (id field " + idField + " is not sortable)");
                    }
                } else {
                    throw new IllegalStateException("Column " + sortField + " specified in ordering on field " + theListField
                            + " is of a non sortable type");
                }
            }
            return 0;
        }

        private int compareFieldValues(Object o1, Object o2) {
            if (o1 == null) {
                return o2 == null ? 0 : -1;
            }
            if (o2 == null) {
                return 1;
            }
            if (o1 instanceof GenericEntity) {
                GenericEntity e1 = (GenericEntity) o1;
                GenericEntity e2 = (GenericEntity) o2;
                return ((Comparable<Object>) e1.get(e1.getType().getIdField().getName())).compareTo(e2.get(e2.getType()
                        .getIdField().getName()));
            } else {
                return ((Comparable<Object>) o1).compareTo(o2);
            }
        }
    }

    /**
     * Parses an XML stream serially, notifying a handler for each immediate child (not each descendant) of the root element
     *
     * @param reader
     *            The XML stream to read
     * @param handler
     *            The handler to deal with each element under the root
     * @throws IOException
     *             If an error occurs reading the file
     * @throws JDOMException
     *             If an error occurs parsing the file
     */
    public static void parseSerial(Reader reader, final ElementHandler handler) throws IOException, JDOMException {
        final SAXHandler saxHandler = new SAXHandler() {
            @Override
            public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
                boolean hit = getCurrentElement().getParentElement() != null
                        && getCurrentElement().getParentElement().getParentElement() == null;
                if (hit) {
                    handler.handle(getCurrentElement());
                }
                super.endElement(namespaceURI, localName, qName);
                if (hit) {
                    // Clean up to lower memory footprint
                    getCurrentElement().removeContent();
                }
            }
        };
        SAXBuilder builder = new SAXBuilder();
        builder.setSAXHandlerFactory(factory -> saxHandler);
        builder.build(reader);
    }

    /**
     * @param entityClass
     *            The class to get the entity name of
     * @return The name of the entity to use in hibernate queries
     */
    public static String getEntityName(Class<?> entityClass) {
        Entity entity = entityClass.getAnnotation(Entity.class);
        if (entity == null || entity.name() == null || entity.name().length() == 0) {
            return entityClass.getSimpleName();
        } else {
            return entity.name();
        }
    }

    /**
     * @param className
     *            The name of the class to create an XML element for
     * @return The name of the XMl element for the class
     */
    public static String getElementNameFromClass(String className) {
        int dotIdx = className.lastIndexOf('.');
        if (dotIdx >= 0) {
            className = className.substring(dotIdx + 1);
        }
        return javaToXml(className);
    }

    /**
     * Converts from a standard java name (camel-cased, possibly with an initial capital) to a standard XML name (all lower-case, with
     * dashes)
     *
     * @param javaName
     *            The java name to convert
     * @return The converted XML name
     */
    public static String javaToXml(String javaName) {
		String ret = StringUtils.parseByCase(javaName, false).toKebabCase();
        if ((Character.isAlphabetic(ret.charAt(0)) || ret.charAt(0) == '_') && !javaName.toLowerCase().startsWith("xml")) {
        } else {
			ret = "_." + ret;
		}
		return ret;
    }

    /**
     * Converts from a standard XML name (all lower-case, with dashes) to a standard java name (camel-cased, possibly with an initial
     * capital)
     *
     * @param xmlName
     *            The XML name to convert
     * @param type
     *            Whether to return a type name (initial capital)
     * @return The converted java name
     */
    public static String xmlToJava(String xmlName, boolean type) {
		return StringUtils.split(xmlName, '-').toPascalCase();
    }

    /**
     * @param getter
     *            The getter method for a field
     * @return Whether the field is a mapped collection
     */
    public static String getMap(Method getter) {
        if (!(getter.getGenericReturnType() instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType pt = (ParameterizedType) getter.getGenericReturnType();
        Type raw = pt.getRawType();
        while (raw instanceof ParameterizedType) {
            raw = ((ParameterizedType) raw).getRawType();
        }
        if (!(raw instanceof Class)) {
            return null;
        }
        if (!Collection.class.isAssignableFrom((Class<?>) raw)) {
            return null;
        }
        OneToMany otm = getter.getAnnotation(OneToMany.class);
        if (otm != null) {
            if (otm.mappedBy().length() == 0) {
                return null;
            }
            return javaToXml(otm.mappedBy());
        }
        ManyToMany mtm = getter.getAnnotation(ManyToMany.class);
        if (mtm != null) {
            if (mtm.mappedBy().length() == 0) {
                return null;
            }
            return javaToXml(mtm.mappedBy());
        }
        return null;
    }

    /**
     * @param getter
     *            The getter method for a field
     * @return The columns that the field's collection is sorted by, in order
     */
    public static String[] getSorting(Method getter) {
        if (!(getter.getGenericReturnType() instanceof ParameterizedType)) {
			return new String[0];
		}
        ParameterizedType pt = (ParameterizedType) getter.getGenericReturnType();
        Type raw = pt.getRawType();
        while (raw instanceof ParameterizedType) {
			raw = ((ParameterizedType) raw).getRawType();
		}
        if (!(raw instanceof Class)) {
			return new String[0];
		}
        if (!List.class.isAssignableFrom((Class<?>) raw) && !SortedSet.class.isAssignableFrom(((Class<?>) raw))) {
			return new String[0];
		}
        OrderBy[] order = getter.getAnnotationsByType(OrderBy.class);
        OrderColumn[] orderCol = getter.getAnnotationsByType(OrderColumn.class);
        if (order.length == 0 && orderCol.length == 0) {
            return new String[0];
        }
        if (order.length + orderCol.length > 1) {
            String field = PersistenceUtils.javaToXml(ReflectionUtils.getFieldNameFromGetter(getter.getName()));
            System.err.println("Multiple @OrderBy/@OrderColumn tags specified on field " + getter.getDeclaringClass().getSimpleName() + "."
                    + field + ". First +" + (order.length > 0 ? "@OrderBy" : "@OrderColumn") + " annotation will be used");
        }
        if (order.length > 0) {
            String colStr = order[0].value();
            String[] cols = colStr.split(",");
            for (int i = 0; i < cols.length; i++) {
                String colTrim = cols[i].trim();
                if (colTrim.length() == 0) {
                    // Orders a collection by its values--not used here, as this ordering is attempted for collections regardless
                    cols = ArrayUtils.remove(cols, i);
                } else {
					cols[i] = javaToXml(colTrim);
				}
            }
            return cols;
        } else {
            return new String[] { javaToXml(orderCol[0].name()) };
        }
    }

    /**
     * Checks the validity of the sorting on a field
     *
     * @param field
     *            The field to check
     * @throws IllegalStateException
     *             If the sorting on the field is invalid for the field's type
     */
    public static void checkSorting(EntityField field) throws IllegalStateException {
        if (field.getSorting().length == 0) {
            return;
        }
        if (!(field.getType() instanceof ParameterizedType)) {
            throw new IllegalStateException("Field " + field + " cannot be sorted. Sorting is only available for fields of type"
                    + " java.util.List containing entities");
        }
        ParameterizedType pt = (ParameterizedType) field.getType();
        if (!(pt.getRawType() instanceof Class) || !List.class.isAssignableFrom((Class<?>) pt.getRawType())
                || pt.getActualTypeArguments().length != 1 || !(pt.getActualTypeArguments()[0] instanceof EntityType)) {
            throw new IllegalStateException("Field " + field + " cannot be sorted. Sorting is only available for fields of type"
                    + " java.util.List containing entities");
        }
        EntityType elType = (EntityType) pt.getActualTypeArguments()[0];
        for (String order : field.getSorting()) {
            EntityField sortField = elType.getField(order);
            if (sortField == null) {
                throw new IllegalStateException("Field " + order + " specified in ordering on field " + field
                        + " is not present in target entity type " + elType.getName());
            }
            if (sortField.getType() instanceof Class
                    && (Comparable.class.isAssignableFrom((Class<?>) sortField.getType()) || ((Class<?>) sortField.getType()).isPrimitive())) {
                // Ok
            } else if (sortField.getType() instanceof EntityType) {
                EntityField idField = ((EntityType) sortField.getType()).getIdField();
                if (idField.getType() instanceof Class
                        && (Comparable.class.isAssignableFrom((Class<?>) idField.getType()) || ((Class<?>) idField.getType()).isPrimitive())) {
                    // Ok
                } else {
                    throw new IllegalStateException("Column " + sortField + " specified in ordering on field " + field
                            + " is of a non sortable type (id field " + idField + " is not sortable)");
                }
            } else {
                throw new IllegalStateException("Column " + sortField + " specified in ordering on field " + field
                        + " is of a non sortable type");
            }
        }
    }

    /**
     *
     * @param entity
     *            The entity to check the type for
     * @param name
     *            The name of the field being checked
     * @param type
     *            The type to check against
     * @param value
     *            The value to check for compatibility with the field
     * @throws IllegalArgumentException
     *             If the given value cannot be assigned to the given field for any reason
     */
	public static void checkType(EntityType entity, String name, Type type, Object value) throws IllegalArgumentException {
        if (value == null && type instanceof Class && ((Class<?>) type).isPrimitive()) {
            throw new IllegalArgumentException("Field " + name + " of type " + entity.getName() + " is primitive--may not be set to null");
        }
        if (value != null) {
            if (type instanceof Class<?>) {
                if (!isConvertible(type, value.getClass())) {
                    if (value instanceof GenericEntity) {
                        if (!getElementNameFromClass(toString(type)).equals(((GenericEntity) value).getType().getName())) {
                            throw new IllegalArgumentException("Value of type " + ((GenericEntity) value).getType().getName()
                                    + " is not valid for field " + entity.getName() + "." + name + " (type "
                                    + PersistenceUtils.toString(type) + ")");
                        }
                    } else {
                        throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " is not valid for field "
                                + entity.getName() + "." + name + " (type " + PersistenceUtils.toString(type) + ")");
                    }
                } else {
                    /* Commenting this out, since custom types are now supported
                    if (value instanceof Number || value instanceof Boolean || value instanceof Character || value instanceof String
                            || value instanceof Date || value instanceof Timestamp || value instanceof Enum) {
                        // Primitives, strings, dates, and enums are fine
                    } else {
                        throw new IllegalArgumentException(((Class<?>) type).getSimpleName() + " instance must be converted to a "
                                + GenericEntity.class.getSimpleName());
                    }*/
                }
            } else if (type instanceof EntityType) {
                if (value instanceof GenericEntity) {
                    if (!((EntityType) type).isAssignableFrom(((GenericEntity) value).getType())) {
                        throw new IllegalArgumentException("Value of type " + ((GenericEntity) value).getType().getName()
                                + " is not valid for field " + entity.getName() + "." + name + " (type " + PersistenceUtils.toString(type)
                                + ")");
                    }
                } else {
                    throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " is not valid for field "
                            + entity.getName() + "." + name + " (entity type " + PersistenceUtils.toString(type) + ")");
                }
            } else if (type instanceof EnumType) {
                if (value instanceof EnumValue) {
                    if (((EnumValue) value).getEnumType() != type) {
						throw new IllegalArgumentException("Value " + value + " of enum type " + ((EnumValue) value).getEnumType().getName()
                                + " is not valid for enum type " + type);
					}
                } else {
					throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " is not valid for field "
                            + entity.getName() + "." + name + " (enum type " + PersistenceUtils.toString(type) + ")");
				}
            } else if (type instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) type;
                if (!(pt.getRawType() instanceof Class)) {
                    throw new IllegalStateException("Parameterized type not based on a class.  Implementation error.");
                }
                if (!((Class<?>) pt.getRawType()).isInstance(value)) {
                    throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " is not valid for field "
                            + entity.getName() + "." + name + " (type " + PersistenceUtils.toString(type) + ")");
                }
                if (value instanceof Collection) {
                    Type elType = pt.getActualTypeArguments()[0];
                    for (Object val : (Collection<?>) value) {
						checkType(entity, name, elType, val);
                    }
                } else if (value instanceof Map) {
                    Type keyType = pt.getActualTypeArguments()[0];
                    Type valType = pt.getActualTypeArguments()[1];
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
						checkType(entity, name, keyType, entry.getKey());
						checkType(entity, name, valType, entry.getValue());
                    }
                } else {
                    // With custom types possible, this is not good
                    // throw new IllegalStateException("Unrecognized entity field type (" + entity.getName() + "." + name + "): "
                    // + toString(pt.getRawType()));
                }
            } else if (type instanceof NotFoundType) {
                if (!(value instanceof GenericEntity)
                        || !((GenericEntity) value).getType().getName().equals(((NotFoundType) type).name)) {
                    throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " is not valid for field "
                            + entity.getName() + "." + name + " (type " + PersistenceUtils.toString(type) + ")");
                }
            } else {
				throw new IllegalArgumentException("Unrecognized type type: " + type.getClass().getName());
			}
        }
    }

    /**
     * @param type
     *            Type from which to get the referenced entity for a mapping type
     * @return The referenced entity for the mapped field
     * @throws IllegalStateException
     *             If the given type cannot be the type of a mapped field
     */
    public static EntityType getMappedType(Type type) throws IllegalStateException {
        if (type instanceof EntityType) {
            return (EntityType) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paraType = (ParameterizedType) type;
            if (paraType.getActualTypeArguments().length != 1 || !isConvertible(Collection.class, paraType.getRawType())) {
                throw new IllegalArgumentException("The type for a mapped field must be an entity or a collection of entities: " + type);
            }
            if (!(paraType.getActualTypeArguments()[0] instanceof EntityType)) {
                throw new IllegalArgumentException("The type for a mapped field must be an entity or a collection of entities: " + type);
            }
            return (EntityType) paraType.getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("The type for a mapped field must be an entity or a collection of entities: " + type);
        }
    }

    /**
     * @param field
     *            The field to check the mapping of
     * @param types
     *            The type set to use to check
     * @return The field referred to by the given field's mapped field, or null if the given field is not a mapped field
     * @throws IllegalStateException
     *             If the field's mapping field reference cannot be resolved or is invalid
     */
    public static EntityField getMappedField(EntityField field, EntityTypeSet types) throws IllegalStateException {
        if (field.getMappingField() != null) {
            EntityType refType = getMappedType(field.getType());
            refType = types.getEntityType(refType.getName());
            EntityField refField = refType.getField(field.getMappingField());
            if (refField == null) {
                throw new IllegalStateException("Mapping field " + field.getMappingField() + " for field " + field + " does not exist.");
            }
            if (!refField.getType().equals(field.entity)) {
                throw new IllegalStateException("Mapping field " + refField + " for field " + field + " refers to " + refField.getType()
                        + ", not " + field.entity);
            }
            return refField;
        }
        return null;
    }

    /**
     * @param from
     *            The type to assign to (left side)
     * @param to
     *            The type being assigned (right side)
     * @return Whether the given types can be assigned
     */
    public static boolean isConvertible(Type from, Type to) {
        if (equals(from, to)) {
            return true;
        }
        if (from instanceof Class && to instanceof Class) {
            Class<?> fromC = (Class<?>) from;
            Class<?> toC = (Class<?>) to;
            if (fromC.isAssignableFrom(toC)) {
                return true;
            }
            if (Boolean.class.equals(from) || Boolean.TYPE.equals(from)) {
                return Boolean.class.equals(to) || Boolean.class.equals(from);
            } else if (Character.class.equals(from) || Character.TYPE.equals(from)) {
                return Character.class.equals(to) || Character.class.equals(from);
            } else if (Double.class.equals(from) || Double.TYPE.equals(from) || Float.class.equals(from) || Float.TYPE.equals(from)) {
                return Double.class.equals(to) || Double.TYPE.equals(to) || Float.class.equals(to) || Float.TYPE.equals(to)
                        || Long.class.equals(to) || Long.TYPE.equals(to) || Integer.class.equals(to) || Integer.TYPE.equals(to);
            } else if (Long.class.equals(from) || Long.TYPE.equals(from)) {
                return Long.class.equals(to) || Long.TYPE.equals(to) || Integer.class.equals(to) || Integer.TYPE.equals(to);
            } else if (Integer.class.equals(from) || Integer.TYPE.equals(from)) {
                return Integer.class.equals(to) || Integer.TYPE.equals(to) || Character.class.equals(to) || Character.TYPE.equals(to);
            } else if (Character.class.equals(from) || Character.TYPE.equals(from) || Short.class.equals(from) || Short.TYPE.equals(from)) {
                return Character.class.equals(to) || Character.TYPE.equals(to) || Short.class.equals(to) || Short.TYPE.equals(to);
            } else if (Byte.class.equals(from) || Byte.TYPE.equals(from)) {
                return Byte.class.equals(to) || Byte.TYPE.equals(to);
            } else {
                return false;
            }
        } else if(from instanceof ParameterizedType){
            ParameterizedType paraFrom=(ParameterizedType) from;
            if(to instanceof ParameterizedType){
                ParameterizedType paraTo = (ParameterizedType) to;
                if (paraFrom.getActualTypeArguments().length != paraTo.getActualTypeArguments().length) {
                    return false;
                }
                if (!isConvertible(paraFrom.getRawType(), paraTo.getRawType())) {
                    return false;
                }
                for (int i = 0; i < paraFrom.getActualTypeArguments().length; i++) {
                    if (!isConvertible(paraFrom.getActualTypeArguments()[i], paraTo.getActualTypeArguments()[i])) {
                        return false;
                    }
                }
                return true;
            } else{
                return isConvertible(paraFrom.getRawType(), to);
            }
        } else if (from instanceof EntityType && to instanceof EntityType) {
            return ((EntityType) to).isAssignableFrom((EntityType) from);
        } else {
            return false;
        }
    }
    
    /**
     * @param typeName
     *            The name of the type to get
     * @param entityTypes
     *            All entity types available
     * @param typeGetter
     *            The type getter to allow injection of types not accessible here
     * @return The type with the given name. May be a Class, a {@link ParameterizedType}, or a {@link NotFoundType}
     */
    public static Type getType(String typeName, EntityTypeSet entityTypes, TypeGetter typeGetter) {
        Type type;
        Pattern arrayPattern = Pattern.compile("(.+)\\[\\s*\\]");
        Matcher arrayMatcher = arrayPattern.matcher(typeName);
        if (arrayMatcher.matches()) {
            Type componentType = getType(arrayMatcher.group(1).trim(), entityTypes, typeGetter);
            type = new ArrayTypeImpl(componentType);
        }
        int paramIdx = typeName.indexOf('<');
        if (paramIdx >= 0 && typeName.endsWith(">")) {
            Type rawType = getType(typeName.substring(0, paramIdx).trim(), entityTypes, typeGetter);
            ArrayList<Type> params = new ArrayList<>();
            typeName = typeName.substring(paramIdx + 1);
            do {
                paramIdx = typeName.indexOf(',');
                if (paramIdx < 0) {
                    paramIdx = typeName.indexOf('>');
                }
                params.add(getType(typeName.substring(0, paramIdx).trim(), entityTypes, typeGetter));
                typeName = typeName.substring(paramIdx + 1);
            } while (typeName.length() > 1);
            type = new ParameterizedTypeImpl(rawType, params.toArray(new Type[params.size()]));
        } else if ("long".equals(typeName)) {
            type = Long.TYPE;
        } else if ("int".equals(typeName)) {
            type = Integer.TYPE;
        } else if ("double".equals(typeName)) {
            type = Double.TYPE;
        } else if ("float".equals(typeName)) {
            type = Float.TYPE;
        } else if ("short".equals(typeName)) {
            type = Short.TYPE;
        } else if ("byte".equals(typeName)) {
            type = Byte.TYPE;
        } else if ("char".equals(typeName)) {
            type = Character.TYPE;
        } else if ("boolean".equals(typeName)) {
            type = Boolean.TYPE;
        } else if (entityTypes.getEntityType(typeName) != null) {
            return entityTypes.getEntityType(typeName);
        } else if (entityTypes.getEnumType(typeName) != null) {
            return entityTypes.getEnumType(typeName);
        } else {
            String simpleName = simpleName(typeName);
            EnumType enumType = entityTypes.getEnumType(simpleName);
            try {
                if (typeGetter != null) {
					type = typeGetter.getType(typeName);
				} else {
					type = Class.forName(typeName);
				}
            } catch (ClassNotFoundException e) {
                // May be a legacy data set referring to an enum type that has been removed or renamed
                if (enumType != null) {
					return enumType;
				}
                type = new NotFoundType(typeName);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to find type " + typeName, e);
            }
            if (type instanceof Class && Enum.class.isAssignableFrom((Class<?>) type)) {
                // Must be a legacy data set referring to an enum type.
                // Convert to the migratable form.
                if (enumType != null) {
					return enumType;
				}
                throw new IllegalStateException("Unrecognized enum type: " + typeName);
            }
        }
        return type;
    }

    private static String simpleName(String className) {
        int idx = className.lastIndexOf('.');
        if (idx >= 0) {
			className = className.substring(idx + 1);
		}
        idx = className.lastIndexOf('$');
        if (idx >= 0) {
			className = className.substring(idx + 1);
		}
        return PersistenceUtils.javaToXml(className);
    }

    /**
     * Converts a type to use entity types if possible
     *
     * @param type
     *            the type to convert
     * @param entityTypes
     *            All entity types available
     * @return The converted type
     */
    public static Type convertToEntity(Type type, EntityTypeSet entityTypes) {
        if (type instanceof Class) {
            if (entityTypes.getEntityType((Class<?>) type) != null) {
                return entityTypes.getEntityType((Class<?>) type);
            } else if (entityTypes.getEnumType((Class<?>) type) != null) {
                return entityTypes.getEnumType((Class<?>) type);
            } else {
                return type;
            }
        } else if (type instanceof ArrayTypeImpl) {
            Type comp = ((ArrayTypeImpl) type).getComponentType();
            Type conv = convertToEntity(comp, entityTypes);
            if (conv != comp) {
                return new ArrayTypeImpl(conv);
            } else {
                return type;
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] args = new Type[pt.getActualTypeArguments().length];
            boolean converted = false;
            for (int a = 0; a < args.length; a++) {
                args[a] = convertToEntity(pt.getActualTypeArguments()[a], entityTypes);
                if (args[a] != pt.getActualTypeArguments()[a]) {
                    converted = true;
                }
            }
            if (converted) {
                return new ParameterizedTypeImpl(pt.getRawType(), args);
            } else {
                return type;
            }
        } else if (type instanceof NotFoundType) {
            if (entityTypes.getEntityType(((NotFoundType) type).name) != null) {
                return entityTypes.getEntityType(((NotFoundType) type).name);
            } else {
                return type;
            }
        } else {
            return type;
        }
    }

    /**
     * @param type
     *            The type to print
     * @return The string representation of the type
     */
    public static String toString(Type type) {
        if (type instanceof NotFoundType) {
            return ((NotFoundType) type).name;
        } else if (type instanceof Class) {
            Class<?> c = (Class<?>) type;
            if (c.isPrimitive()) {
                return c.getSimpleName();
            } else {
                return ((Class<?>) type).getName();
            }
        } else if (type instanceof ArrayTypeImpl) {
            return toString(((ArrayTypeImpl) type).getComponentType()) + " []";
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            StringBuilder ret = new StringBuilder(toString(pt.getRawType()));
            ret.append('<');
            for (int i = 0; i < pt.getActualTypeArguments().length; i++) {
                if (i > 0) {
                    ret.append(", ");
                }
                ret.append(toString(pt.getActualTypeArguments()[i]));
            }
            ret.append('>');
            return ret.toString();
        } else if (type instanceof EntityType || type instanceof EnumType) {
            return type.toString();
        } else {
            throw new IllegalArgumentException("Unacceptable type: " + type.getClass().getName() + ": " + type);
        }
    }

    /**
     * @param type
     *            The type to print
     * @return The simple name of the type
     */
    public static String toShortString(Type type) {
        if (type instanceof NotFoundType) {
            return shorten(((NotFoundType) type).name);
        } else if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        } else if (type instanceof ArrayTypeImpl) {
            return toShortString(((ArrayTypeImpl) type).getComponentType()) + " []";
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            StringBuilder ret = new StringBuilder(toShortString(pt.getRawType()));
            ret.append('<');
            for (int i = 0; i < pt.getActualTypeArguments().length; i++) {
                if (i > 0) {
                    ret.append(", ");
                }
                ret.append(toShortString(pt.getActualTypeArguments()[i]));
            }
            ret.append('>');
            return ret.toString();
        } else if (type instanceof EntityType) {
            return type.toString();
        } else {
            throw new IllegalArgumentException("Unacceptable type: " + type.getClass().getName() + ": " + type);
        }
    }

    private static String shorten(String name) {
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx < 0) {
            return name;
        } else {
            return name.substring(dotIdx + 1);
        }
    }

    /**
     * Compares two types for equality
     *
     * @param type1
     *            The first type to compare
     * @param type2
     *            The second type to compare
     * @return Whether the two types are equivalent
     */
    public static boolean equals(Type type1, Type type2) {
        if (type1 instanceof NotFoundType) {
            if (type2 instanceof NotFoundType) {
                return ((NotFoundType) type1).name.equals(((NotFoundType) type2).name);
            } else if (type2 instanceof Class) {
                return ((NotFoundType) type1).name.equals(((Class<?>) type2).getName());
            } else {
                return false;
            }
        } else if (type1 instanceof Class) {
            if (type2 instanceof NotFoundType) {
                return ((Class<?>) type1).getName().equals(((NotFoundType) type2).name);
            } else if (type2 instanceof Class) {
                return ((Class<?>) type1).getName().equals(((Class<?>) type2).getName());
            } else {
                return false;
            }
        } else if (type1 instanceof ArrayTypeImpl) {
            if (type2 instanceof ArrayTypeImpl) {
                return equals(((ArrayTypeImpl) type1).getComponentType(), ((ArrayTypeImpl) type2).getComponentType());
            } else {
                return false;
            }
        } else if (type1 instanceof ParameterizedType) {
            if (type2 instanceof ParameterizedType) {
                ParameterizedType pt1 = (ParameterizedType) type1;
                ParameterizedType pt2 = (ParameterizedType) type2;
                if (!equals(pt1.getRawType(), pt2.getRawType())) {
                    return false;
                }
                if (pt1.getActualTypeArguments().length != pt2.getActualTypeArguments().length) {
                    return false;
                }
                for (int i = 0; i < pt1.getActualTypeArguments().length; i++) {
                    if (!equals(pt1.getActualTypeArguments()[i], pt2.getActualTypeArguments()[i])) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        } else if (type1 instanceof EntityType) {
            if (type2 instanceof EntityType) {
                return ((EntityType) type1).getName().equals(((EntityType) type2).getName());
            } else {
                return false;
            }
        } else {
            return type1.equals(type2);
        }
    }

    /**
     * @param type
     *            The type to hash
     * @return The hash code for the given type
     */
    public static int hashCode(Type type) {
        if (type instanceof NotFoundType) {
            return ((NotFoundType) type).name.hashCode();
        } else if (type instanceof Class) {
            return ((Class<?>) type).getName().hashCode();
        } else if (type instanceof ArrayTypeImpl) {
            return -((ArrayTypeImpl) type).getComponentType().hashCode();
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            int ret = hashCode(pt.getRawType());
            for (Type arg : pt.getActualTypeArguments()) {
                ret = ret * 13 + hashCode(arg);
            }
            return ret;
        } else if (type instanceof EntityType) {
            return ((EntityType) type).getName().hashCode();
        } else if (type instanceof EnumType) {
            return ((EnumType) type).getName().hashCode();
        } else {
            throw new IllegalArgumentException("Unacceptable type: " + type.getClass().getName() + ": " + type);
        }
    }

    /**
     * @param file
     *            The file to delete
     * @return Whether the delete was completely successful
     */
    public static boolean delete(File file) {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                delete(sub);
            }
        }
        return file.delete();
    }

    /**
     * @param type
     *            The type to test
     * @return Whether the given type is a parameterized collection or map type
     */
    public static boolean isCollectionOrMap(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType pt = (ParameterizedType) type;
        if (!(pt.getRawType() instanceof Class)) {
            return false;
        }
        return Collection.class.isAssignableFrom((Class<?>) pt.getRawType()) || Map.class.isAssignableFrom((Class<?>) pt.getRawType());
    }

    /**
     * @param type
     *            The type to check
     * @param dissecter
     *            The dissecter to understand data types
     * @param entityTypeTester
     *            The tester that can distinguish entity types from non-entity types
     * @return Whether the type or any of its type arguments are instances of {@link EntityType}
     */
    public static boolean hasEntityType(Type type, TypeSetDissecter dissecter, Predicate<Type> entityTypeTester) {
        return hasEntityType(new LinkedHashSet<>(), type, dissecter, entityTypeTester);
    }

    private static boolean hasEntityType(Set<Type> path, Type type, TypeSetDissecter dissecter, Predicate<Type> entityTypeTester) {
        if (entityTypeTester.test(type)) {
			return true;
		}
        if (!path.add(type)) {
			return false;
		}

        if (type instanceof EnumType) {
			return false;
		}
        Class<?> raw = getRawType(type);
        if (dissecter.isSimple(raw)) {
			return false;
		}
        DissecterGenerator dg = dissecter.getDissecter(raw);
        if (dg == null) {
			throw new IllegalStateException("Unrecognized type: " + toString(type));
		}
        Dissecter d = dg.dissect(type, null);
        if (d instanceof ValueDissecter) {
            ValueDissecter vd = (ValueDissecter) d;
            for (TypedField field : vd.getFields()) {
                if (hasEntityType(path, field.type, dissecter, entityTypeTester)) {
					return true;
				}
            }
            return false;
        } else if (d instanceof CollectionDissecter) {
            CollectionDissecter cd = (CollectionDissecter) d;
            return hasEntityType(path, cd.getComponentType(), dissecter, entityTypeTester);
        }
        throw new IllegalStateException("Unrecognized type: " + toString(type));
    }

    /**
     * @param type
     *            The possibly parameterized type to get the raw type of
     * @return The raw type of the type if it is parameterized. Otherwise just the type.
     */
    public static Class<?> getRawType(Type type) {
        if (type instanceof Class) {
			return (Class<?>) type;
		} else if (type instanceof ParameterizedType) {
			return (Class<?>) ((ParameterizedType) type).getRawType();
		} else {
			throw new IllegalStateException("Unrecognized type: " + toString(type));
		}
    }

    /**
     * @param raw
     *            The raw type to parameterize
     * @param params
     *            The parameter types for the type
     * @return The parameterized type
     */
    public static Type parameterize(Class<?> raw, Type... params) {
        if (raw.getTypeParameters().length != params.length) {
            throw new IllegalArgumentException("Parameterizing " + raw.getClass().getName()
                    + " with an incorrect number of parameters.  Should be " + raw.getTypeParameters() + ", but was "
                    + ArrayUtils.toString(params));
        }
        return new ParameterizedTypeImpl(raw, params);
    }
}
