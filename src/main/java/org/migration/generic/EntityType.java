package org.migration.generic;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Element;
import org.migration.TypeGetter;
import org.migration.TypedField;
import org.migration.ValueDissecter;
import org.migration.util.PersistenceUtils;
import org.qommons.ArrayUtils;
import org.qommons.IterableUtils;


/** Represents an entity and how it maps from one schema to another */
public class EntityType implements Type, Iterable<EntityField>, Cloneable {
    /** The type that this type inherits from */
    private EntityType theSuperType;

    /** The name of this entity as it was written to XML */
    private String theEntityName;

    private EntityField theIdField;

    private Map<String, EntityField> theFields;

    /**
     * @param superType
     *            The type that this type is to inherit from
     * @param name
     *            The name for this type
     */
    protected EntityType(EntityType superType, String name) {
        theSuperType = superType;
        theEntityName = name;
        theFields = new TreeMap<>();
    }

    /** @return The type that this type inherits from */
    public EntityType getSuperType() {
        return theSuperType;
    }

    void internalSetSuperType(EntityType superType) {
        theSuperType = superType;
    }

    /**
     * Replaces this entity's super-type
     * 
     * @param superType
     *            The new type to be the super-type of this type
     * @throws IllegalArgumentException
     *             If the given type is not valid as this type's super-type
     */
    public void setSuperType(EntityType superType) throws IllegalArgumentException {
        if (superType == theSuperType) {
			return;
		}
        if (superType != null) {
            // ID fields need to be the same
            EntityField oldId = getIdField();
            EntityField newId = superType.getIdField();
            if (oldId.getName().equals(newId.getName()) && oldId.getType().equals(newId.getType())
                    && oldId.isNullable() == newId.isNullable() && ArrayUtils.equals(oldId.getSorting(), newId.getSorting())) {
            } else {
				throw new IllegalArgumentException("Replacement super-type's ID field must be the same as this type");
			}

            EntityType st = superType;
            while (st != null) {
                if (st.getName().equals(getName())) {
					throw new IllegalArgumentException("Cyclic hierarchy detected: Replacement super-type " + superType.getName()
                            + " inherits from this type " + getName());
				}
                st = st.getSuperType();
            }
            st = this;
            while (st != null) {
                if (st.getName().equals(superType.getName())) {
					throw new IllegalArgumentException(
                            "This type " + getName() + " already inherits from replacement super-type " + superType.getName());
				}
                st = st.getSuperType();
            }
		} else {
			// Can't not have an ID
			theIdField = addField(theSuperType.getIdField().getName(), theSuperType.getIdField().getType(),
					theSuperType.getIdField().isNullable(), theSuperType.getIdField().getMappingField(),
					theSuperType.getIdField().getSorting());
        }
        EntityField[] oldSuperFields = theSuperType == null ? new EntityField[0]
                : theSuperType.theFields.values().toArray(new EntityField[0]);
        EntityField[] newSuperFields = superType == null ? new EntityField[0] : superType.theFields.values().toArray(new EntityField[0]);
        List<String> localFieldsToRemove = new ArrayList<>();
        List<EntityField> superFieldsToAdd = new ArrayList<>();
        ArrayUtils.adjust(oldSuperFields, newSuperFields, new ArrayUtils.DifferenceListener<EntityField, EntityField>() {
            @Override
            public boolean identity(EntityField o1, EntityField o2) {
                return o1.getName().equals(o2.getName());
            }

            @Override
            public EntityField added(EntityField o, int mIdx, int retIdx) {
                // New super type has a field that the old super type didn't have
                // If this entity type has the same field, we remove it from our local fields
                EntityField localField = theFields.get(o.getName());
                // The fields have to be the same
                if (localField == null) {
                    // If this type doesn't have the field, make them add it explicitly first
                    throw new IllegalArgumentException("Cannot replace super-type: No field " + getName() + "." + o.getName()
                            + " to be replaced by " + superType.getName() + "." + o.getName() + ". Add the " + o.getName() + " field to "
                            + getName() + " before replacing the super-type.");
                } else if (!localField.getType().equals(o.getType())) {
                    throw new IllegalArgumentException("Cannot replace super-type: " + superType.getName() + "." + o.getName()
                            + " has a different type (" + PersistenceUtils.toString(o.getType()) + ") than " + getName() + "."
                            + localField.getName() + " (" + PersistenceUtils.toString(localField.getType()) + ")");
                } else if (!ArrayUtils.equals(localField.getSorting(), o.getSorting())) {
                    throw new IllegalArgumentException("Cannot replace super-type: " + superType.getName() + "." + o.getName()
                            + "'s sorting (" + Arrays.toString(o.getSorting()) + ") is not the same as " + getName() + "."
                            + localField.getName() + " (" + Arrays.toString(localField.getSorting()) + ")");
                } else if (!localField.isNullable() && o.isNullable()) {
                    throw new IllegalArgumentException("Cannot replace super-type: " + superType.getName() + "." + o.getName()
                            + " may have null values which are not allowed in " + getName() + "." + localField.getName());
                } else {
                    localFieldsToRemove.add(o.getName());
                }
                return o;
            }

            @Override
            public EntityField removed(EntityField o, int oIdx, int incMod, int retIdx) {
				if (!o.isId()) { // Already accounted for ID
					// New super-type is missing a field that the old super type had
					// Add the field to this type
					superFieldsToAdd.add(o);
				}
                return null;
            }

            @Override
            public EntityField set(EntityField o1, int idx1, int incMod, EntityField o2, int idx2, int retIdx) {
                // Fields must be the same
                if (o1.getType().equals(o2.getType()) && o1.isNullable() == o2.isNullable()
                        && ArrayUtils.equals(o1.getSorting(), o2.getSorting())) {
                } else {
                    throw new IllegalArgumentException("Cannot replace super-type: " + superType.getName() + "." + o1.getName()
                            + " is not equivalent to " + theSuperType + "." + o1.getName());
                }
                return o2;
            }
        });
        internalSetSuperType(superType);
        theFields.keySet().removeAll(localFieldsToRemove);
        for (EntityField field : superFieldsToAdd) {
            EntityField newField = new EntityField(this, field.getName(), field.getType(), field.isNullable(), field.getMappingField(),
                    field.getSorting());
            theFields.put(field.getName(), newField);
        }
    }

