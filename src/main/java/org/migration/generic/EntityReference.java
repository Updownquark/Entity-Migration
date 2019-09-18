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

import org.migration.util.PersistenceUtils;

/** A entity field's reference to another entity */
public class EntityReference {
    private final EntityField theReference;
    private final EntityField theReverseMappingField;
    private final boolean isMapKey;

    /**
     * @param reference
     *            The field that is the reference
     */
    public EntityReference(EntityField reference) {
        this(reference, false);
    }

    /**
     * @param reference
     *            The field that is the reference
     * @param isKey
     *            Whether the reference is as a map key (as opposed to the map value)
     */
    public EntityReference(EntityField reference, boolean isKey) {
        EntityType entityRef;
        if (reference.getType() instanceof EntityType)
            entityRef=(EntityType) reference.getType();
        else if (PersistenceUtils.isCollectionOrMap(reference.getType())) {
            ParameterizedType type = (ParameterizedType) reference.getType();
            Class<?> raw = (Class<?>) type.getRawType();
            if (Collection.class.isAssignableFrom(raw)) {
                if (!(type.getActualTypeArguments()[0] instanceof EntityType))
                    throw new IllegalArgumentException("Elements of collection field " + reference + " are not entities");
                entityRef = (EntityType) type.getActualTypeArguments()[0];
            } else if (isKey) {
                if (!(type.getActualTypeArguments()[0] instanceof EntityType))
                    throw new IllegalArgumentException("Keys of map field " + reference + " are not entities");
                entityRef = (EntityType) type.getActualTypeArguments()[0];
            } else {
                if (!(type.getActualTypeArguments()[1] instanceof EntityType))
                    throw new IllegalArgumentException("Values of map field " + reference + " are not entities");
                entityRef = (EntityType) type.getActualTypeArguments()[1];
            }
        } else if (reference.getType() instanceof EnumType) {
            entityRef = null;
        } else
            throw new IllegalArgumentException("Field " + reference + "'s type cannot be an entity reference");
        theReference = reference;
        isMapKey = isKey;

        EntityField reverse = null;
        if (reference.getType() instanceof EntityType)
            for(EntityField field : entityRef){
                if(!reference.getName().equals(field.getMappingField()))
                    continue;
                if(field.getType() ==reference.getDeclaringType()){
                    reverse=field;
                    break;
                }
                if(!(field.getType() instanceof ParameterizedType))
                    continue;
                ParameterizedType pt=(ParameterizedType) field.getType();
                if(Collection.class.isAssignableFrom((Class<?>) pt.getRawType()) && pt.getActualTypeArguments()[0]==reference.getDeclaringType()){
                    reverse=field;
                    break;
                } else if(Map.class.isAssignableFrom((Class<?>) pt.getRawType()) && pt.getActualTypeArguments()[0]==reference.getDeclaringType()){
                    reverse=field;
                    break;
                }
            }
        theReverseMappingField = reverse;
    }

    /** @return The field that is the reference */
    public EntityField getReferenceField() {
        return theReference;
    }

    /** @return The entity or enum that is referred to by this reference */
    public Type getReferenceType() {
        if (theReference.getType() instanceof EntityType)
            return theReference.getType();
        else if (theReference.getType() instanceof EnumType)
            return theReference.getType();
        ParameterizedType type = (ParameterizedType) theReference.getType();
        Class<?> raw = (Class<?>) type.getRawType();
        if (Collection.class.isAssignableFrom(raw))
            return type.getActualTypeArguments()[0];
        else if (isMapKey)
            return type.getActualTypeArguments()[0];
        else
            return type.getActualTypeArguments()[1];
    }

    /** @return Whether this reference is to the key of a map type (as opposed to the value) */
    public boolean isMapKey() {
        return isMapKey;
    }

    /** @return The field on the referred entity that uses this reference as a mapping field */
    public EntityField getReverseMappingField() {
        return theReverseMappingField;
    }

