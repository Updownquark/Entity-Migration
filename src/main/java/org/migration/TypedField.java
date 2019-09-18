package org.migration;

import java.lang.reflect.Type;
import java.util.Comparator;

import org.migration.util.PersistenceUtils;

/** A typed field of a class */
public class TypedField {
    /** The type that this field is a member of */
    public final Class<?> declaringType;
    /** The name of this field */
    public final String name;
    /** The type of data that may be assigned to this field */
    public final Type type;
    /** Whether this field is an identifier for its type */
    public final boolean id;
    /**
     * If this field's value is determined by a reference from the typed entity, this is the name of that field on that entity. Null for
     * non-mapped fields.
     */
    public final String mapping;
    /** The columns of the target type by which this field should be sorted (collections only) */
    public final String[] ordering;
	/** The comparator by which to sort this field (collections only) */
	public final Comparator<?> sorting;

	private TypedField(Class<?> declaringType, String name, Type type, boolean id, String mapping, String[] ordering,
		Comparator<?> sorting) {
        this.declaringType = declaringType;
        this.name = name;
        this.type = type;
        this.id = id;
        this.mapping = mapping;
        this.ordering = ordering;
        this.sorting = sorting;
    }

    /** @return Whether this field's type is a collection whose values are ordered */
    public boolean isOrdered() {
        return (ordering == null || ordering.length == 0) && sorting == null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TypedField && ((TypedField) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return declaringType.getName() + "." + name + " (" + PersistenceUtils.toString(type) + ")";
    }

    /**
     * @param declaringType
     *            The type that declared the field to create
     * @param name
     *            The name for the field
     * @param type
     *            The type of the field
     * @return A builder that can create typed fields
     */
    public static Builder builder(Class<?> declaringType, String name, Type type) {
        return new Builder(declaringType, name, type);
    }

    /** Builds {@link TypedField}s */
    public static class Builder {
        private final Class<?> declaringType;
        private final String name;
        private final Type type;
        private boolean id = false;
        private String mapping = null;
        private String[] ordering = new String[0];
		private Comparator<?> sorting = null;

        private Builder(Class<?> declaring, String name, Type type) {
            declaringType = declaring;
            this.name = name;
            this.type = type;
        }

        /**
         * @param id
         *            Whether the field is an identifier of its type. False by default.
         * @return This builder, for chaining
         */
        public Builder id(@SuppressWarnings("hiding") boolean id) {
            if (id) {
                if (type == Integer.TYPE || type == Integer.class || type == Long.TYPE || type == Long.class || type == String.class) {
                } else {
					throw new IllegalStateException("Illegal type for identifier field: " + PersistenceUtils.toString(type) + " for field "
                            + declaringType + "." + name);
				}
            }
            this.id = id;
            return this;
        }

        /**
         * @param mapping
         *            The mapping for this field. See {@link TypedField#mapping}. Null by default
         * @return This builder, for chaining
         */
        public Builder mapping(@SuppressWarnings("hiding") String mapping) {
            this.mapping = mapping;
            return this;
        }

        /**
         * @param ordering
         *            The ordering for this field. See {@link TypedField#ordering}. Zero-length by default.
         * @return This builder, for chaining
         */
        public Builder ordering(@SuppressWarnings("hiding") String[] ordering) {
            this.ordering = ordering;
            return this;
        }

        /**
		 * 
		 * @param sort
		 *            The @{@link Comparator} annotation from the field's getter
		 * @return This builder, for chaining
		 */
		public Builder sort(Comparator<?> sort) {
            this.sorting = sort;
            return this;
        }

        /** @return The TypedField instance, built with the parameters set in this builder */
        public TypedField build() {
			return new TypedField(declaringType, name, type, id, mapping, ordering, sorting);
        }
    }
}