package org.migration;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contains knowledge of how to handle type hierarchies for reflective dissection
 * 
 * @param <T>
 *            The super type of types that this generator can handle
 */
public class ReflectiveDissecterGenerator<T> implements DissecterGenerator {
    private final Class<T> theType;
    private final ReflectiveDissecter<T> theDissecter;
    private final Map<String, ReflectiveDissecter<? extends T>> theSubDissecters;
    private final TypeGetter theTypeGetter;

    /**
     * @param type
     *            The super type of types that this generator will handle
     * @param typeGetter
     *            The type getter to get types by name
     */
    public ReflectiveDissecterGenerator(Class<T> type, TypeGetter typeGetter) {
        theType = type;
        theDissecter = new ReflectiveDissecter<>(type);
        theSubDissecters = new LinkedHashMap<>();
        theTypeGetter = typeGetter;
    }

    @Override
    public String getSubType(Class<?> type) {
        if (type == theType)
            return null;
        else
            return type.getName();
    }

    /**
     * @param name
     *            The name of the sub-type to get
     * @return The sub-class represented by the sub-type name
     */
    protected Class<? extends T> getSubType(String name) {
        Class<?> clazz;
        try {
            if (theTypeGetter != null)
                clazz = theTypeGetter.getType(name);
            else
                clazz = Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not get sub type of " + theType + " named \"" + name + "\"", e);
        }
        return clazz.asSubclass(theType);
    }

    @Override
    public Dissecter dissect(Type type, String subType) {
        if (subType == null)
            return theDissecter;
        ReflectiveDissecter<? extends T> ret = theSubDissecters.get(subType);
        if (ret == null) {
            Class<? extends T> clazz = getSubType(subType);
            ret = new ReflectiveDissecter<>(clazz);
            theSubDissecters.put(subType, ret);
        }
        return ret;
    }
}
