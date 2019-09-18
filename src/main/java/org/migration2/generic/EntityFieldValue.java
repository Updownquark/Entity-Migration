package org.migration2.generic;

import java.util.function.Supplier;

public interface EntityFieldValue<F> extends Supplier<F> {
	EntityField<F> getType();

	Entity getEntity();
}
