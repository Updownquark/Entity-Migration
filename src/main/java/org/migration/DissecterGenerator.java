package org.migration;

import java.lang.reflect.Type;

/** Understand a type or a type hierarchy, generating appropriate {@link ValueDissecter} or {@link CollectionDissecter}s */
@FunctionalInterface
public interface DissecterGenerator {
    /**
     * @param type
     *            The specific type
     * @return A string representation of the sub-type of the given type within this generator's knowledge domain
     */
    default String getSubType(Class<?> type) {
        return null;
    }

	/**
	 * @param type
	 *            The declared field type
	 * @return Whether this generator requires or supports sub-type declaration for fields of the given type
	 */
	default boolean needsSubType(Type type) {
		return false;
	}

    /**
     * @param type
     *            The (possibly general) type to dissect
     * @param subType
     *            The {@link #getSubType(Class) sub-type} to dissect
     * @return A dissector that understands instances of the given type/sub-type
     */
    Dissecter dissect(Type type, String subType);
}
