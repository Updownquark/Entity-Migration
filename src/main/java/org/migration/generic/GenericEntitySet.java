package org.migration.generic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.migrators.CustomMigrator;
import org.migration.migrators.EntityCreatedMigrator;
import org.migration.migrators.EntityMigrator;
import org.migration.migrators.EntityRemovedMigrator;
import org.migration.migrators.EntityRenameMigrator;
import org.migration.migrators.EntityTypeModificationMigrator;
import org.migration.migrators.EnumTypeModificationMigrator;
import org.migration.migrators.EnumValueRemovedMigrator;
import org.migration.migrators.EnumValueRenameMigrator;
import org.migration.migrators.FieldRemovedMigrator;
import org.migration.migrators.FieldRenameMigrator;
import org.migration.migrators.FieldTypeModificationMigrator;
import org.migration.migrators.ReplaceSuperMigrator;
import org.qommons.tree.Tree;
import org.qommons.tree.TreeBuilder;

/** A set of {@link GenericEntity}s */
public class GenericEntitySet {
    static final Comparator<EntityType> TYPE_COMPARE = EntityTypeSet.TYPE_COMPARE;

    private final EntityTypeSet theOldTypes;
    private final EntityTypeSet theCurrentTypes;
    private final TreeBuilder<EntitySetNode, EntityType> theEntities;
    private Map<String, String> theRenames;

    /**
     * @param types
     *            The types of entities that this entity set can support
     */
    public GenericEntitySet(EntityTypeSet types) {
        theCurrentTypes = types;
        theOldTypes = types.clone();
        theEntities = new TreeBuilder<>(TYPE_COMPARE, EntityType::getSuperType);
    }

    /** @return The types of entities at the beginning of the current migration */
    public EntityTypeSet getOldTypes() {
        return theOldTypes;
    }

    /** @return The types of entities at the current spot in the current migration */
    public EntityTypeSet getCurrentTypes() {
        return theCurrentTypes;
    }

    /**
     * @param typeName
     *            The name of the entity type to get entities for
     * @return All entities of the given type in this entity set
     */
    public List<GenericEntity> get(String typeName) {
        return get(typeName, false);
    }

    private List<GenericEntity> get(String typeName, boolean tolerateOldType) {
        EntityType type = theCurrentTypes.getEntityType(typeName);
        if (type == null && tolerateOldType) {
			type = theOldTypes.getEntityType(typeName);
		}
        if (type == null) {
			throw new IllegalArgumentException("Unrecognized type: " + typeName);
		}
        EntitySetNode node = theEntities.getNode(type, null);
        if (node == null) {
			return Collections.EMPTY_LIST;
		}
        List<GenericEntity> ret = new ArrayList<>();
        node.act(n -> {
            ret.addAll(n.theEntities.values());
        });
        return Collections.unmodifiableList(ret);
    }

    /**
     * @param typeName
     *            The name of the type of the entity to get
     * @param id
     *            The identifier value of the entity to get
     * @return The entity of the given type with the given identifier, or null if no such entity exists in this set
     */
    public GenericEntity get(String typeName, Object id) {
        EntityType type = theCurrentTypes.getEntityType(typeName);
        if (type == null) {
			throw new IllegalArgumentException("Unrecognized type: " + typeName);
		}
        EntitySetNode node = theEntities.getNode(type, null);
        if (node == null) {
			return null;
		}
        GenericEntity[] ret = new GenericEntity[1];
        node.act(n -> {
            if (ret[0] == null) {
				ret[0] = n.theEntities.get(id);
			}
        });
        return ret[0];
    }

    /**
     * Populates this non-migrating entity set with data
     *
     * @param typeName
     *            The name of the type to create the entity for
     * @return The new entity to configure
     * @throws IllegalStateException
     *             If this entity set is currently migrating
     */
    public GenericEntity addEntity(String typeName) {
		return addEntity(typeName, null);
	}

