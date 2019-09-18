package org.migration.generic;

import java.lang.reflect.Type;

import org.migration.NotFoundType;
import org.migration.util.PersistenceUtils;

/** Represents a persistable field on an entity */
public class EntityField {
    /** The entity that this field belongs to */
    public final EntityType entity;

    /** The name of the field */
    private String theName;

    /**
     * The field's type. May be an instance of
     * <ul>
     * <li>{@link java.lang.Class Class}</li>
     * <li>{@link java.lang.reflect.ParameterizedType ParameterizedType}</li>
     * <li>{@link NotFoundType}</li>
     * </ul>
     */
    private final Type theType;

    private String theMappingField;

    private String[] theSorting;

    /**
     * @param anEntity
     *            The entity that this field belongs to
     * @param aName
     *            The field's name
     * @param aType
     *            The field's type
     * @param map
     *            The field on the target entity referring back to this field's type
     * @param sorting
     *            The columns that this field's collection is sorted by, in order
     */
	public EntityField(EntityType anEntity, String aName, Type aType, String map, String[] sorting) {
        if (anEntity == null)
            throw new NullPointerException("No entity for field");
        if (aName == null)
            throw new NullPointerException("No name for field");
        if (aType == null)
            throw new NullPointerException("No type for field");
        entity = anEntity;
        theName = PersistenceUtils.javaToXml(aName);
        theType = aType;
        theMappingField = map;
        theSorting = sorting;

        if (theMappingField != null)
            PersistenceUtils.getMappedType(theType);
    }

    /** @return The entity type that declares this field */
    public EntityType getDeclaringType() {
        return entity;
    }

    /** @return This field's name */
    public String getName() {
        return theName;
    }

    /** @return This field's type */
    public Type getType() {
        return theType;
    }

    /** @return The field on the target entity referring back to this field's type */
    public String getMappingField() {
        return theMappingField;
    }

    /** @return The columns that this field's collection is sorted by, in order */
    public String[] getSorting() {
        return theSorting;
    }

    /** @return Whether this is the entity's identifier field */
    public boolean isId() {
        return entity.getIdField().getName().equals(theName);
    }

    /**
     * @param name
     *            The new name for this field
     */
    protected void setName(String name) {
        theName = name;
    }

    @Override
    public boolean equals(Object o) {
		if (this == o)
			return true;
        if (!(o instanceof EntityField))
            return false;
        EntityField field = (EntityField) o;
		if (!field.entity.equals(entity))
			return false;
        if (!field.theName.equals(theName))
            return false;
        if (!PersistenceUtils.equals(field.theType, theType))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int ret = theName.hashCode();
        ret = ret * 13 + PersistenceUtils.hashCode(theType);
        return ret;
    }

    @Override
    public String toString() {
        return entity + "." + theName + " (" + PersistenceUtils.toString(theType) + ")";
    }
}
