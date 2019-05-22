package org.migration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.persistence.Id;

import org.hibernate.annotations.SortComparator;
import org.hibernate.annotations.SortNatural;
import org.migration.util.PersistenceUtils;
import org.migration.util.ReflectionUtils;

/**
 * A dissecter that uses reflection to pull apart and put together objects of a particular type
 * 
 * @param <T>
 *            The type that the dissecter can understand
 */
public class ReflectiveDissecter<T> implements ValueDissecter {
	/** A comparator for Comparables */
	public static class NaturalComparator implements Comparator<Comparable<?>> {
		@Override
		public int compare(Comparable<?> o1, Comparable<?> o2) {
			return ((Comparable<Object>) o1).compareTo(o2);
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof NaturalComparator;
		}

		@Override
		public String toString() {
			return "Natural";
		}
	}

	/** A comparator that just leaves things in the order it found them */
	public static class EncounterOrder implements Comparator<Object> {
		@Override
		public int compare(Object o1, Object o2) {
			return o1 == o2 ? 0 : -1;
		}

		@Override
		public int hashCode() {
			return 1;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof EncounterOrder;
		}

		@Override
		public String toString() {
			return "Encounter order";
		}
	}

    private final Class<T> theType;

    private final Constructor<T> theConstructor;
    private final Map<String, Method> theGetters;
    private final Map<String, Method> theSetters;

    /**
     * @param type
     *            The type to create the dissecter for
     */
    public ReflectiveDissecter(Class<T> type) {
        theType = type;

        Constructor<?> noArg = null;
        try {
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == 0) {
                    noArg = c;
                    break;
                }
            }
            if (noArg == null) {
                throw new IllegalStateException("No no-argument constructor for entity type " + type.getName());
            }
            if (!noArg.isAccessible()) {
                noArg.setAccessible(true);
            }
        } catch (SecurityException e) {
            throw new IllegalStateException("Could not access constructors for entity type " + type.getName());
        }
        theConstructor = (Constructor<T>) noArg;

        theGetters = new TreeMap<>();
        theSetters = new HashMap<>();
        for (Method getter : ReflectionUtils.getAllGetters(type, true)) {
            String fieldName = PersistenceUtils.javaToXml(ReflectionUtils.getFieldNameFromGetter(getter.getName()));
            theGetters.put(fieldName, getter);
            Method setter = ReflectionUtils.getSetter(type, getter, true);
            theSetters.put(fieldName, setter);
            if (PersistenceUtils.isNullable(getter) && !getter.getReturnType().isPrimitive() && setter.getParameterTypes()[0].isPrimitive()) {
				System.err.println("WARNING: Setter for nullable field " + type.getName() + "." + fieldName + " accepts a primitive value");
			}
        }
    }

    /** @return The type that this dissecter understands */
    public Class<T> getType() {
        return theType;
    }

    @Override
    public TypedField[] getFields() {
        ArrayList<TypedField> fields = new ArrayList<>(theGetters.size());
        for (Map.Entry<String, Method> entry : theGetters.entrySet()) {
            Method getter = entry.getValue();
            MigrationField migField = getter.getAnnotation(MigrationField.class);
            boolean id = (migField != null && migField.id()) || getter.getAnnotation(Id.class) != null;
            boolean nullable = PersistenceUtils.isNullable(getter);
            String mapping = PersistenceUtils.getMap(getter);
            String[] sorting = PersistenceUtils.getSorting(getter);
			Comparator<?> sortCompare = getSortComparator(getter);

            fields.add(TypedField.builder(theType, entry.getKey(), getter.getGenericReturnType()).id(id).nullable(nullable).mapping(mapping)
					.ordering(sorting).sort(sortCompare).build());
        }
        return fields.toArray(new TypedField[fields.size()]);
    }

	@SuppressWarnings("deprecation")
	private static <T> Comparator<T> getSortComparator(Method getter) {
		SortComparator scAnn = getter.getAnnotation(SortComparator.class);
		if (scAnn != null) {
			try {
				return (Comparator<T>) scAnn.value().newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalStateException("Could not instantiate comparator class " + scAnn.value().getName(), e);
			}
		} else {
			SortNatural snAnn = getter.getAnnotation(SortNatural.class);
			if (snAnn != null) {
				return (Comparator<T>) new NaturalComparator();
			} else {
				org.hibernate.annotations.Sort sortAnn = getter.getAnnotation(org.hibernate.annotations.Sort.class);
				if (sortAnn != null) {
	                switch (sortAnn.type()) {
	                case UNSORTED:
						return (Comparator<T>) new EncounterOrder();
	                case NATURAL:
						return (Comparator<T>) new NaturalComparator();
	                case COMPARATOR:
						try {
							return (Comparator<T>) sortAnn.comparator().newInstance();
						} catch (InstantiationException | IllegalAccessException e) {
							throw new IllegalStateException("Could not instantiate comparator class " + sortAnn.comparator().getName(), e);
						}
					default:
						return null;
					}
				} else {
					return null;
				}
			}
		}
	}

	@Override
    public Object getFieldValue(Object entity, String field) {
        if (!theType.isInstance(entity)) {
            throw new IllegalArgumentException("Unrecognized type: " + entity.getClass().getName());
        }
        Method getter = theGetters.get(field);
        try {
            return getter.invoke(entity);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalStateException("Could not invoke field getter for " + entity.getClass().getSimpleName() + "." + field, e);
        }
    }

    @Override
    public Object createWith(Map<String, Object> fieldValues) {
        Object newInstance;
        try {
            newInstance = theConstructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalStateException("Could not instantiate " + theType.getName(), e);
        }

        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            Method setter = theSetters.get(entry.getKey());
            if (setter == null) {
                throw new IllegalArgumentException("Unrecognized " + theType.getName() + " field " + entry.getKey());
            }
            try {
                if (!setter.isAccessible()) {
                    setter.setAccessible(true);
                }
                setter.invoke(newInstance, entry.getValue());
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException(
                        "Could not invoke setter for field " + theType.getName() + "." + entry.getKey() + " with value " + entry.getValue(),
                        e);
            }
        }
        return newInstance;
    }

    @Override
    public void setFieldValue(Object entity, String field, Object fieldValue) {
        if (!theType.isInstance(entity)) {
            throw new IllegalArgumentException("Unrecognized type: " + entity.getClass().getName());
        }
        Method setter = theSetters.get(field);
        if (setter == null) {
            throw new IllegalArgumentException("Unrecognized " + theType.getName() + " field " + field);
        }
        try {
            if (!setter.isAccessible()) {
                setter.setAccessible(true);
            }
            setter.invoke(entity, fieldValue);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalStateException("Could not invoke setter for field " + theType.getName() + "." + field, e);
        }
    }
}