    void checkFields() {
        if (theSuperType == null) {
			return;
		}
        theIdField = null;
        for (EntityField field : theSuperType) {
			theFields.remove(field.getName());
		}
        for (EntityField field : theFields.values()) {
			PersistenceUtils.checkSorting(field);
		}
    }

    /**
     * @param type
     *            The type to check
     * @return Whether this type is the same as or a super-type of the given type
     */
    public boolean isAssignableFrom(EntityType type) {
        while (type != null && !type.getName().equals(getName())) {
			type = type.getSuperType();
		}
        return type != null;
    }

    /**
     * @param type
     *            The entity class to populate the fields from
     * @param allTypes
     *            All entity types available
     * @param dissecter
     *            The dissecter to pull apart the type
     */
    protected void populateFields(Class<?> type, EntityTypeSet allTypes, ValueDissecter dissecter) {
        TypedField[] fields = dissecter.getFields();

        boolean hasId = false;
        for (TypedField field : fields) {
			if (field.id) {
                if (hasId) {
					throw new IllegalStateException("Multiple ID fields returned for type " + theEntityName + "(" + type.getName() + ")");
				}
                hasId = true;
            }
		}
        if (!hasId) {
			throw new IllegalStateException("No id field on type definition for " + theEntityName + " (" + type.getName() + ")");
		}

        for (TypedField field : fields) {
            if (theSuperType != null && theSuperType.getField(field.name) != null) {
				continue;
			}
            if (theFields.containsKey(field.name)) {
				throw new IllegalStateException("Duplicate fields \"" + field.name + "\" for type " + theEntityName + "(" + type.getName()
                        + ")");
			}
            Type fieldType;
            if (field.type instanceof Class && Enum.class.isAssignableFrom((Class<?>) field.type)) {
                EnumType enumType = allTypes.getEnumType((Class<?>) field.type);
                if (enumType == null) {
                    enumType = new EnumType(PersistenceUtils.javaToXml(((Class<?>) field.type).getSimpleName()));
                    enumType.populateValues((Class<? extends Enum<?>>) field.type);
                    allTypes.addType(enumType);
                    allTypes.map((Class<? extends Enum<?>>) field.type, enumType);
                }
                fieldType = enumType;
            } else {
				fieldType = PersistenceUtils.convertToEntity(field.type, allTypes);
			}
            EntityField entityField = new EntityField(this, field.name, fieldType, field.nullable, field.mapping, field.ordering);
            theFields.put(field.name, entityField);
            if (field.id) {
				theIdField = entityField;
			}
        }
    }

