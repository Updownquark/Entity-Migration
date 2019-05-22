package org.migration.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Id;
import javax.persistence.Transient;

import org.migration.MigrationField;

/** Contains methods to reflect hibernate properties out of entity classes */
public class ReflectionUtils {
    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES;
    private static final Map<Class<?>, Class<?>> WRAPPED_TYPES;

    static {
        Map<Class<?>, Class<?>> wrapperTypes = new HashMap<>();
        Map<Class<?>, Class<?>> wrappedTypes = new HashMap<>();
        wrapperTypes.put(Boolean.TYPE, Boolean.class);
        wrappedTypes.put(Boolean.class, Boolean.TYPE);
        wrapperTypes.put(Character.TYPE, Character.class);
        wrappedTypes.put(Character.class, Character.TYPE);
        wrapperTypes.put(Byte.TYPE, Byte.class);
        wrappedTypes.put(Byte.class, Byte.TYPE);
        wrapperTypes.put(Short.TYPE, Short.class);
        wrappedTypes.put(Short.class, Short.TYPE);
        wrapperTypes.put(Integer.TYPE, Integer.class);
        wrappedTypes.put(Integer.class, Integer.TYPE);
        wrapperTypes.put(Long.TYPE, Long.class);
        wrappedTypes.put(Long.class, Long.TYPE);
        wrapperTypes.put(Float.TYPE, Float.class);
        wrappedTypes.put(Float.class, Float.TYPE);
        wrapperTypes.put(Double.TYPE, Double.class);
        wrappedTypes.put(Double.class, Double.TYPE);
        WRAPPER_TYPES = Collections.unmodifiableMap(wrapperTypes);
        WRAPPED_TYPES = Collections.unmodifiableMap(wrappedTypes);
    }

    /**
     * @param type
     *            The type to get all methods for
     * @return All methods declared by the given type and its super types
     */
    public static Method[] getAllMethods(Class<?> type) {
        List<Method> ret = new ArrayList<>();
        addAllMethods(ret, type);
        return ret.toArray(new Method[ret.size()]);
    }

