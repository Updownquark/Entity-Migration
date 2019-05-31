package org.migration2.generic;

import java.util.Objects;

import org.observe.util.TypeTokens;
import org.qommons.collect.ParameterSet.ParameterMap;

public class EntityIdentity implements FieldValues, Comparable<EntityIdentity> {
	private final ParameterMap<Comparable<?>> theValues;

	public EntityIdentity(EntityType type, ParameterMap<Comparable<?>> values) {
		theValues = values;
		for (int i = 0; i < values.keySet().size(); i++) {
			if (values.get(i) == null) {
				if (type.getIdentity().get(i).getType().isPrimitive())
					throw new IllegalArgumentException("Null is not valid for parameter " + type.getIdentity().get(i).getName() + ", type "
						+ type.getIdentity().get(i).getType());
			} else if (!TypeTokens.get().isInstance(type.getIdentity().get(i).getType(), values.get(i)))
				throw new IllegalArgumentException(
					values.get(i) + ", type " + values.get(i).getClass().getName() + " is not valid for parameter "
						+ type.getIdentity().get(i).getName() + ", type " + type.getIdentity().get(i).getType());
		}
	}

	@Override
	public Object getField(String fieldName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getField(int fieldIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <F> F getField(EntityField<F> field) {
		if(field.get
		return 
		// TODO Auto-generated method stub
		return null;
	}

	public ParameterMap<Comparable<?>> getValues() {
		return theValues;
	}

	@Override
	public int compareTo(EntityIdentity o) {
		if (!theType.equals(o.theType))
			throw new IllegalArgumentException("Cannot compare identities of different entity types: " + theType + " and " + o);
		for (int i = 0; i < theType.getIdentity().keySet().size(); i++) {
			int comp = ((Comparable<Object>) theValues.get(i)).compareTo(o.theValues.get(i));
			if (comp != 0)
				return comp;
		}
		return 0;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (int i = 0; i < theValues.keySet().size(); i++) {
			if (i != 0)
				hash = hash * 31;
			hash += Objects.hashCode(theValues.get(i));
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof EntityIdentity))
			return false;
		EntityIdentity other = (EntityIdentity) obj;
		if (!theType.equals(theType))
			return false;
		for (int i = 0; i < theValues.keySet().size(); i++)
			if (!Objects.equals(theValues.get(i), other.theValues.get(i)))
				return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(theType).append('(');
		for (int i = 0; i < theValues.keySet().size(); i++) {
			if (i > 0)
				str.append(',');
			str.append(theType.getIdentity().get(i).getName()).append('=').append(theValues.get(i));
		}
		str.append(')');
		return str.toString();
	}
}
