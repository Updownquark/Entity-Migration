package org.migration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.migration.util.ReflectionUtils;
import org.qommons.tree.Tree;
import org.qommons.tree.TreeBuilder;

/**
 * Stores values by entity class hierarchy in an easily-accessible structure
 * 
 * @param <T>
 *            The type of values stored in the map
 */
public class EntityMap<T> {
    private static final Comparator<Class<?>> CLASS_COMPARE = new Comparator<Class<?>>() {
        @Override
        public int compare(Class<?> class1, Class<?> class2) {
            return class1.getName().compareToIgnoreCase(class2.getName());
        }
    };

    private final TreeBuilder<ClassNode<T>, Class<?>> theTree;

    /** Creates the tree */
    public EntityMap() {
        theTree = new TreeBuilder<>(CLASS_COMPARE, Class::getSuperclass);
    }

    /** @return The number of types of entities stored in this tree */
    public int getTypeCount() {
        return theTree.size();
    }

    /** @return The number of values stored in this tree */
    public int size() {
        int ret = 0;
        for (ClassNode<T> node : theTree.nodes()) {
			ret += node.theValues.size();
		}
        return ret;
    }

    public boolean isEmpty() {
        for (ClassNode<T> node : theTree.nodes()) {
			if (!node.theValues.isEmpty()) {
				return false;
			}
		}
        return true;
    }

    /** @return All classes for which there are entities stored in this tree */
    public Iterable<Class<?>> getAllClasses() {
        return theTree.values();
    }

    /**
     * @param type
     *            The type of entities to get values for
     * @param withSubTypes
     *            Whether to include entities whose type is a sub-type of the given type. This will be ignored if type is an interface.
     * @return The stored values for all entities in this tree that are instances of the given type
     */
    public Collection<T> get(Class<?> type, boolean withSubTypes) {
        ArrayList<T> ret = new ArrayList<>();
        if (type.isInterface()) {
			theTree.nodes().forEach(node -> {
                if (type.isAssignableFrom(node.theType)) {
					ret.addAll(node.theValues.values());
				}
            });
		} else {
            ClassNode<T> node = theTree.getNode(type, null);
            if (node != null) {
				if (withSubTypes) {
					node.addTo(ret);
				} else {
					ret.addAll(node.theValues.values());
				}
			}
        }
        return Collections.unmodifiableList(ret);
    }

    /**
     * @param type
     *            Type the type of entity to get the value for
     * @param id
     *            The ID of the entity to get the value for
     * @return The value stored in this structure for an entity of the given type and ID
     */
    public Object get(Class<?> type, Object id) {
        Object[] ret = new Object[1];
        if (type.isInterface()) {
			theTree.nodes().forEach(node -> {
                if (ret[0] != null) {
					return;
				}
                if (type.isAssignableFrom(node.theType)) {
					ret[0] = node.theValues.get(id);
				}
            });
		} else {
            ClassNode<T> node = theTree.getNode(type, null);
            if (node != null) {
				ret[0] = node.getForId(id);
			}
        }
        return ret[0];
    }

    /**
     * @param type
     *            The type that the value is stored by
     * @param value
     *            The stored value
     * @return The ID that the
     */
    public Object getId(Class<?> type, T value) {
        for (ClassNode<T> node : theTree.nodes()) {
            if (!type.isAssignableFrom(node.theType)) {
				continue;
			}
            for (Map.Entry<Object, T> entry : node.theValues.entrySet()) {
				if (entry.getValue().equals(value)) {
					return entry.getKey();
				}
			}
        }
        return null;
    }

    /** @param type The type to add to this entity tree */
    public void addClass(Class<?> type) {
        theTree.getNode(type, ClassNode::new);
    }

    /**
     * @param entity
     *            The entity to add
     * @return null if the entity is new to this set. If non-null, then this set was unaffected and the return value is the value that is
     *         stored in this set with the same type and ID.
     */
    public T put(Object entity, T value) {
        ClassNode<T> node = theTree.getNode(entity.getClass(), ClassNode::new);
        return node.put(entity, value);
    }

    /**
     * Allows storage of a value by an entity that isn't actually constructed, using the type and ID of the entity
     * 
     * @param clazz
     *            The type to store the value by
     * @param id
     *            The ID to store the value by
     * @param value
     *            The value to store
     * @return null if the entity is new to this set. If non-null, then this set was unaffected and the return value is the value that is
     *         stored in this set with the same type and ID.
     */
    public T put(Class<?> clazz, Object id, T value) {
        ClassNode<T> node = theTree.getNode(clazz, ClassNode::new);
        return node.putForId(id, value);
    }

    /**
     * @param entity
     *            The entity to remove from this tree
     */
    public T remove(Object entity) {
        ClassNode<T> node = theTree.getNode(entity.getClass(), null);
        if (node == null) {
			return null;
		}
        return node.remove(entity);
    }

    /**
     * @param entities
     *            The entities to remove from this tree
     */
    public void removeAll(Collection<?> entities) {
        ClassNode<T> lastUsed = null;
        for (Object entity : entities) {
            if (lastUsed == null || lastUsed.theType != entity.getClass()) {
				lastUsed = theTree.getNode(entity.getClass(), null);
			}
            if (lastUsed == null) {
				continue;
			}
            lastUsed.remove(entity);
            if (lastUsed.isRemoved) {
				lastUsed = null;
			}
        }
        Iterator<ClassNode<T>> iter = theTree.nodes().iterator();
        while (iter.hasNext()) {
            ClassNode<T> node = iter.next();
            if (node.theValues.isEmpty() && node.theChildren.isEmpty()) {
				iter.remove();
			}
        }
    }