    /**
     * @param element
     *            The XML element representing the entity version
     * @param allTypes
     *            All entity types available
     * @param typeGetter
     *            The type getter to allow injection of types not accessible here
     */
    protected void populateFields(Element element, EntityTypeSet allTypes, TypeGetter typeGetter) {
        String superTypeName=element.getAttributeValue("extends");
        if (superTypeName != null) {
            theSuperType = allTypes.getEntityType(superTypeName);
            if (theSuperType == null) {
				throw new IllegalStateException("Unrecognized super type "+superTypeName+" for type "+getName());
			}
        }
        theFields = new TreeMap<>();
        for (Element fieldEl : element.getChildren()) {
            String fieldName = fieldEl.getName();
            if (theSuperType != null && theSuperType.getField(fieldName) != null) {
				continue;
			}
            String typeName = fieldEl.getAttributeValue("type");
            if (typeName == null) {
				continue;
			}
            Type fieldType;
            try {
                fieldType = PersistenceUtils.getType(typeName, allTypes, typeGetter);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException(e.getMessage() + " for field " + theEntityName + "." + fieldName, e);
            }
            theFields.put(fieldName,
                    new EntityField(this, fieldName, fieldType, !"false".equalsIgnoreCase(fieldEl.getAttributeValue("nullable")),
                            fieldEl.getAttributeValue("map"), MigratorFactory.split(fieldEl.getAttributeValue("sorting"))));
        }
        String idField = element.getAttributeValue("id");
        if (idField != null) {
            if (superTypeName != null) {
				throw new IllegalStateException("id specified for a sub-type: " + getName() + " extends " + superTypeName);
			}
            theIdField = theFields.get(idField);
            if (theIdField == null) {
				throw new IllegalStateException("ID field \""+idField+"\" for entity "+theEntityName+" does not exist");
			}
            if (theIdField.getType() == Integer.TYPE || theIdField.getType() == Integer.class || theIdField.getType() == Long.TYPE
                    || theIdField.getType() == Long.class || theIdField.getType() == String.class) {
            } else {
				throw new IllegalStateException("Illegal type for identifier field: " + PersistenceUtils.toString(theIdField.getType())
                        + " for field " + theEntityName + "." + theIdField.getName());
			}
        } else if (theSuperType == null) {
			throw new IllegalStateException("No id field on type definition for " + theEntityName);
		}
    }

    /**
     * Replaces a java type with an entity type in this entity type's fields
     * 
     * @param clazz
     *            The java type to replace
     * @param type
     *            The entity type to replace it with
     */
    protected void map(Class<?> clazz, Type type) {
        for (Map.Entry<String, EntityField> field : theFields.entrySet()) {
            EntityField f = field.getValue();
            if (f.getType() == clazz) {
                EntityField newField = new EntityField(this, f.getName(), type, f.isNullable(), f.getMappingField(), f.getSorting());
                field.setValue(newField);
                if (f.isId()) {
					theIdField = newField;
				}
            }
        }
    }

    /** @return This entity type's name */
    public String getName() {
        return theEntityName;
    }

