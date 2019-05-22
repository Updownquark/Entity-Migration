package org.migration;

import java.util.Arrays;
import java.util.Collection;

/** Stores entities by class hierarchy in an easily-accessible structure */
public class EntitySet {
    private final EntityMap<Object> theMap;

    /** Creates the tree */
    public EntitySet() {
        theMap = new EntityMap<>();
    }

    /** @return The number of entities stored in this tree */
    public int size() {
        return theMap.size();
    }

    /** @return All classes for which there are entities stored in this tree */
    public Iterable<Class<?>> getAllClasses() {
        return theMap.getAllClasses();
    }

    /**
     * @param <T>
     *            The (compile-time) type of entities to get
     * @param type
     *            The (run-time) type of entities to get
     * @param withSubTypes
     *            Whether to include entities whose type is a sub-type of the given type. This will be ignored if type is an interface.
     * @return All entities in this tree that are instances of the given type
     */
    public <T> Collection<T> get(Class<T> type, boolean withSubTypes) {
        return (Collection<T>) theMap.get(type, withSubTypes);
    }

    /** @param type The type to add to this entity tree */
    public void addClass(Class<?> type) {
        theMap.addClass(type);
    }

    /**
     * @param entity
     *            The entity to add
     * @return null if the entity is new to this set. If non-null, then this set was unaffected and the return value is the entity that is
     *         stored in this set with the same type and ID.
     */
    public <T> T add(T entity) {
        return (T) theMap.put(entity, entity);
    }

    /**
     * Allows storage of an entity in this set whose ID has not been set. This is valueable because calling the ID setter has implications
     * for hibernate.
     * 
     * @param entity
     *            The entity to add to this set
     * @param id
     *            The ID to store the entity under
     * @return null if the entity ID is new to this set. If non-null, then this set was unaffected and the return value is the entity that
     *         is stored in this set with the same type and the given ID.
     */
    public <T> T addForId(T entity, Object id) {
        return (T) theMap.put(entity.getClass(), id, entity);
    }

    /**
     * @param type
     *            The type of the entity to get
     * @param id
     *            The ID of the entity to get
     * @return The entity stored in this structure with the given type and ID
     */
    public <T> T get(Class<T> type, Object id) {
        return (T) theMap.get(type, id);
    }

    /**
     * @param entity
     *            The entity stored in this set
     * @return The ID by which the entity is stored
     */
    public Object getId(Object entity) {
        return theMap.getId(entity.getClass(), entity);
    }

    /**
     * @param entities
     *            The entities to add to this tree
     */
    public void add(Object... entities) {
        add(Arrays.asList(entities));
    }

    /**
     * @param entities
     *            The entities to add to this tree
     */
    public void add(Collection<?> entities) {
        for (Object entity : entities)
            add(entity);
    }

    /**
     * @param entities
     *            The entities to remove from this tree
     */
    public void remove(Object... entities) {
        remove(Arrays.asList(entities));
    }

    /**
     * @param entities
     *            The entities to remove from this tree
     */
    public void remove(Collection<?> entities) {
        theMap.removeAll(entities);
    }

    /**
     * @param type
     *            The type to remove all entities from this tree. Sub-typed entities will be removed as well.
     */
    public void removeType(Class<?> type) {
        theMap.removeType(type);
    }
}
