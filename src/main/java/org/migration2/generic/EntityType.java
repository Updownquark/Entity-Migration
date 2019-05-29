package org.migration2.generic;

import java.util.List;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface EntityType {
	String getName();

	List<EntityType> getSuperTypes();

	ParameterMap<? extends EntityField<? extends Comparable<?>>> getIdentity();

	ParameterMap<? extends EntityField<?>> getFields();

	default EntityIdentity createId(Comparable<?>... values) {
		ParameterMap<Comparable<?>> idValues = getIdentity().keySet().createMap();
		for (int i = 0; i < idValues.keySet().size(); i++)
			idValues.put(i, values[i]);
		return new EntityIdentity(this, idValues);
	}
}