    /**
     * @param entity
     *            The entity to get references to
     * @param entitySet
     *            The entity set
     * @param withDirectRefs
     *            Whether to include entities that contain a direct reference to the entity
     * @param withCollectionRefs
     *            Whether to include entity that contain the given entity in a collection field
     * @return All entities in the entity set that contain a reference to the given entity
     */
	public Collection<GenericEntity> getReferring(GenericEntity entity, GenericEntitySet entitySet, boolean withDirectRefs,
            boolean withCollectionRefs) {
        if (!(getReferenceType() instanceof EntityType))
            throw new IllegalStateException("This method may only be called if the reference type is an entity, not an enum");
        if (!withDirectRefs && theReference.getType() instanceof EntityType)
            return Collections.EMPTY_LIST;
        if (!withCollectionRefs && !(theReference.getType() instanceof EntityType))
            return Collections.EMPTY_LIST;

        EntityType refType = (EntityType) getReferenceType();
        if (!refType.isAssignableFrom(entity.getType()))
            throw new IllegalArgumentException(entity.getType() + " is not a subtype of this reference (" + refType + ")");
        if (!refType.isAssignableFrom(entity.getType()))
            return Collections.EMPTY_LIST;
        if (theReverseMappingField != null) {
            if (theReverseMappingField.getType() instanceof EntityType)
                return Arrays.asList(entity.getEntity(theReverseMappingField.getName()));
            ParameterizedType pt = (ParameterizedType) theReverseMappingField.getType();
            if (Collection.class.isAssignableFrom((Class<?>) pt.getRawType()))
                return entity.getEntityCollection(theReverseMappingField.getName());
            else
                return ((Map<GenericEntity, ?>) entity.get(theReverseMappingField.getName())).keySet();
        } else if (theReference.getMappingField() != null) {
            for (EntityField field : refType)
                if (field.getName().equals(theReference.getMappingField())) {
                    if (entity.getEntity(field.getName()) != null)
                        return Arrays.asList(entity.getEntity(field.getName()));
                    else
                        return Collections.EMPTY_LIST;
                }
            throw new IllegalStateException("No such mapping field found: " + refType + "." + theReference.getMappingField());
		} else if (theReference.getType() instanceof EntityType) {
			return entitySet.query(theReference, entity);
        } else {
            ArrayList<GenericEntity> ret = new ArrayList<>();
			for (GenericEntity e : entitySet.queryAll(theReference.getDeclaringType().getName())) {
				ParameterizedType type = (ParameterizedType) theReference.getType();
				Class<?> raw = (Class<?>) type.getRawType();
				if (Collection.class.isAssignableFrom(raw)) {
					if (((Collection<GenericEntity>) e.get(theReference.getName())).contains(entity))
                        ret.add(e);
				} else if (isMapKey) {
					if (((Map<GenericEntity, ?>) e.get(theReference.getName())).containsKey(entity))
                        ret.add(e);
				} else if (((Map<?, GenericEntity>) e.get(theReference.getName())).containsValue(entity))
					ret.add(e);
			}
            return Collections.unmodifiableList(ret);
        }
    }

    /**
     * @param value
     *            The enum constant to get references to
     * @param entitySet
     *            The entity set
     * @param withDirectRefs
     *            Whether to include entities that contain a direct reference to the entity
     * @param withCollectionRefs
     *            Whether to include entity that contain the given entity in a collection field
     * @return All entities in the entity set that contain a reference to the given entity
     */
	public Collection<GenericEntity> getReferring(EnumValue value, GenericEntitySet entitySet, boolean withDirectRefs,
            boolean withCollectionRefs) {
        if (!(getReferenceType() instanceof EnumType))
            throw new IllegalStateException("This method may only be called if the reference type is an enum, not an entity");
        EnumType refType = (EnumType) getReferenceType();
        if (!refType.equals(value.getEnumType()))
            throw new IllegalArgumentException(
                    value.getEnumType() + " is not a subtype of this reference (" + refType + ")");
        if (!withDirectRefs && theReference.getType() instanceof EnumType)
            return Collections.EMPTY_LIST;
        if (!withCollectionRefs && !(theReference.getType() instanceof EnumType))
            return Collections.EMPTY_LIST;

        ArrayList<GenericEntity> ret = new ArrayList<>();
		if (theReference.getType() instanceof EnumType)
			return entitySet.query(theReference, value);
		for (GenericEntity e : entitySet.queryAll(theReference.getDeclaringType().getName())) {
			ParameterizedType type = (ParameterizedType) theReference.getType();
			Class<?> raw = (Class<?>) type.getRawType();
			if (Collection.class.isAssignableFrom(raw)) {
				if (((Collection<EnumValue>) e.get(theReference.getName())).contains(value))
                    ret.add(e);
			} else if (isMapKey) {
				if (((Map<EnumValue, ?>) e.get(theReference.getName())).containsKey(value))
                    ret.add(e);
			} else if (((Map<?, EnumValue>) e.get(theReference.getName())).containsValue(value))
				ret.add(e);
		}
        return Collections.unmodifiableList(ret);
    }