    /**
     * @param name
     *            The name for this entity type
     */
    protected void setName(String name) {
        theEntityName = name;
    }

    /**
	 * @param name
	 *            The name for the new field
	 * @param type
	 *            The type for the new field
	 * @param nullable
	 *            Whether the new field is to be nullable
	 * @param map
	 *            The field on the target entity referring back to this type
	 * @param sorting
	 *            The columns to sort the field's collection value by
	 * @return The new field
	 */
	protected EntityField addField(String name, Type type, boolean nullable, String map, String[] sorting) {
		EntityField newField = new EntityField(this, name, type, nullable, map, sorting);
		theFields.put(name, newField);
		return newField;
    }

    /**
     * @param name
     *            The name of the field to remove
     * @return The removed field
     */
    protected EntityField removeField(String name) {
        if (theIdField != null && theIdField.getName().equals(name)) {
			throw new IllegalArgumentException("Cannot remove the ID field of an entity");
		}
        EntityField ret = theFields.remove(name);
        if (ret == null) {
			throw new IllegalArgumentException("No such field \"" + name + "\" in entity " + getName());
		}
        return ret;
    }

    /**
     * @param from
     *            The name of the field to rename
     * @param to
     *            The new name for the field
     */
    protected void renameField(String from, String to) {
        EntityField ret = theFields.remove(from);
        if (ret == null) {
			throw new IllegalArgumentException("No such field \"" + from + "\" in entity " + getName());
		}
        if(theFields.containsKey(to)) {
			throw new IllegalArgumentException("Cannot rename field "+this+" to "+to+": field already exists");
		}
        ret.setName(to);
        theFields.put(to, ret);
    }

    @Override
    public Iterator<EntityField> iterator() {
        Iterator<EntityField> ret = Collections.unmodifiableCollection(theFields.values()).iterator();
        if (theSuperType != null) {
			ret = IterableUtils.iterator(theSuperType.iterator(), ret);
		}
        return ret;
    }

    /** @return The field that serves as the identifier for this entity type */
    public EntityField getIdField() {
        if (theSuperType != null) {
			return theSuperType.getIdField();
		} else {
			return theIdField;
		}
    }

    /**
     * @param field
     *            The name of the field to get
     * @return The given field in this entity type, or null if no such field exists in this entity version
     */
    public EntityField getField(String field) {
        EntityField ret = theFields.get(field);
        if (ret == null && theSuperType != null) {
			ret = theSuperType.getField(field);
		}
        return ret;
    }

    /**
     * @param type
     *            The type to get references to
     * @return All references to the given type in this entity type's fields
     */
    public Collection<EntityReference> getReferences(Type type) {
        ArrayList<EntityReference> ret = new ArrayList<>();
        for (EntityField field : this) {
			if (isReference(field.getType(), type)) {
				ret.add(new EntityReference(field));
			} else if (PersistenceUtils.isCollectionOrMap(field.getType())) {
                ParameterizedType pType = (ParameterizedType) field.getType();
                if (Collection.class.isAssignableFrom((Class<?>) pType.getRawType())) {
                    if(isReference(pType.getActualTypeArguments()[0], type)) {
						ret.add(new EntityReference(field));
					}
                } else {
                    if(isReference(pType.getActualTypeArguments()[0], type)) {
						ret.add(new EntityReference(field, true));
					}
                    if (isReference(pType.getActualTypeArguments()[1], type)) {
						ret.add(new EntityReference(field, false));
					}
                }
            }
		}
        return ret;
    }

    private boolean isReference(Type fieldType, Type refType) {
        if (fieldType instanceof EntityType) {
            if (!(refType instanceof EntityType)) {
				return false;
			}
            return ((EntityType) fieldType).isAssignableFrom((EntityType) refType)//
                    || ((EntityType) refType).isAssignableFrom((EntityType) fieldType);
        } else if (fieldType instanceof EnumType) {
            if (!(refType instanceof EnumType)) {
				return false;
			}
            return refType.equals(fieldType);
        } else {
			return false;
		}
    }

