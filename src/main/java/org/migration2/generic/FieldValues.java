package org.migration2.generic;

public interface FieldValues {
	Object getField(String fieldName);

	Object getField(int fieldIndex);

	<F> F getField(EntityField<F> field);
}