    /**
     * Deletes a reference to {@code toDelete} in {@code entity}
     * 
     * @param entity
     *            The entity to delete the reference in
     * @param toDelete
     *            The referenced entity to delete the reference to
     * @return Whether the given referring entity will need to be deleted in order to delete the reference (one-to-one relationship)
     */
    public boolean delete(GenericEntity entity, GenericEntity toDelete) {
        if (!theReference.entity.isAssignableFrom(entity.getType()))
            throw new IllegalArgumentException(entity.getType() + " is not a subtype of this reference's referrer ("
                    + theReference.entity + ")");
        if (!(getReferenceType() instanceof EntityType))
            throw new IllegalStateException("This method may only be called if the reference type is an entity, not an enum");
        EntityType refType = (EntityType) getReferenceType();
        if (!refType.isAssignableFrom(toDelete.getType()))
            throw new IllegalArgumentException(toDelete.getType() + " is not a subtype of this reference (" + refType + ")");
        if (theReference.getType() instanceof EntityType) {
            GenericEntity fieldValue = entity.getEntity(theReference.getName());
            return fieldValue != null && fieldValue.equals(toDelete);
        }
        ParameterizedType type = (ParameterizedType) theReference.getType();
        Class<?> raw = (Class<?>) type.getRawType();
        if (Collection.class.isAssignableFrom(raw))
            removeAll((Collection<GenericEntity>) entity.get(theReference.getName()), toDelete);
        else if (isMapKey)
            removeAll(((Map<GenericEntity, ?>) entity.get(theReference.getName())).keySet(), toDelete);
        else
            removeAll(((Map<?, GenericEntity>) entity.get(theReference.getName())).values(), toDelete);
        return false;
    }

    private boolean removeAll(Collection<GenericEntity> collection, GenericEntity entity) {
        boolean found = false;
        // Remove all occurrences of the given entity in the collection
        while (collection.remove(entity))
            found = true;
        return found;
    }

    /**
     * Deletes a reference to {@code toDelete} in {@code entity}
     * 
     * @param entity
     *            The entity to delete the reference in
     * @param toDelete
     *            The referenced enum value to delete the reference to
     * @return Whether the given referring entity will need to be deleted in order to delete the reference (one-to-one relationship)
     */
    public boolean delete(GenericEntity entity, EnumValue toDelete) {
        if (!theReference.entity.isAssignableFrom(entity.getType()))
            throw new IllegalArgumentException(
                    entity.getType() + " is not a subtype of this reference's referrer (" + theReference.entity + ")");
        if (!(getReferenceType() instanceof EnumType))
            throw new IllegalStateException("This method may only be called if the reference type is an enum, not an entity");
        EnumType refType = (EnumType) getReferenceType();
        if (!refType.equals(toDelete.getEnumType()))
            throw new IllegalArgumentException(toDelete.getEnumType() + " is not an instance of this reference type (" + refType + ")");
        if (theReference.getType() instanceof EnumType) {
            EnumValue fieldValue = entity.getEnum(theReference.getName());
            return fieldValue != null && fieldValue.equals(toDelete);
        }
        ParameterizedType type = (ParameterizedType) theReference.getType();
        Class<?> raw = (Class<?>) type.getRawType();
        if (Collection.class.isAssignableFrom(raw))
            removeAll((Collection<EnumValue>) entity.get(theReference.getName()), toDelete);
        else if (isMapKey)
            removeAll(((Map<EnumValue, ?>) entity.get(theReference.getName())).keySet(), toDelete);
        else
            removeAll(((Map<?, EnumValue>) entity.get(theReference.getName())).values(), toDelete);
        return false;
    }

    private boolean removeAll(Collection<EnumValue> collection, EnumValue enumValue) {
        boolean found = false;
        Iterator<EnumValue> iter = collection.iterator();
        while (iter.hasNext())
            if (iter.next().equals(enumValue)) {
                found = true;
                iter.remove();
            }
        return found;
    }

