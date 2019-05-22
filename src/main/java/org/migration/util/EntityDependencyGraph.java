package org.migration.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import org.qommons.ArrayUtils;

/** Maps dependencies between a set of entity types */
public class EntityDependencyGraph {
    /** Represents a persistable field of an entity */
    public class PersistedField {
        private Class<?> theEntityType;

        private Method theGetter;

        private Method theSetter;

        private boolean isCollection;

        private boolean isMap;

        PersistedField(Class<?> entityType, Method getter, Method setter) {
            theEntityType = entityType;
            theGetter = getter;
            theSetter = setter;
            isCollection = Collection.class.isAssignableFrom(getter.getReturnType());
            isMap = Map.class.isAssignableFrom(getter.getReturnType());
        }

        /** @return The entity type that this field is an attribute of */
        public Class<?> getEntityType() {
            return theEntityType;
        }

        /** @return A unique name to represent the field */
        public String getName() {
            return theGetter.getName();
        }

        /** @return The getter for this field */
        public Method getGetter() {
            return theGetter;
        }

        /** @return The setter for this field */
        public Method getSetter() {
            return theSetter;
        }

        /** @return The entity types that this field references */
        public Class<?>[] getTargetTypes() {
            Class<?> fieldType = theGetter.getReturnType();
            if (Collection.class.isAssignableFrom(fieldType) || Map.class.isAssignableFrom(fieldType)) {
                ArrayList<Class<?>> ret = new ArrayList<>();
                Type[] typeArgs = ((ParameterizedType) theGetter.getGenericReturnType()).getActualTypeArguments();
                for (Type arg : typeArgs) {
                    if (getClasses().contains(arg)) {
                        ret.add((Class<?>) arg);
                    }
                }
                return ret.toArray(new Class[ret.size()]);
            } else {
                return new Class[] { fieldType };
            }
        }

        /**
         * @param entity
         *            The entity to get the field value of
         * @return The value of this field on the given entity
         */
        public Object get(Object entity) {
            try {
                if (!theGetter.isAccessible()) {
                    theGetter.setAccessible(true);
                }
                Object ret = theGetter.invoke(entity);
                if (isCollection) {
                    if (ret == null) {
                        throw new IllegalStateException("Collection was not initialized");
                    }
                    return new ArrayList<Object>((Collection<?>) ret);
                } else if (isMap) {
                    if (ret == null) {
                        throw new IllegalStateException("Map was not initialized");
                    }
                    return new LinkedHashMap<Object, Object>((Map<?, ?>) ret);
                } else {
                    return ret;
                }
            } catch (Throwable e) {
                throw new IllegalStateException("Could not retrieve field with getter " + theEntityType.getName() + "."
                        + theGetter.getName() + "()", e);
            }
        }

        /**
         * @param entity
         *            The entity to set the field value of
         * @param value
         *            The value of the field to set on the given entity
         */
        public void set(Object entity, Object value) {
            try {
                if (!theSetter.isAccessible()) {
                    theSetter.setAccessible(true);
                }
                if (isCollection) {
                    Collection<Object> fieldValue = (Collection<Object>) theGetter.invoke(entity);
                    fieldValue.clear();
                    if (value != null) {
                        fieldValue.addAll((Collection<?>) value);
                    }
                } else if (isMap) {
                    Map<Object, Object> fieldValue = (Map<Object, Object>) theGetter.invoke(entity);
                    fieldValue.clear();
                    if (value != null) {
                        fieldValue.putAll((Map<?, ?>) value);
                    }
                } else {
                    theSetter.invoke(entity, value);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException("Could not set field with setter " + theEntityType.getName() + "." + theSetter.getName()
                        + "()", e);
            }
        }

        @Override
        public String toString() {
            return theEntityType.getSimpleName() + "." + ReflectionUtils.getFieldNameFromGetter(theGetter.getName());
        }
    }

    /** Represents a reference from one type to another */
    public static enum RefType {
        /** The reference is required--the reference must be persisted before the referrer is */
        required,
        /** The reference is optional--the reference may be persisted after the referrer is */
        optional;
    }