	/**
	 * Populates this non-migrating entity set with data
	 *
	 * @param typeName
	 *            The name of the type to create the entity for
	 * @param identity
	 *            The suggested identity for the new entity if it is available. If this is null or the identity is already taken by another
	 *            entity, a new identity will be automatically assigned
	 * @return The new entity to configure
	 * @throws IllegalStateException
	 *             If this entity set is currently migrating
	 */
	public GenericEntity addEntity(String typeName, Object identity) {
        EntityType type = theCurrentTypes.getEntityType(typeName);
        if (type == null) {
			throw new IllegalArgumentException("Unrecognized type: " + typeName);
		}
        EntityType oldType;
        if (theRenames != null && theRenames.containsKey(typeName)) {
			oldType = theOldTypes.getEntityType(theRenames.get(typeName));
		} else {
			oldType = theOldTypes.getEntityType(typeName);
		}
        if (oldType == null) {
			throw new IllegalStateException("No old type found for " + typeName);
		}
        EntitySetNode node = theEntities.getNode(type, EntitySetNode::new);
        EntitySetNode root=node;
        while (root.getParent() != null) {
			root = root.getParent();
		}
        Object newId;
		if (identity != null && !root.theEntities.containsKey(identity)) {
			newId = identity;
		}
        if (root.isEmpty()) {
			newId = firstId((Class<?>) type.getIdField().getType());
		} else {
            Object maxId = root.getLastId();
            newId = incrementId(maxId);
        }
        GenericEntity ret = new GenericEntity(oldType, type, this);
        ret.setIdentityInternal(newId);
        node.theEntities.put((Comparable<Object>) newId, ret);
        return ret;
    }

    private Object firstId(Class<?> type) {
        if (type == Integer.TYPE || type == Integer.class) {
			return 0;
		} else if (type == Long.TYPE || type == Long.class) {
			return 0L;
		} else if (type == String.class) {
			return "0";
		} else {
			throw new IllegalStateException("Cannot increment ID value of type " + type.getName());
		}
    }

    private static Object incrementId(Object id) {
        if (id instanceof Integer) {
			return ((Integer) id).intValue() + 1;
		} else if (id instanceof Long) {
			return ((Long) id).longValue() + 1;
		} else if(id instanceof String){
            String idStr=(String) id;
            if(idStr.length()==0) {
				return "0";
			}
            StringBuilder ret=new StringBuilder(idStr);
            int index=idStr.length()-1;
            if(ret.charAt(index)<'0' || ret.charAt(index)>'9'){
                ret.append('0');
                return ret.toString();
            }
            for (; index >= 0 && ret.charAt(index) >= '0' && ret.charAt(index) <= '9'; index--) {
                int digit=ret.charAt(index)-'0';
                digit++;
                ret.setCharAt(index, (char) ('0'+digit%10));
                if (digit < 10) {
					break;
				}
            }
            if (index < 0 || ret.charAt(index) < '0' || ret.charAt(index) > '9') {
				ret.insert(index + 1, '1');
			}
            return ret.toString();
        } else {
			throw new IllegalStateException("Cannot increment ID value of type " + id.getClass().getName());
		}
    }

    void idChanged(GenericEntity entity, Object oldId, Object newId) {
        EntityType type = theCurrentTypes.getEntityType(entity.getCurrentType().getName());
        if (type == null) {
			throw new IllegalArgumentException("Unrecognized type: " + entity.getCurrentType().getName());
		}
        EntitySetNode node = theEntities.getNode(type, null);
        if (node == null) {
			throw new IllegalStateException("No entities of type " + type + " exist in this entity set");
		}
        node.theEntities.remove(oldId);
        node.theEntities.put((Comparable<Object>) newId, entity);
    }

    /**
     * Copies an entity's fields into a new value
     *
     * @param entity
     *            The entity to copy
     * @return The new entity, with the same field values as the given entity, but with a new ID
     * @throws IllegalStateException
     *             If this entity set is currently migrating
     */
    public GenericEntity copyEntity(GenericEntity entity) {
        GenericEntity ret = addEntity(entity.getCurrentType().getName());
        EntityField idField = ret.getCurrentType().getIdField();
        for (EntityField field : ret.getCurrentType()) {
			if (field != idField) {
				ret.set(field.getName(), entity.get(field.getName()));
			}
		}
        return ret;
    }

