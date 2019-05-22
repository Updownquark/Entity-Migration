package org.migration.generic;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.migration.util.PersistenceUtils;

/** A typed set of fields and their data */
public class GenericEntity {
    private final EntityType theOldType;
    private final EntityType theCurrentType;
    private final GenericEntitySet theEntitySet;

    private final Map<String, Object> theFieldMap;

    /**
     * @param oldType
     *            The type of the entity before the current migration
     * @param currentType
     *            The type of the entity at the current spot in the migration
     * @param entitySet
     *            The entity set that this GenericEntity belongs to
     */
    GenericEntity(EntityType oldType, EntityType currentType, GenericEntitySet entitySet) {
        theOldType = oldType;
        theCurrentType = currentType;
        theEntitySet = entitySet;
        theFieldMap = new java.util.TreeMap<>();
    }

    /** @return The value of this entity's identity field */
    public Object getIdentity() {
        EntityField field = theCurrentType.getIdField();
        if (field == null)
            return null;
        return get(field.getName());

    }

    /** @return The type of the entity at the current spot in the migration */
    public EntityType getCurrentType() {
        return theCurrentType;
    }

    /**
     * @param field
     *            The name field to get the value of
     * @return The value of the given field in this entity
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     */
    public Object get(String field) {
        if (theCurrentType.getField(field) == null && theOldType.getField(field) == null)
            throw new IllegalArgumentException("No such field \"" + field + "\" for type " + theOldType.getName());
        return theFieldMap.get(field);
    }

    void setIdentityInternal(Object value) {
        theFieldMap.put(theCurrentType.getIdField().getName(), value);
    }

    /**
     * @param field
     *            the name of the field to set the value for
     * @param value
     *            The new value for the field
     * @return This entity, for chaining
     * @throws IllegalArgumentException
     *             If the given field does not exist in the current version of this entity's type or if the given value may not be assigned
     *             to the field
     */
    public GenericEntity set(String field, Object value) {
        try {
            theCurrentType.checkFieldValue(field, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for field " + theCurrentType + "." + field + ": " + e.getMessage(), e);
        }
        Object oldValue = theFieldMap.put(field, value);
        if (theCurrentType.getIdField() != null && field.equals(theCurrentType.getIdField().getName()))
            theEntitySet.idChanged(this, oldValue, value);
        return this;
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to a boolean
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not a boolean
     */
    public boolean is(String field) {
        return (Boolean) get(field);
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to an integer
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not an integer
     */
    public int getInt(String field) {
        Number ret = (Number) get(field);
        if (ret instanceof Byte || ret instanceof Short || ret instanceof Integer)
            return ret.intValue();
        else
            throw new ClassCastException("Cannot cast from " + ret.getClass().getName() + " to int");
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to a long
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not a long
     */
    public long getLong(String field) {
        Number ret = (Number) get(field);
        if (ret instanceof Byte || ret instanceof Short || ret instanceof Integer || ret instanceof Long)
            return ret.longValue();
        else
            throw new ClassCastException("Cannot cast from " + ret.getClass().getName() + " to long");
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to a double
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not a double
     */
    public double getDouble(String field) {
        Number ret = (Number) get(field);
        if (ret instanceof Float || ret instanceof Double)
            return ret.doubleValue();
        else
            throw new ClassCastException("Cannot cast from " + ret.getClass().getName() + " to double");
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to a string
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not a string
     */
    public String getString(String field) {
        return (String) get(field);
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to an entity
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not an entity
     */
    public GenericEntity getEntity(String field) {
        return (GenericEntity) get(field);
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, cast to an enum constant
     * @throws IllegalArgumentException
     *             If no field with the given name exists in the current version of this entity's type
     * @throws ClassCastException
     *             If the value of the given field is not an enum constant
     */
    public EnumValue getEnum(String field) {
        return (EnumValue) get(field);
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, checked and cast as a collection of entities
     */
    public Collection<GenericEntity> getEntityCollection(String field) {
        EntityField f = theCurrentType.getField(field);
        if (f == null)
            f = theOldType.getField(field);
        if (f == null)
            throw new IllegalArgumentException("No such field " + field + " for type " + theOldType.getName());
        Type type = f.getType();
        if (!(type instanceof ParameterizedType) || !Collection.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType()))
            throw new IllegalArgumentException(
                    "Field " + f + "'s type is " + PersistenceUtils.toString(type) + ", not a collection of entities");
        if (!(((ParameterizedType) type).getActualTypeArguments()[0] instanceof EntityType))
            throw new IllegalArgumentException(
                    "Field " + f + "'s type is " + PersistenceUtils.toString(type) + ", not a collection of entities");
        return (Collection<GenericEntity>) get(field);
    }

    /**
     * @param field
     *            The name of the field to get the value of
     * @return The value of the given field, checked and cast as a list of entities
     */
    public List<GenericEntity> getEntityList(String field) {
        EntityField f = theCurrentType.getField(field);
        if (f == null)
            f = theOldType.getField(field);
        if (f == null)
            throw new IllegalArgumentException("No such field " + field + " for type " + theOldType.getName());
        Type type = f.getType();
        if (!(type instanceof ParameterizedType) || !List.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType()))
            throw new IllegalArgumentException("Field " + f + "'s type is " + PersistenceUtils.toString(type) + ", not a list of entities");
        if (!(((ParameterizedType) type).getActualTypeArguments()[0] instanceof EntityType))
            throw new IllegalArgumentException("Field " + f + "'s type is " + PersistenceUtils.toString(type) + ", not a list of entities");
        return (List<GenericEntity>) get(field);
    }

	/**
	 * Copies all non-identity field data from one entity into another. The entity must be related to this entity by inheritance, either a
	 * super- or a sub-type.
	 * 
	 * @param entity
	 *            The entity whose data to copy into this entity
	 * @return This entity
	 */
	public GenericEntity copyFrom(GenericEntity entity) {
		boolean argIsSuper = entity.theCurrentType.isAssignableFrom(theCurrentType);
		if (!argIsSuper && !theCurrentType.isAssignableFrom(entity.theCurrentType))
			throw new IllegalArgumentException("copyFrom may only be used on a related entity");

		for (EntityField field : (argIsSuper ? entity.theCurrentType : theCurrentType)) {
			if (field.isId())
				continue;
			set(field.getName(), entity.get(field.getName()));
		}
		return this;
	}

    /**
     * @param field
     *            The name of the field that has been removed from this entity's current type
     */
    protected void fieldRemoved(String field) {
        theFieldMap.remove(field);
    }

    @Override
    public int hashCode() {
        int hash = getCurrentType().hashCode() * 17;
        Object id = getIdentity();
        if (id == null)
            return hash;
        return hash + id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        return obj instanceof GenericEntity && ((GenericEntity) obj).getCurrentType().equals(getCurrentType())
                && Objects.equals(((GenericEntity) obj).getIdentity(), getIdentity());
    }

    @Override
    public String toString() {
        return theCurrentType.getName() + " " + theCurrentType.getIdField().getName() + "=" + get(theCurrentType.getIdField().getName());
    }
}
