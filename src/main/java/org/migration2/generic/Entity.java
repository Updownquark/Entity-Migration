package org.migration2.generic;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface Entity {
	EntityType getType();
	EntityIdentity getId();

	default Object getField(String fieldName){
		int index=getType().getFields().keySet().indexOf(fieldName);
		if(index>=0)
			return getField(index);
		index=getType().getIdentity().keySet().indexOf(fieldName);
		if(index>=0)
			return getId().g
	}

	ParameterMap<? extends EntityFieldValue<?>> getFieldValues();
}