    /**
     * @param entity
     *            The entity to remove from this set
     */
    public void removeEntity(GenericEntity entity) {
        EntityType type = entity.getCurrentType();
        // The entity may have already been deleted if this is a recursive call
        EntitySetNode node = theEntities.getNode(type, null);
        if (node == null || !node.theEntities.containsKey(entity.getIdentity())) {
			return;
		}
        Collection<EntityReference> refs = theCurrentTypes.getReferences(type);
        LinkedHashSet<GenericEntity> moreDeletions = new LinkedHashSet<>();
        for (EntityReference ref : refs) {
            if (!((EntityType) ref.getReferenceType()).isAssignableFrom(type)) {
				continue;
			}
            moreDeletions.addAll(ref.getReferring(entity, this, true, false));
            for (GenericEntity referring : ref.getReferring(entity, this, false, true)) {
				ref.delete(referring, entity);
			}
        }
        moreDeletions.remove(entity);
        _remove(entity);

        if (!moreDeletions.isEmpty()) {
            System.out.println(
                    "As a consequence of the deletion of " + entity + ", the following entities will also be deleted: " + moreDeletions);
        }
        for (GenericEntity toDelete : moreDeletions) {
			removeEntity(toDelete);
		}
    }

    private void _remove(GenericEntity entity) {
        EntitySetNode node = theEntities.getNode(entity.getCurrentType(), null);
        if (node == null) {
			return;
		}
        node.theEntities.remove(entity.getIdentity());
    }

    /**
     * @param value
     *            The enum to remove from this set
     */
    private void removeEnum(EnumValue value) {
        EnumType type = value.getEnumType();
        Collection<EntityReference> refs = theCurrentTypes.getReferences(type);
        LinkedHashSet<GenericEntity> moreDeletions = new LinkedHashSet<>();
        for (EntityReference ref : refs) {
            if (!type.equals(ref.getReferenceType())) {
				continue;
			}
            moreDeletions.addAll(ref.getReferring(value, this, true, false));
            for (GenericEntity referring : ref.getReferring(value, this, false, true)) {
				ref.delete(referring, value);
			}
        }

        if (!moreDeletions.isEmpty()) {
            System.out.println("As a consequence of the deletion of " + type + "." + value
                    + ", the following entities will also be deleted: " + moreDeletions);
        }
        for (GenericEntity toDelete : moreDeletions) {
			removeEntity(toDelete);
		}
    }

    /**
     * @param toReplace
     *            The entity to replace
     * @param replacement
     *            The replacement for <code>toReplace</code>
     */
    public void replaceEntity(GenericEntity toReplace, GenericEntity replacement) {
        EntityType type = toReplace.getCurrentType();
        if (theCurrentTypes.getEntityType(type.getName()) == null) {
			throw new IllegalArgumentException("Unrecognized entity type: " + type.getName());
		}

        EntitySetNode node = theEntities.getNode(type, null);
        if (node == null) {
			return;
		}

        node.theEntities.remove(toReplace.getIdentity());
        for (EntityReference ref : theCurrentTypes.getReferences(type)) {
			for (GenericEntity e : get(ref.getReferenceField().entity.getName())) {
				ref.replace(e, toReplace, replacement);
			}
		}
    }

    /**
     * @param toReplace
     *            The enum to replace
     * @param replacement
     *            The replacement for <code>toReplace</code>
     */
    public void replaceEnum(EnumValue toReplace, EnumValue replacement) {
        EnumType type = toReplace.getEnumType();
        if (theCurrentTypes.getEnumType(type.getName()) == null) {
			throw new IllegalArgumentException("Unrecognized enum type: " + type.getName());
		}

        for (EntityReference ref : theCurrentTypes.getReferences(type)) {
			for (GenericEntity e : get(ref.getReferenceField().entity.getName())) {
				ref.replace(e, toReplace, replacement);
			}
		}
    }

    /**
     * Migrates this entity set
     *
     * @param migSet
     *            The migration set to process
     * @param dissecter
     *            The dissecter to understand data types
     */
    public void migrate(MigrationSet migSet, TypeSetDissecter dissecter) {
        theRenames = new LinkedHashMap<>();
        theCurrentTypes.setVersionDate(migSet.getDate());
        for (EntityMigrator migrator : migSet.getMigrators()) {
			if (migrator instanceof CustomMigrator) {
				((CustomMigrator) migrator).init(theCurrentTypes, dissecter);
			}
            System.out.println("\tMigrating with " + migrator);
            migrate(migrator, dissecter);
        }
        theOldTypes.setVersionDate(migSet.getDate());
        theRenames = null;
    }

