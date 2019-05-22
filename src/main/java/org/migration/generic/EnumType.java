package org.migration.generic;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jdom2.Element;

/** Represents an enum type referred to by an entity */
public class EnumType implements Type, Comparable<EnumType>, Iterable<EnumValue>, Cloneable {
    /** The name of this enum as it was written to XML */
    private String theName;

    private NavigableSet<EnumValue> theValues;
    private NavigableMap<String, EnumValue> theValuesByName;

    /**
     * @param name
     *            The name of the enum
     */
    protected EnumType(String name) {
        theName = name;
        theValues = new TreeSet<>();
        theValuesByName = new TreeMap<>();
    }

    /**
     * Populates this enum type's values from XML
     * 
     * @param xml
     *            The XML element representing this enum type
     */
    protected void populateValues(Element xml) {
        for (Element child : xml.getChildren())
            addValue(child.getName());
    }

    /**
     * Populates this enum type's values from the enum class it represents
     * 
     * @param enumType
     *            The enum class represented by this type
     */
    protected void populateValues(Class<? extends Enum<?>> enumType) {
        for (Enum<?> v : enumType.getEnumConstants())
            addValue(v.name());
    }

    /** @return This enum type's name */
    public String getName() {
        return theName;
    }

    /**
     * @param name
     *            The new name for this enum
     */
    protected void setName(String name) {
        theName = name;
    }

    /** @return All values of this enum */
    public NavigableSet<EnumValue> getValues() {
        return Collections.unmodifiableNavigableSet(theValues);
    }

    /** @return All values of this enum, by name */
    public NavigableMap<String, EnumValue> getValuesByName() {
        return Collections.unmodifiableNavigableMap(theValuesByName);
    }

    /**
     * Creates a new value for this enum
     * 
     * @param name
     *            The name for the new value
     * @return The new value
     */
    protected EnumValue addValue(String name) {
        EnumValue value = theValuesByName.get(name);
        if (value != null)
            throw new IllegalArgumentException("Value " + name + " already exists");
        value = new EnumValue(this, name);
        theValues.add(value);
        theValuesByName.put(name, value);
        return value;
    }

    /**
     * Removes a value from this enum
     * 
     * @param name
     *            The name of the value to remove
     * @return The value that was removed
     */
    protected EnumValue removeValue(String name) {
        EnumValue value = theValuesByName.remove(name);
        if (value == null)
            throw new IllegalArgumentException("Value " + name + " does not exist");
        theValues.remove(value);
        return value;
    }

    /**
     * @param name
     *            The name of the enum constant to get
     * @return The given constant in this enum, or null if no such constant exists in this entity version
     */
    public EnumValue getValue(String name) {
		EnumValue value = theValuesByName.get(name);
		if (value == null)
			throw new IllegalArgumentException("No such constant " + theName + "." + name);
		return value;
    }

    @Override
    public Iterator<EnumValue> iterator() {
        return Collections.unmodifiableCollection(theValues).iterator();
    }

    /** @return The XML element representing this entity version */
    public Element toElement() {
        Element ret = new Element(theName);
        for (EnumValue value : theValues) {
            Element valueEl = new Element(value.getName());
            ret.addContent(valueEl);
        }
        return ret;
    }

    @Override
    public int compareTo(EnumType o) {
        return theName.compareTo(o.theName);
    }

    @Override
    public int hashCode() {
        return theName.hashCode() * 13 + theValues.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        return o instanceof EnumType && theName.equals(((EnumType) o).theName);
    }

    @Override
    public String toString() {
        return theName;
    }

    @Override
    public EnumType clone() {
        EnumType copy;
        try {
            copy = (EnumType) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        copy.theValues = new TreeSet<>();
        copy.theValuesByName = new TreeMap<>();

        for (EnumValue value : theValues) {
            EnumValue valueCopy = new EnumValue(copy, value.getName());
            copy.theValues.add(valueCopy);
            copy.theValuesByName.put(value.getName(), valueCopy);
        }
        return copy;
    }
}
