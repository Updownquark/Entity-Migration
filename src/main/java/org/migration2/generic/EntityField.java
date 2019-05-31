package org.migration2.generic;

import com.google.common.reflect.TypeToken;

public interface EntityField<F> {
	EntityType getEntity();
	boolean isId();

	int getFieldIndex();
	String getName();

	TypeToken<F> getType();
}