    /** Represents a strategy for persisting entities of a given type and their dependencies */
    public class EntityReferenceSet {
        private Class<?> theEntityType;

        private List<PersistedField> thePreRequisites;

        private List<PersistedField> theOptional;

        private List<PersistedField> theRequiredDependents;

        private List<PersistedField> theOptionalDependents;

        EntityReferenceSet(Class<?> entityType) {
            if (entityType == null) {
                throw new NullPointerException();
            }
            theEntityType = entityType;
            thePreRequisites = new ArrayList<>();
            theOptional = new ArrayList<>();
            theRequiredDependents = new ArrayList<>();
            theOptionalDependents = new ArrayList<>();
        }

        /** @return The type of entity that this strategy is for */
        public Class<?> getEntityType() {
            return theEntityType;
        }

        /** @return The set of dependencies that this entity relies on */
        public List<PersistedField> getPreRequisites() {
            return thePreRequisites;
        }

        /** @return The set of dependencies that this entity references but does not require */
        public List<PersistedField> getOptional() {
            return theOptional;
        }

        /** @return The set of dependencies that this entity references in any way */
        public List<PersistedField> getDependencies() {
            ArrayList<PersistedField> ret = new ArrayList<>(thePreRequisites);
            ret.addAll(theOptional);
            return ret;
        }

        /** @return The set of fields from other entities that require this entity */
        public List<PersistedField> getRequiredDependents() {
            return theRequiredDependents;
        }

        /** @return The set of fields from other entities that reference this entity but do not require it */
        public List<PersistedField> getOptionalDependents() {
            return theOptionalDependents;
        }

        /** @return The set of fields from other entities that depend on this entity in any way */
        public List<PersistedField> getDependents() {
            ArrayList<PersistedField> ret = new ArrayList<>(theRequiredDependents);
            ret.addAll(theOptionalDependents);
            return ret;
        }

        /**
         * @param type
         *            The type to determine the reference path to
         * @return The reference type of the reference path from this type to the given type:
         *         <ul>
         *         <li>{@link RefType#required} if entities of the given are required by entities of this type</li>
         *         <li>{@link RefType#optional} if entities of the given are referenced but not required by entities of this type</li>
         *         <li>null if entities of the given are not referenced by entities of this type</li>
         *         </ul>
         */
        public RefType getReferenceType(Class<?> type) {
            return getReferenceType(type, new HashSet<EntityReferenceSet>());
        }

        private RefType getReferenceType(Class<?> type, Set<EntityReferenceSet> visited) {
            if (visited.contains(this)) {
                return null;
            }
            for (PersistedField pre : thePreRequisites) {
                if (ArrayUtils.contains(pre.getTargetTypes(), type)) {
                    return RefType.required;
                }
            }
            for (PersistedField opt : theOptional) {
                if (ArrayUtils.contains(opt.getTargetTypes(), type)) {
                    return RefType.optional;
                }
            }
            visited.add(this);
            try {
                for (PersistedField pre : thePreRequisites) {
                    for (Class<?> refType : pre.getTargetTypes()) {
                        RefType ref = getReferenceSet(refType).getReferenceType(type, visited);
                        if (ref != null) {
                            return ref;
                        }
                    }
                }
                for (PersistedField opt : theOptional) {
                    for (Class<?> refType : opt.getTargetTypes()) {
                        RefType ref = getReferenceSet(refType).getReferenceType(type, visited);
                        if (ref != null) {
                            return RefType.optional;
                        }
                    }
                }
            } finally {
                visited.remove(this);
            }
            return null;
        }

        void addPreRequisite(PersistedField field) {
            if (field == null) {
                throw new NullPointerException();
            }
            thePreRequisites.add(field);
        }

        void addOptional(PersistedField field) {
            if (field == null) {
                throw new NullPointerException();
            }
            theOptional.add(field);
        }