    private void migrate(EntityMigrator migrator, TypeSetDissecter dissecter) {
        String entity = migrator.getEntityName();
        EntityType type = theCurrentTypes.getEntityType(entity);
        if (type == null && !(migrator instanceof EntityCreatedMigrator) && !(migrator instanceof EnumTypeModificationMigrator)) {
			throw new IllegalArgumentException("Unrecognized entity type " + entity + " for entity migrator " + migrator);
		}

        if (migrator instanceof EntityTypeModificationMigrator) {
            EntitySetNode node = type == null ? null : theEntities.getNode(type, null);
            switch (((EntityTypeModificationMigrator) migrator).getType()) {
            case creation:
                theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
                type = theCurrentTypes.getEntityType(migrator.getEntityName());
                node = theEntities.getNode(type, EntitySetNode::new);
                break;
            case rename:
                if (node != null) {
                    theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
                    node = theEntities.getNode(type, null);
                    String rename = ((EntityRenameMigrator) migrator).afterName;
                    theRenames.put(rename, entity);
                    if (node.theParent != null) {
						node.theParent.theChildren.add(node);
					} else {
						theEntities.addRoot(node);
					}
                    entity = rename;
				} else {
					theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
				}
                break;
            case replaceSuper:
                if (node != null) {
                    node = theEntities.getNode(type, null);
                    if (node.theParent != null) {
						node.theParent.theChildren.remove(node);
					} else {
						theEntities.remove(type);
					}
                    EntityType newSuperType = ((ReplaceSuperMigrator) migrator).newSuperType;
                    EntitySetNode newParentNode = newSuperType == null ? null : theEntities.getNode(newSuperType, EntitySetNode::new);
                    theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
                    if (newParentNode == null) {
						theEntities.addRoot(node);
					} else {
						newParentNode.theChildren.add(node);
					}
				} else {
					theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
				}
                break;
            case deletion:
            case fieldAddition:
            case fieldRemoval:
            case fieldRename:
            case fieldNullability:
                theCurrentTypes.migrate((EntityTypeModificationMigrator) migrator, true);
                break;
            }
        } else if (migrator instanceof EnumTypeModificationMigrator) {
            EnumType enumType = theCurrentTypes.getEnumType(((EnumTypeModificationMigrator) migrator).getEntityName());
            switch (((EnumTypeModificationMigrator) migrator).getType()) {
            case creation:
            case deletion:
            case rename:
            case valueAddition:
                // No impact on existing values
                theCurrentTypes.migrate((EnumTypeModificationMigrator) migrator, true);
                break;
            case valueRemoval:
                EnumValue toRemove = enumType.getValue(((EnumValueRemovedMigrator) migrator).value);
                if (toRemove == null) {
					throw new IllegalArgumentException(
                            "Unrecognized " + enumType + " value " + ((EnumValueRemovedMigrator) migrator).value);
				}
                removeEnum(toRemove);
                theCurrentTypes.migrate((EnumTypeModificationMigrator) migrator, true);
                break;
            case valueRename:
                EnumValue toReplace = enumType.getValue(((EnumValueRenameMigrator) migrator).beforeName);
                if (toReplace == null) {
					throw new IllegalArgumentException(
                            "Unrecognized " + enumType + " value " + ((EnumValueRenameMigrator) migrator).beforeName);
				}
				if (enumType.getValuesByName().containsKey(((EnumValueRenameMigrator) migrator).afterName)) {
					throw new IllegalArgumentException(
                            "Renamed " + enumType + " value " + ((EnumValueRenameMigrator) migrator).afterName + " already exists");
				}
                theCurrentTypes.migrate((EnumTypeModificationMigrator) migrator, true);
				EnumValue replacement = enumType.getValue(((EnumValueRenameMigrator) migrator).afterName);
                replaceEnum(toReplace, replacement);
                break;
            }
        }
        if (type != null) {
            List<GenericEntity> entities = get(entity, true);
            GenericEntity[] listCopy = entities.toArray(new GenericEntity[entities.size()]);
            int removed = 0;
            int replaced = 0;
            for (GenericEntity original : listCopy) {
                GenericEntity replace = migrator.migrate(original, this, dissecter);
                if (replace == null) {
                    removed++;
                    removeEntity(original);
                } else if (replace != original) {
                    replaced++;
                    replaceEntity(original, replace);
                }
                if (migrator instanceof FieldRenameMigrator || migrator instanceof FieldRemovedMigrator) {
					replace.fieldRemoved(((FieldTypeModificationMigrator) migrator).field);
				}
            }
            if (removed > 0 || replaced > 0) {
                String msg = "";
                if (removed > 0) {
					msg += "Removed " + removed + " entit" + (removed == 1 ? "y" : "ies");
				}
                if (replaced > 0) {
					msg += (removed > 0 ? ", r" : "R") + "eplaced " + replaced + " entit" + (replaced == 1 ? "y" : "ies");
				}
                System.out.println("\t\t" + msg);
            }
        }
        if (migrator instanceof EntityTypeModificationMigrator) {
            if (migrator instanceof EntityRemovedMigrator) {
                theEntities.remove(theOldTypes.getEntityType(migrator.getEntityName()));
                theRenames.remove(entity);
            }
            theOldTypes.migrate((EntityTypeModificationMigrator) migrator, true);
        } else if (migrator instanceof EnumTypeModificationMigrator) {
            theOldTypes.migrate((EnumTypeModificationMigrator) migrator, true);
        }
    }