    private static void addAllMethods(List<Method> methods, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            boolean contained = false;
            for (Method m : methods) {
                if (m.getName().equals(method.getName()) && m.getParameterTypes().length == method.getParameterTypes().length) {
                    contained = true;
                    for (int p = 0; contained && p < m.getParameterTypes().length; p++) {
                        if (!m.getParameterTypes()[p].equals(method.getParameterTypes()[p])) {
                            contained = false;
                        }
                    }
                }
                if (contained) {
                    // Use the most specific return type
                    if (m.getReturnType().isAssignableFrom(method.getReturnType()) && !m.getReturnType().equals(method.getReturnType())) {
                        methods.remove(m);
                        contained = false;
                    }
                    break;
                }
            }
            if (!contained) {
                methods.add(method);
            }
        }
        if (type.getSuperclass() != null && !Object.class.equals(type.getSuperclass())) {
            addAllMethods(methods, type.getSuperclass());
        }
    }

    /**
     * @param type
     *            The type to get all getter methods for
     * @return All getter methods declared by the given type
     */
    public static Method[] getAllGetters(Class<?> type) {
        return getAllGetters(type, true);
    }

    /**
     * @param type
     *            The type to get all getter methods for
     * @param ignoreTransient
     *            Whether to ignore methods tagged with @{@link Transient}
     * @return All getter methods declared by the given type
     */
    public static Method[] getAllGetters(Class<?> type, boolean ignoreTransient) {
        ArrayList<Method> ret = new ArrayList<>();
        for (Method method : getAllMethods(type)) {
            if (isGetter(type, method, ignoreTransient)) {
                ret.add(method);
            }
        }
        return ret.toArray(new Method[ret.size()]);
    }

    /**
     * @param type
     *            The type of entity that the method is from
     * @param method
     *            The method to check
     * @return Whether the given method is a getter for a hibernate property
     */
    public static boolean isGetter(Class<?> type, Method method) {
        return isGetter(type, method, true);
    }

    /**
     * @param type
     *            The type of entity that the method is from
     * @param method
     *            The method to check
     * @param ignoreTransient
     *            Whether to ignore methods tagged with @{@link Transient}
     * @return Whether the given method is a getter for a hibernate property
     */
    public static boolean isGetter(Class<?> type, Method method, boolean ignoreTransient) {
        if (method.getParameterTypes().length > 0 || Void.TYPE.equals(method.getReturnType()) || method.isSynthetic()
                || (method.getModifiers() & Modifier.STATIC) != 0) {
            return false;
        }
        if (ignoreTransient) {
            MigrationField migField = method.getAnnotation(MigrationField.class);
            if (migField != null) {
                if (!migField.persisted()) {
					return false;
				}
            } else if (method.getAnnotation(Transient.class) != null) {
				return false;
			}
        }
        if (method.getName().equals("toString") || method.getName().equals("clone") || method.getName().equals("hashCode")) {
            return false;
        }
        if (getSetter(type, method, false) == null) {
            return false;
        }
        return true;
    }

    /**
     * @param type
     *            The type to get the field getter for
     * @param fieldName
     *            The name of the field to get the getter for
     * @return The getter for the given field on the given entity, or null if such a getter could not be found
     */
    public static Method getGetter(Class<?> type, String fieldName) {
        if (fieldName.startsWith("_.")) {
			fieldName = fieldName.substring(2);
		}
        fieldName = fieldName.toLowerCase();
        ArrayList<String> possibleNames = new ArrayList<>();
        possibleNames.add(fieldName);
        possibleNames.add("get" + fieldName);
        possibleNames.add("is" + fieldName);
        for (Method method : getAllMethods(type)) {
            if (possibleNames.contains(method.getName().toLowerCase()) && isGetter(type, method, false)) {
                return method;
            }
        }
        return null;
    }

    /**
     * @param type
     *            The type to get the property setter for
     * @param getter
     *            The getter of the property
     * @param withError
     *            Whether to print an error describing why the setter was not found
     * @return The setter of the property for which <code>getter</code> is the getter, or null if such a setter could not be found.
     */
    public static Method getSetter(Class<?> type, Method getter, boolean withError) {
        String fieldName = getter.getName();
        String setterName = fieldName.toLowerCase();
        Class<?> fieldType = getter.getReturnType();
        if (setterName.startsWith("get")) {
            fieldName = fieldName.substring(3);
        } else if (setterName.startsWith("is")) {
            fieldName = fieldName.substring(2);
        }
        setterName = "set" + fieldName.toLowerCase();
        Method bad = null;
        for (Method method : getAllMethods(type)) {
            if (!method.getName().toLowerCase().equals(setterName) || method.getParameterTypes().length != 1 || method.isSynthetic()
                    || (method.getModifiers() & Modifier.STATIC) != 0) {
                continue;
            }
            if (wrap(method.getParameterTypes()[0]).isAssignableFrom(wrap(fieldType))) {
                return method;
            } else {
                bad = method;
            }
        }
        if (bad != null && withError) {
            System.err.println("Setter " + bad.getName() + " for property " + fieldName + " does not accept the field type "
                    + fieldType.getName());
        }
        return null;
    }

    /**
     * @param primType
     *            The primitive type to wrap
     * @return The wrapper type for the given primitive type, or the given class if it is not primitive
     */
    public static Class<?> wrap(Class<?> primType) {
        return WRAPPER_TYPES.getOrDefault(primType, primType);
    }

    /**
     * @param wrapperType
     *            The wrapper type to unwrap
     * @return The primitive type that the given type wraps, or the given class if it is not a wrapper type
     */
    public static Class<?> unwrap(Class<?> wrapperType) {
        return WRAPPED_TYPES.getOrDefault(wrapperType, wrapperType);
    }

    /**
     * @param type
     *            The type to get the default (zero-argument) constructor for
     * @return The default constructor for the given type
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> type) {
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterTypes().length == 0) {
                return (Constructor<T>) c;
            }
        }
        return null;
    }

    /**
     * @param entityType
     *            The entity type to get the identifier for
     * @return The getter method for the entity's identifier
     */
    public static Method getIdGetter(Class<?> entityType) {
        for (Method m : getAllMethods(entityType)) {
            MigrationField migField = m.getAnnotation(MigrationField.class);
            if (migField != null && migField.id()) {
				return m;
			}
            if (m.getAnnotation(Id.class) != null) {
                return m;
            }
        }
        return null;
    }

    /**
     * @param getterName
     *            The name of the getter of the property to get the field name for
     * @return The field name to represent the given property
     */
    public static String getFieldNameFromGetter(String getterName) {
        if (getterName.length() > 3 && getterName.startsWith("get")) {
            getterName = getterName.substring(3);
        } else if (getterName.length() > 2 && getterName.startsWith("is")) {
            getterName = getterName.substring(2);
        }
        if (Character.isUpperCase(getterName.charAt(0))) {
            getterName = Character.toLowerCase(getterName.charAt(0)) + getterName.substring(1);
        }
        if ((Character.isAlphabetic(getterName.charAt(0)) || getterName.charAt(0) == '_') && !getterName.toLowerCase().startsWith("xml")) {
        } else {
            getterName = "_." + getterName;
        }
        return getterName;
    }
}