        void addRequiredDependent(PersistedField field) {
            if (field == null) {
                throw new NullPointerException();
            }
            theRequiredDependents.add(field);
        }

        void addOptionalDependent(PersistedField field) {
            if (field == null) {
                throw new NullPointerException();
            }
            theOptionalDependents.add(field);
        }

        boolean references(Class<?> type, Set<Class<?>> preRequired, Map<Class<?>, EntityReferenceSet> making) {
            return references(type, new HashSet<EntityReferenceSet>(), preRequired, making) != null;
        }

        boolean isReferenceOptional(Class<?> type, Set<Class<?>> preRequired, Map<Class<?>, EntityReferenceSet> making) {
            return references(type, new HashSet<EntityReferenceSet>(), preRequired, making) == RefType.optional;
        }

        private RefType references(Class<?> type, Set<EntityReferenceSet> visited, Set<Class<?>> preRequired,
                Map<Class<?>, EntityReferenceSet> making) {
            if (visited.contains(this)) {
                return null;
            }
            for (PersistedField pre : thePreRequisites) {
                if (ArrayUtils.contains(pre.getTargetTypes(), type)) {
                    return RefType.required;
                }
            }
            for (PersistedField opt : theOptional) {
                if (ArrayUtils.contains(opt.getTargetTypes(), type)) {
                    return RefType.optional;
                }
            }
            visited.add(this);
            try {
                for (PersistedField pre : thePreRequisites) {
                    for (Class<?> refType : pre.getTargetTypes()) {
                        RefType ref = makeReferenceSet(refType, preRequired, making).references(type, visited, preRequired, making);
                        if (ref != null) {
                            return ref;
                        }
                    }
                }
                for (PersistedField opt : theOptional) {
                    for (Class<?> refType : opt.getTargetTypes()) {
                        RefType ref = makeReferenceSet(refType, preRequired, making).references(type, visited, preRequired, making);
                        if (ref != null) {
                            return RefType.optional;
                        }
                    }
                }
            } finally {
                visited.remove(this);
            }
            return null;
        }

        @Override
        public String toString() {
            return "Persistence strategy for " + theEntityType.getName();
        }
    }

    private Map<Class<?>, EntityReferenceSet> theRefSets;

    /**
     * @param classes
     *            The entity classes to compile the dependency graph for
     */
    public EntityDependencyGraph(Iterable<Class<?>> classes) {
        theRefSets = new LinkedHashMap<>();
        for (Class<?> clazz : classes) {
            theRefSets.put(clazz, null);
        }
        Map<Class<?>, EntityReferenceSet> making = new LinkedHashMap<>();
        Set<Class<?>> preReq = new HashSet<>();
        for (Class<?> clazz : classes) {
            if (theRefSets.get(clazz) == null) {
                makeReferenceSet(clazz, preReq, making);
            }
        }

        // Compile reverse references
        for (Map.Entry<Class<?>, EntityReferenceSet> ref : theRefSets.entrySet()) {
            for (PersistedField field : ref.getValue().getPreRequisites()) {
                for (Class<?> refType : field.getTargetTypes()) {
                    theRefSets.get(refType).addRequiredDependent(field);
                }
            }
            for (PersistedField field : ref.getValue().getOptional()) {
                for (Class<?> refType : field.getTargetTypes()) {
                    theRefSets.get(refType).addOptionalDependent(field);
                }
            }
        }
        theRefSets = Collections.unmodifiableMap(theRefSets);
    }

    /** @return The classes whose dependencies are mapped in this graph */
    public Collection<Class<?>> getClasses() {
        return theRefSets.keySet();
    }

    /**
     * @param clazz
     *            The class to get the reference set for
     * @return The set of entity references made by the given type
     */
    public EntityReferenceSet getReferenceSet(Class<?> clazz) {
        EntityReferenceSet ret = theRefSets.get(clazz);
        if (ret == null) {
            throw new IllegalArgumentException("This entity dependency graph has no knowledge of " + clazz.getName());
        }
        return ret;
    }

