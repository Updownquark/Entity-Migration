package org.migration.generic;

import java.util.Collections;
import java.util.List;

public class StandardFieldTags {
	public static final FieldTag<Boolean> nullable = new FieldTag<>("nullable", boolean.class, true);
	public static final FieldTag<List<String>> sorting = new FieldTag<>("sorting", (Class<List<String>>) (Class<?>) List.class,
		Collections.emptyList());
}