    /**
     * @param fieldName
     *            The name of the field to check the value against
     * @param value
     *            The value to check against the field
     * @throws IllegalArgumentException
     *             If the given value may not be assigned to the given field for any reason
     */
    public void checkFieldValue(String fieldName, Object value) throws IllegalArgumentException {
        EntityField field = getField(fieldName);
        if (field == null) {
			throw new IllegalArgumentException("No such field " + fieldName + " in entity " + getName());
		}
        PersistenceUtils.checkType(this, field.getName(), field.getType(), field.isNullable(), value);
    }

    /**
     * Replaces all types referenced by this type with the types of the same name in the given type set
     *
     * @param types
     *            The type set whose types to replace this type's referred types with
     */
    void replaceTypes(EntityTypeSet types) {
        if (theSuperType != null) {
			theSuperType = types.getEntityType(theSuperType.getName());
		}

        for (Map.Entry<String, EntityField> entry : theFields.entrySet()) {
            EntityField field = entry.getValue();
            Type newFieldType = types.replaceType(field.getType());
            if (newFieldType == null) {
				throw new IllegalStateException("New type set does not contain type for field " + field);
			}
            if (newFieldType == field.getType()) {
				continue;
			}

            EntityField newField = new EntityField(this, field.getName(), newFieldType, field.isNullable(), field.getMappingField(),
                    field.getSorting());
            theFields.put(field.getName(), newField);
            if (theIdField != null && theIdField.getName().equals(field.getName())) {
				theIdField = newField;
			}
        }
    }

    /** @return The XML element representing this entity version */
    public Element toElement() {
        Element ret = new Element(PersistenceUtils.javaToXml(theEntityName));
        if (theSuperType != null) {
			ret.setAttribute("extends", theSuperType.getName());
		} else {
			ret.setAttribute("id", theIdField.getName());
		}
        for (EntityField field : theFields.values()) {
            Element fieldEl = new Element(PersistenceUtils.javaToXml(field.getName()));
            ret.addContent(fieldEl);
            fieldEl.setAttribute("type", PersistenceUtils.toString(field.getType()));
            fieldEl.setAttribute("nullable", "" + field.isNullable());
            if (field.getSorting().length > 0) {
				fieldEl.setAttribute("sorting", MigratorFactory.join(field.getSorting()));
			}
            if (field.getMappingField() != null) {
				fieldEl.setAttribute("map", field.getMappingField());
			}
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
			return true;
		}
        if (!(o instanceof EntityType)) {
			return false;
		}
        EntityType ev = (EntityType) o;
        if (!ev.theEntityName.equals(theEntityName)) {
			return false;
		}
        if (theSuperType != null && (ev.theSuperType == null || !theSuperType.getName().equals(ev.theSuperType.getName()))) {
			return false;
		}
        if (theSuperType == null && ev.theSuperType != null) {
			return false;
		}
        if (theSuperType == null) {
			if (!ev.theIdField.getName().equals(theIdField.getName())) {
				return false;
			}
		}
        return true;
    }

    @Override
    public int hashCode() {
        int ret = theSuperType == null ? 0 : theSuperType.hashCode();
        ret = ret * 17 + theEntityName.hashCode();
        if (theIdField != null) {
			ret = ret * 13 + theIdField.getName().hashCode();
		}
        ret = ret * 7 + theFields.hashCode();
        return ret;
    }

    @Override
    public String toString() {
        return theEntityName;
    }

    @Override
    public EntityType clone() {
        EntityType ret;
        try {
            ret = (EntityType) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        ret.theFields = new TreeMap<>();
        for (EntityField field : theFields.values()) {
            EntityField newField = new EntityField(this, field.getName(), field.getType(), field.isNullable(), field.getMappingField(),
                    field.getSorting());
            ret.theFields.put(field.getName(), newField);
        }
        if (ret.theIdField != null) {
			ret.theIdField=ret.theFields.get(ret.theIdField.getName());
		}
        return ret;
    }
}