    /**
     * @param type
     *            The type to remove all entities from this tree. Sub-typed entities will be removed as well.
     */
    public void removeType(Class<?> type) {
        ClassNode<T> node = theTree.getNode(type, null);
        if (node != null) {
			node.remove();
		}
    }

    public boolean containsKey(Object key) {
        ClassNode<T> node = theTree.getNode(key.getClass(), null);
        if (node == null) {
			return false;
		}
        return node.containsKey(key);
    }

    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    public T get(Object key) {
        ClassNode<T> node = theTree.getNode(key.getClass(), null);
        if (node == null) {
			return null;
		}
        return node.get(key);
    }

    public void putAll(Map<? extends Object, ? extends T> m) {
        ClassNode<T> lastUsed = null;
        for (Map.Entry<? extends Object, ? extends T> entry : m.entrySet()) {
            if (lastUsed == null || lastUsed.theType != entry.getKey().getClass()) {
				lastUsed = theTree.getNode(entry.getKey().getClass(), ClassNode::new);
			}
            lastUsed.put(entry.getKey(), entry.getValue());
        }
    }

    public void clear() {
        theTree.clear();
    }

    public Collection<T> values() {
        return get(Object.class, true);
    }

    private static class ClassNode<T> implements Tree<Class<?>, ClassNode<T>, NavigableSet<ClassNode<T>>> {
        static final Comparator<ClassNode<?>> NODE_COMPARE = new Comparator<ClassNode<?>>() {
            @Override
            public int compare(ClassNode<?> node1, ClassNode<?> node2) {
                return CLASS_COMPARE.compare(node1.theType, node2.theType);
            }
        };

        final ClassNode<T> theParent;
        final Class<?> theType;
        private Method theIdGetter;
        final NavigableMap<Object, T> theValues;
        final NavigableSet<ClassNode<T>> theChildren;
        boolean isRemoved;

        ClassNode(Class<?> type, ClassNode<T> parent) {
            theParent = parent;
            theType = type;
            theIdGetter = ReflectionUtils.getIdGetter(type);
            if (theIdGetter != null && !theIdGetter.isAccessible()) {
				theIdGetter.setAccessible(true);
			}
            theValues = new TreeMap<>();
            theChildren = new TreeSet<>(NODE_COMPARE);
        }

        @Override
        public Class<?> getValue() {
            return theType;
        }

        @Override
        public NavigableSet<ClassNode<T>> getChildren() {
            return theChildren;
        }

        Object getId(Object entity) {
            if (theIdGetter == null) {
				throw new IllegalStateException("Class " + theType
                        + " has no ID getter--items of this type cannot be added to an entity set");
			}
            if (!Comparable.class.isAssignableFrom(theIdGetter.getReturnType()) && !theIdGetter.getReturnType().isPrimitive()) {
				throw new IllegalStateException("Class " + theType + "'s ID is not comparable: " + theIdGetter.getReturnType().getName());
			}
            Object id;
            try {
                id = theIdGetter.invoke(entity);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException("Could not get ID for entity " + theType, e);
            }
            if (id == null) {
				throw new IllegalArgumentException("Cannot add an entity with a null ID here: " + theType.getName());
			}
            return id;
        }

        T get(Object entity) {
            Object id = getId(entity);
            return theValues.get(id);
        }

        T getForId(Object id) {
            return getForId(id, false, true);
        }

        boolean containsKey(Object entity) {
            Object id = getId(entity);
            return theValues.containsKey(id);
        }

        T put(Object entity, T value) {
            return putForId(getId(entity), value);
        }

        T putForId(Object id, T value) {
            T ret = getForId(id, true, true);
            if (ret != null) {
				return ret;
			}
            return theValues.put(id, value);
        }

        private T getForId(Object id, boolean ascend, boolean descend) {
            T ret = theValues.get(id);
            if (ret != null) {
				return ret;
			}
            if (ascend) {
                if (theParent != null) {
					ret = theParent.getForId(id, true, false);
				}
                if (ret != null) {
					return ret;
				}
            }
            if (descend) {
				for (ClassNode<T> child : theChildren) {
                    ret = child.getForId(id, false, true);
                    if (ret == null) {
						return ret;
					}
                }
			}
            return null;
        }

        T remove(Object entity) {
            if (theValues.isEmpty()) {
				return null;
			}
            if (theIdGetter == null) {
				throw new IllegalStateException("Class " + theType
                        + " has no ID getter--items of this type cannot be added to an entity set");
			}
            if (!Comparable.class.isAssignableFrom(theIdGetter.getReturnType())) {
				throw new IllegalStateException("Class " + theType + "'s ID is not comparable: " + theIdGetter.getReturnType().getName());
			}
            Object id;
            try {
                id = theIdGetter.invoke(entity);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException("Could not get ID for entity " + theType, e);
            }
            return theValues.remove(id);
        }

        void remove() {
            theParent.theChildren.remove(this);
        }

        void addTo(Collection<T> addTo) {
            addTo.addAll(theValues.values());
            for (ClassNode<T> child : theChildren) {
				child.addTo(addTo);
			}
        }

        @Override
        public String toString() {
            return theType.getName();
        }
    }
}
