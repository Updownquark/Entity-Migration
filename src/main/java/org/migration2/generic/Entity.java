package org.migration2.generic;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface Entity {
	EntityType getType();
	EntityIdentity getId();
	ParameterMap<? extends EntityFieldValue<?>> getFieldValues();
}