    private EntityReferenceSet makeReferenceSet(Class<?> clazz, Set<Class<?>> preRequired, Map<Class<?>, EntityReferenceSet> making) {
        if (theRefSets.get(clazz) != null) {
            return theRefSets.get(clazz);
        }
        if (making.get(clazz) != null) {
            return making.get(clazz);
        }
        if (preRequired.contains(clazz)) {
            throw new IllegalStateException("Entity type " + clazz.getName() + " has a non-optional recursive dependency: " + preRequired);
        }
        preRequired.add(clazz);
        EntityReferenceSet ret = new EntityReferenceSet(clazz);
        try {
            for (Method getter : getEntityMethods(clazz, RefType.required)) {
                Class<?> type = getter.getReturnType();
                // Make sure the persistence strategy for the given type is either already created, or being created further up the stack
                if (!making.containsKey(type)) {
                    makeReferenceSet(type, preRequired, making);
                }
                ret.addPreRequisite(new PersistedField(clazz, getter, ReflectionUtils.getSetter(clazz, getter, true)));
            }
        } finally {
            preRequired.remove(clazz);
        }
        making.put(clazz, ret);
        try {
            for (Method getter : getEntityMethods(clazz, RefType.optional)) {
                Class<?> type = getter.getReturnType();
                if (Collection.class.isAssignableFrom(type)) {
                    ret.addOptional(new PersistedField(clazz, getter, ReflectionUtils.getSetter(clazz, getter, true)));
                } else if (Map.class.isAssignableFrom(type)) {
                    ret.addOptional(new PersistedField(clazz, getter, ReflectionUtils.getSetter(clazz, getter, true)));
                } else {
                    EntityReferenceSet fieldStrategy = making.get(type);
                    boolean reference;
                    if (fieldStrategy != null) {
                        reference = fieldStrategy.references(clazz, preRequired, making);
                    } else if (type == clazz) {
                        reference = true;
                    } else {
                        fieldStrategy = makeReferenceSet(type, preRequired, making);
                        reference = fieldStrategy.references(clazz, preRequired, making);
                    }
                    PersistedField field = new PersistedField(clazz, getter, ReflectionUtils.getSetter(clazz, getter, true));
                    if (!reference) {
                        ret.addPreRequisite(field);
                    } else {
                        ret.addOptional(field);
                    }
                }
            }
        } finally {
            making.remove(clazz);
        }
        theRefSets.put(clazz, ret);
        return ret;
    }

    private Method[] getEntityMethods(Class<?> entityClass, RefType type) {
        ArrayList<Method> ret = new ArrayList<>();
        for (Method getter : ReflectionUtils.getAllGetters(entityClass)) {
            Class<?> fieldType = getter.getReturnType();
            if (getClasses().contains(fieldType)) {
                if (type != null) {
                    boolean optional = true;
                    OneToOne oto = getter.getAnnotation(OneToOne.class);
                    if (oto != null) {
                        optional = oto.optional();
                    } else {
                        ManyToOne mto = getter.getAnnotation(ManyToOne.class);
                        if (mto != null) {
                            optional = mto.optional();
                        }
                    }
                    if (optional != (type == RefType.optional)) {
                        continue;
                    }
                }
                ret.add(getter);
            } else if (type != RefType.required && Collection.class.isAssignableFrom(fieldType)) {
                Type gt = getter.getGenericReturnType();
                if (gt instanceof ParameterizedType && getClasses().contains(((ParameterizedType) gt).getActualTypeArguments()[0])) {
                    ret.add(getter);
                }
            } else if (type != RefType.required && Map.class.isAssignableFrom(fieldType)) {
                Type gt = getter.getGenericReturnType();
                if (gt instanceof ParameterizedType
                        && (getClasses().contains(((ParameterizedType) gt).getActualTypeArguments()[0]) || getClasses().contains(
                                ((ParameterizedType) gt).getActualTypeArguments()[1]))) {
                    ret.add(getter);
                }
            }
        }
        return ret.toArray(new Method[ret.size()]);
    }
}