    /**
     * Replaces a reference to {@code toReplace} in {@code entity} with {@code replacement}
     * 
     * @param entity
     *            The entity to replace the reference in
     * @param toReplace
     *            The referred entity to replace
     * @param replacement
     *            The entity to replace as the reference to {@code toReplace} in {@code entity}
     */
    public void replace(GenericEntity entity, GenericEntity toReplace, GenericEntity replacement) {
        if (!(getReferenceType() instanceof EntityType))
            throw new IllegalStateException("This method may only be called if the reference type is an entity, not an enum");
        EntityType type = (EntityType) getReferenceType();
        if (!theReference.entity.isAssignableFrom(entity.getType()))
            throw new IllegalArgumentException(entity.getType() + " is not a subtype of this reference's referrer ("
                    + theReference.entity + ")");
        if (!type.isAssignableFrom(toReplace.getType()))
            throw new IllegalArgumentException(toReplace.getType()+" is not a subtype of this reference ("+type+")");
        else if(!type.isAssignableFrom(replacement.getType()))
            throw new IllegalArgumentException(replacement.getType()+" is not a subtype of this reference ("+type+")");

        if (theReference.getType() instanceof EntityType) {
            GenericEntity fieldValue = entity.getEntity(theReference.getName());
            if (fieldValue != null && fieldValue.equals(toReplace))
                entity.set(theReference.getName(), replacement);
        } else {
            ParameterizedType pType = (ParameterizedType) theReference.getType();
            Class<?> raw = (Class<?>) pType.getRawType();
            if (List.class.isAssignableFrom(raw)) {
                List<GenericEntity> list = (List<GenericEntity>) entity.get(theReference.getName());
                for (int i = 0; i < list.size(); i++)
                    if (list.get(i).equals(toReplace))
                        list.set(i, replacement);
            } else if (Collection.class.isAssignableFrom(raw)) {
                // Assume max one entry, e.g. a set
                if (removeAll((Collection<GenericEntity>) entity.get(theReference.getName()), toReplace))
                    ((Collection<GenericEntity>) entity.get(theReference.getName())).add(replacement);
            } else if (isMapKey) {
                Map<GenericEntity, ?> map = (Map<GenericEntity, ?>) entity.get(theReference.getName());
                for (Map.Entry<GenericEntity, ?> entry : map.entrySet())
                    if (entry.getKey().equals(toReplace)) {
                        ((Map<GenericEntity, Object>) map).put(replacement, map.remove(entry.getKey()));
                        break; // Assume only one key
                    }
            } else {
                Map<?, GenericEntity> map = (Map<?, GenericEntity>) entity.get(theReference.getName());
                for (Map.Entry<?, GenericEntity> entry : map.entrySet())
                    if (entry.getValue().equals(toReplace))
                        entry.setValue(replacement);
            }
        }
    }

    /**
     * Replaces a reference to {@code toReplace} in {@code entity} with {@code replacement}
     * 
     * @param entity
     *            The entity to replace the reference in
     * @param toReplace
     *            The referred enum value to replace
     * @param replacement
     *            The enum value to replace as the reference to {@code toReplace} in {@code entity}
     */
    public void replace(GenericEntity entity, EnumValue toReplace, EnumValue replacement) {
        if (!(getReferenceType() instanceof EnumType))
            throw new IllegalStateException("This method may only be called if the reference type is an enum, not an entity");
        EnumType type = (EnumType) getReferenceType();
        if (!theReference.entity.isAssignableFrom(entity.getType()))
            throw new IllegalArgumentException(
                    entity.getType() + " is not a subtype of this reference's referrer (" + theReference.entity + ")");
        if (!type.equals(toReplace.getEnumType()))
            throw new IllegalArgumentException(toReplace.getEnumType() + " is not an instance of this reference type (" + type + ")");
        else if (!type.equals(replacement.getEnumType()))
            throw new IllegalArgumentException(replacement.getEnumType() + " is not an instance of this reference type, (" + type + ")");

        if (theReference.getType() instanceof EnumType) {
            EnumValue fieldValue = entity.getEnum(theReference.getName());
            if (fieldValue != null && fieldValue.equals(toReplace))
                entity.set(theReference.getName(), replacement);
        } else {
            ParameterizedType pType = (ParameterizedType) theReference.getType();
            Class<?> raw = (Class<?>) pType.getRawType();
            if (List.class.isAssignableFrom(raw)) {
                List<EnumValue> list = (List<EnumValue>) entity.get(theReference.getName());
                for (int i = 0; i < list.size(); i++)
                    if (list.get(i).equals(toReplace))
                        list.set(i, replacement);
            } else if (Collection.class.isAssignableFrom(raw)) {
                // Assume max one entry, e.g. a set
                if (removeAll((Collection<EnumValue>) entity.get(theReference.getName()), toReplace))
                    ((Collection<EnumValue>) entity.get(theReference.getName())).add(replacement);
            } else if (isMapKey) {
                Map<EnumValue, ?> map = (Map<EnumValue, ?>) entity.get(theReference.getName());
                for (Map.Entry<EnumValue, ?> entry : map.entrySet())
                    if (entry.getKey().equals(toReplace)) {
                        ((Map<EnumValue, Object>) map).put(replacement, map.remove(entry.getKey()));
                        break; // Assume only one key
                    }
            } else {
                Map<?, EnumValue> map = (Map<?, EnumValue>) entity.get(theReference.getName());
                for (Map.Entry<?, EnumValue> entry : map.entrySet())
                    if (entry.getValue().equals(toReplace))
                        entry.setValue(replacement);
            }
        }
    }

    @Override
    public String toString() {
        return theReference.toString();
    }

    @Override
    public int hashCode() {
        int hash = theReference.hashCode() * 3;
        if(isMapKey)
            hash++;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EntityReference && theReference.equals(((EntityReference) obj).theReference)
                && isMapKey == ((EntityReference) obj).isMapKey;
    }
}
