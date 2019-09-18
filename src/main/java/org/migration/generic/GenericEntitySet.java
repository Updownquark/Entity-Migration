package org.migration.generic;

import java.util.Deque;

import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;

/** Represents a CRUD-able set of entities of various types that may all refer to each other by ID */
public interface GenericEntitySet {
	/** @return The types of entities in this entity set */
	EntityTypeSet getTypes();

	/**
	 * @param typeName The name of the type of the entity to get
	 * @param id The identifier value(s) of the entity to get
	 * @return The entity of the given type with the given identifier, or null if no such entity exists in this set
	 */
	default GenericEntity queryById(String typeName, Object... id) {
		EntityType type = getTypes().getEntityType(typeName);
		if (type == null)
			throw new IllegalArgumentException("No such entity type " + typeName + " in this entity set");
		return queryById(type, id);
	}

	/**
	 * @param entityType The type of the entity to get
	 * @param id The identifier value(s) of the entity to get
	 * @return The entity of the given type with the given identifier, or null if no such entity exists in this set
	 */
	GenericEntity queryById(EntityType entityType, Object... id);

	/**
	 * Queries an entity by field value
	 * 
	 * @param typeName The name of the entity type to query entities for
	 * @param fieldName The name of the field in the given entity type to query by
	 * @param fieldValue The field value to return entities for
	 * @return All entities in this entity set for which the value of the given field equals the given field value
	 */
	default Deque<GenericEntity> query(String typeName, String fieldName, Object fieldValue) {
		EntityType type = getTypes().getEntityType(typeName);
		if (type == null)
			throw new IllegalArgumentException("No such entity type " + typeName + " in this entity set");
		EntityField field = type.getField(fieldName);
		if (field == null)
			throw new IllegalArgumentException("No such field " + fieldName + " in entity type " + typeName);
		return query(field, fieldValue);
	}

	/**
	 * Queries an entity by field value
	 * 
	 * @param field The field in an entity type in this setto query by
	 * @param fieldValue The field value to return entities for
	 * @return All entities in this entity set for which the value of the given field equals the given field value
	 */
	Deque<GenericEntity> query(EntityField field, Object fieldValue);

	/**
	 * @param typeName The name of the entity type to get entities for
	 * @return All entities of the given type in this entity set
	 */
	default Deque<GenericEntity> queryAll(String typeName) {
		EntityType type = getTypes().getEntityType(typeName);
		if (type == null)
			throw new IllegalArgumentException("No such entity type " + typeName + " in this entity set");
		return queryAll(type);
	}

	/**
	 * @param entityType The entity type to get entities for
	 * @return All entities of the given type in this entity set
	 */
	Deque<GenericEntity> queryAll(EntityType entityType);

	/**
	 * Creates a new entity with a generated ID value in this entity set
	 *
	 * @param typeName The name of the type to create the entity for
	 * @return The new entity to configure
	 * @throws IllegalStateException If this entity set is currently migrating
	 */
	default GenericEntity addEntity(String typeName) {
		EntityType type = getTypes().getEntityType(typeName);
		if (type == null)
			throw new IllegalArgumentException("No such entity type " + typeName + " in this entity set");
		return addEntity(type, new Object[0]);
	}

	/**
	 * Creates a new entity with a generated ID value in this entity set
	 *
	 * @param entityType The type to create the entity for
	 * @return The new entity to configure
	 * @throws IllegalStateException If this entity set is currently migrating
	 */
	default GenericEntity addEntity(EntityType entityType) {
		return addEntity(entityType, new Object[0]);
	}

	/**
	 * Creates a new entity with a specified or generated ID value in this entity set
	 *
	 * @param typeName The name of the type to create the entity for
	 * @param id The suggested identity for the new entity if it is available. If this is zero-length or the identity is already taken by
	 *        another entity, a new identity will be automatically assigned
	 * @return The new entity to configure
	 * @throws IllegalStateException If this entity set is currently migrating
	 */
	default GenericEntity addEntity(String typeName, Object... id) {
		EntityType type = getTypes().getEntityType(typeName);
		if (type == null)
			throw new IllegalArgumentException("No such entity type " + typeName + " in this entity set");
		return addEntity(type, id);
	}

	/**
	 * Creates a new entity with a specified or generated ID value in this entity set
	 *
	 * @param entityType The type to create the entity for
	 * @param id The suggested identity for the new entity if it is available. If this is zero-length or the identity is already taken by
	 *        another entity, a new identity will be automatically assigned
	 * @return The new entity to configure
	 * @throws IllegalStateException If this entity set is currently migrating
	 */
	GenericEntity addEntity(EntityType entityType, Object... id);

	/**
	 * Creates a new entity with the same type and non-ID field values as the given entity
	 *
	 * @param entity The entity to copy
	 * @return The new entity, with the same field values as the given entity, but with a new ID
	 */
	GenericEntity copy(GenericEntity entity);

	/** @param entity The entity to remove from this set */
	void remove(GenericEntity entity);

	/**
	 * @param toReplace The entity to replace
	 * @param replacement The replacement for <code>toReplace</code>
	 */
	default void replaceEntity(GenericEntity toReplace, GenericEntity replacement) {
		for (EntityReference ref : getTypes().getReferences(toReplace.getType())) {
			for (GenericEntity e : query(ref.getReferenceField(), toReplace)) {
				ref.replace(e, toReplace, replacement);
			}
		}
	}

	/**
	 * @param toReplace The enum to replace
	 * @param replacement The replacement for <code>toReplace</code>
	 */
	default void replaceEnum(EnumValue toReplace, EnumValue replacement) {
		EnumType type = toReplace.getEnumType();
		for (EntityReference ref : getTypes().getReferences(type)) {
			for (GenericEntity e : query(ref.getReferenceField(), toReplace)) {
				ref.replace(e, toReplace, replacement);
			}
		}
	}

	/**
	 * Migrates this entity set
	 *
	 * @param migSet The migration set to process
	 * @param dissecter The dissecter to understand data types
	 */
	void migrate(MigrationSet migSet, TypeSetDissecter dissecter);
}
