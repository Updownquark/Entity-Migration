package org.migration.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.migration.TypeGetter;

/**
 * This class represents a necessary hack. When migration was initially implemented, enums were supported as primitives by qualified type.
 * This made renaming types and adding, removing, or renaming enum values very difficult. Enums were then added to the migration framework
 * as migratable types, but legacy data sets created before this addition need to be seeded with a set of enums containing the values that
 * were present in those enums before the migratable enum feature was added.
 */
public interface EnumInitializingTypeGetter extends TypeGetter {
    /** A structure representing an enum type */
    class EnumStruct {
        public final String name;
        public final List<String> values;

        public EnumStruct(String name, String... values) {
            this.name = name;
            this.values = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
        }
    }

    /** @return The initial enum structures for the data set */
    Iterable<EnumStruct> getInitialEnums();
}
