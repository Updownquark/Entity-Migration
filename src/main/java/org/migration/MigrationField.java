package org.migration;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.persistence.Id;

/**
 * When specified on a field (actually the getter method) in an entity to be persisted, this annotation can provide additional information
 * about the field to the migration framework.
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface MigrationField {
    /**
     * @return Whether this field should be persisted or not. Overrides the presence or absence of @{@link javax.persistence.Transient
     *         Transient}
     */
    boolean persisted() default true;

    /**
     * @return Whether this field is the ID column for the entity. There are certain rare cases where tagging a field in a superclass
     *         with @{@link Id} will cause hibernate errors, but migration needs to recognize the field.
     */
    boolean id() default false;
}