    private static class EntitySetNode implements Tree<EntityType, EntitySetNode, NavigableSet<EntitySetNode>> {
        static final Comparator<EntitySetNode> NODE_COMPARE = new Comparator<EntitySetNode>() {
            @Override
            public int compare(EntitySetNode node1, EntitySetNode node2) {
                return TYPE_COMPARE.compare(node1.theType, node2.theType);
            }
        };

        final EntitySetNode theParent;
        final EntityType theType;
        final NavigableSet<EntitySetNode> theChildren;
        final NavigableMap<Comparable<Object>, GenericEntity> theEntities;

        EntitySetNode(EntityType type, EntitySetNode parent) {
            theParent = parent;
            theType = type;
            theChildren = new TreeSet<>(NODE_COMPARE);
            theEntities = new TreeMap<>();
        }

        @Override
        public EntityType getValue() {
            return theType;
        }

        EntitySetNode getParent() {
            return theParent;
        }

        @Override
        public NavigableSet<EntitySetNode> getChildren() {
            return theChildren;
        }

        void act(Consumer<? super EntitySetNode> action) {
            action.accept(this);
            for (EntitySetNode child : theChildren) {
				child.act(action);
			}
        }

        boolean isEmpty() {
            if (!theEntities.isEmpty()) {
				return false;
			}
            for (EntitySetNode child : theChildren) {
				if (!child.isEmpty()) {
					return false;
				}
			}
            return true;
        }

        @Override
        public String toString() {
            return theType.toString();
        }

        /** @return The lowest ID stored in this node or its children */
        @SuppressWarnings("unused")
        public Comparable<Object> getFirstId() {
            Comparable<Object> ret = null;
            if (!theEntities.isEmpty()) {
				ret = theEntities.firstKey();
			}
            for (EntitySetNode child : theChildren) {
                Comparable<Object> childFirstId = child.getFirstId();
                if (ret == null || (childFirstId != null && ret.compareTo(childFirstId) > 0)) {
					ret = childFirstId;
				}
            }
            return ret;
        }

        /** @return The highest ID stored in this node or its children */
        public Comparable<Object> getLastId() {
            Comparable<Object> ret = null;
            if (!theEntities.isEmpty()) {
				ret = theEntities.lastKey();
			}
            for (EntitySetNode child : theChildren) {
                Comparable<Object> childFirstId = child.getLastId();
                if (ret == null || (childFirstId != null && ret.compareTo(childFirstId) < 0)) {
					ret = childFirstId;
				}
            }
            return ret;
        }
    }
}
