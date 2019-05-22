package org.migration.generic;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.migration.TypeGetter;
import org.migration.TypeSetDissecter;
import org.migration.ValueDissecter;
import org.migration.migrators.EntityCreatedMigrator;
import org.migration.migrators.EntityRemovedMigrator;
import org.migration.migrators.EntityRenameMigrator;
import org.migration.migrators.EntityTypeModificationMigrator;
import org.migration.migrators.EnumCreatedMigrator;
import org.migration.migrators.EnumRemovedMigrator;
import org.migration.migrators.EnumRenameMigrator;
import org.migration.migrators.EnumTypeModificationMigrator;
import org.migration.migrators.EnumValueAddedMigrator;
import org.migration.migrators.EnumValueRemovedMigrator;
import org.migration.migrators.EnumValueRenameMigrator;
import org.migration.migrators.FieldAddedMigrator;
import org.migration.migrators.FieldRemovedMigrator;
import org.migration.migrators.FieldRenameMigrator;
import org.migration.migrators.NullabilityMigrator;
import org.migration.migrators.ReplaceSuperMigrator;
import org.migration.util.EnumInitializingTypeGetter;
import org.migration.util.PersistenceUtils;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.tree.Tree;
import org.qommons.tree.TreeBuilder;

/** A set of entity types */
public class EntityTypeSet implements Iterable<EntityType>, Cloneable {
    static final Comparator<EntityType> TYPE_COMPARE = new Comparator<EntityType>() {
        @Override
        public int compare(EntityType type1, EntityType type2) {
            return type1.getName().compareToIgnoreCase(type2.getName());
        }
    };

    private Date theVersionDate;

    private TreeBuilder<EntityTypeNode, EntityType> theTree;
    private NavigableMap<String, EntityTypeNode> theEntitiesByName;
    private Map<Class<?>, EntityTypeNode> theNodesByClassMapping;
    private NavigableMap<String, EnumTypeNode> theEnumsByName;
    private Map<Class<?>, EnumTypeNode> theEnumsByClassMapping;

    /**
     * @param versionDate
     *            The current version for the entity type set
     */
    public EntityTypeSet(Date versionDate) {
        theVersionDate = versionDate;
        theTree = new TreeBuilder<>(TYPE_COMPARE, EntityType::getSuperType);
        theEntitiesByName = new TreeMap<>();
        theNodesByClassMapping = new LinkedHashMap<>();
        theEnumsByName = new TreeMap<>();
        theEnumsByClassMapping = new LinkedHashMap<>();
    }

    /** @return This type set's current version */
    public Date getVersionDate() {
        return theVersionDate;
    }

    /**
     * @param versionDate
     *            The new version for this type set
     */
    protected void setVersionDate(Date versionDate) {
        theVersionDate = versionDate;
    }

    @Override
    public Iterator<EntityType> iterator() {
        return Collections
                .unmodifiableCollection(theEntitiesByName.values().stream().map(node -> node.theType).collect(Collectors.toList()))
                .iterator();
    }

    /** @return All enum types defined in this type set */
    public NavigableSet<EnumType> enums() {
        return Collections.unmodifiableNavigableSet(
                theEnumsByName.values().stream().map(node -> node.theType).collect(Collectors.toCollection(() -> new TreeSet<>())));
    }

    /**
     * @param name
     *            The name of the type to get
     * @return The entity type with the given name in this set or null if no such type exists in this set
     */
    public EntityType getEntityType(String name) {
        EntityTypeNode node = theEntitiesByName.get(name);
        return node == null ? null : node.theType;
    }

    /**
     * @param name
     *            The name of the type to get
     * @return The enum type with the given name in this set or null if no such type exists in this set
     */
    public EnumType getEnumType(String name) {
        EnumTypeNode node = theEnumsByName.get(name);
        return node == null ? null : node.theType;
    }

    /**
     * @param clazz
     *            The class to map
     * @param type
     *            The entity type to map the class to
     */
    public void map(Class<?> clazz, EntityType type) {
        EntityTypeNode node = theEntitiesByName.get(type.getName());
        if (node == null) {
			throw new IllegalArgumentException("Unrecognized entity type \"" + type.getName() + "\"");
		}
        node.theClassMapping = clazz;
        theNodesByClassMapping.put(clazz, node);
        for (EntityType entityType : theTree.values()) {
			entityType.map(clazz, type);
		}
    }

    /**
     * @param clazz
     *            The enum class to map
     * @param type
     *            The enum type to map the class to
     */
    public void map(Class<? extends Enum<?>> clazz, EnumType type) {
        EnumTypeNode node = theEnumsByName.get(type.getName());
        if (node == null) {
			throw new IllegalArgumentException("Unrecognized enum type \"" + type.getName() + "\"");
		}
        node.theClassMapping = clazz;
        theEnumsByClassMapping.put(clazz, node);
        for (EntityType entityType : theTree.values()) {
			entityType.map(clazz, type);
		}
    }

    /** @return The entity class-type mappings in this type set */
    public Iterable<Map.Entry<Class<?>, EntityType>> getEntityMappings() {
        return (Iterable<Entry<Class<?>, EntityType>>) (Iterable<?>) Collections.unmodifiableCollection(theNodesByClassMapping.entrySet()
                .stream().map(entry -> new SimpleMapEntry<>(entry.getKey(), entry.getValue().theType)).collect(Collectors.toList()));
    }

    /** @return The enum class-type mappings in this type set */
    public Iterable<Map.Entry<Class<? extends Enum<?>>, EnumType>> getEnumMappings() {
        return (Iterable<Entry<Class<? extends Enum<?>>, EnumType>>) (Iterable<?>) Collections
                .unmodifiableCollection(theEnumsByClassMapping.entrySet().stream()
                        .map(entry -> new SimpleMapEntry<>(entry.getKey(), entry.getValue().theType)).collect(Collectors.toList()));
    }

    /**
     * @param clazz
     *            The class to get the mapped type for
     * @return The type mapped to the given class in this type set, or null if no type has been mapped to the given class
     */
    public EntityType getEntityType(Class<?> clazz) {
        EntityTypeNode node = theNodesByClassMapping.get(clazz);
        return node == null ? null : node.theType;
    }

    /**
     * @param clazz
     *            The enum class to get the mapped type for
     * @return The enum type mapped to the given class in this type set, or null if no type has been mapped to the given class
     */
    public EnumType getEnumType(Class<?> clazz) {
        EnumTypeNode node = theEnumsByClassMapping.get(clazz);
        return node == null ? null : node.theType;
    }

    /**
     * @param type
     *            The entity type
     * @return The class mapped to the given entity type, or null if the given type does not have a class mapped to it
     */
    public Class<?> getMappedEntity(EntityType type) {
        EntityTypeNode node = theEntitiesByName.get(type.getName());
        if (node == null) {
			throw new IllegalArgumentException("Unrecognized entity type \"" + type.getName() + "\"");
		}
        return node.theClassMapping;
    }

    /**
     * @param type
     *            The enum type
     * @return The class mapped to the given entity type, or null if the given type does not have a class mapped to it
     */
    public Class<? extends Enum<?>> getMappedEnum(EnumType type) {
        EnumTypeNode node = theEnumsByName.get(type.getName());
        if (node == null) {
			throw new IllegalArgumentException("Unrecognized enum type \"" + type.getName() + "\"");
		}
        return node.theClassMapping;
    }

    /**
     * @param type
     *            The type to get references of
     * @return All references to the given type in this entity type set
     */
    public Collection<EntityReference> getReferences(Type type) {
        LinkedHashSet<EntityReference> ret = new LinkedHashSet<>();
        for (EntityType t : this) {
			ret.addAll(t.getReferences(type));
		}
        return ret;
    }

    /**
     * @param entity
     *            The entity type to add to this set
     */
    public void addType(EntityType entity) {
        EntityTypeNode node = theEntitiesByName.get(entity.getName());
        if (node != null) {
            if (node.theType == entity) {
				return;
			} else {
				throw new IllegalArgumentException("An entity type named " + entity.getName() + " already exists in this set");
			}
        }
        node = theTree.getNode(entity, this::createNode);
        if (node.theParent != null) {
			entity.internalSetSuperType(node.theParent.theType);
		}
    }

    private void addNode(EntityTypeNode node) {
        if (node.theParent != null) {
			node.theParent.theChildren.add(node);
		} else {
			theTree.addRoot(node);
		}
        theEntitiesByName.put(node.theType.getName(), node);
        if (node.theClassMapping != null) {
			theNodesByClassMapping.put(node.theClassMapping, node);
		}
    }

    private EntityTypeNode createNode(EntityType type, EntityTypeNode parent) {
        EntityTypeNode ret = new EntityTypeNode(type, parent);
        theEntitiesByName.put(type.getName(), ret);
        return ret;
    }

    /**
     * @param enumType
     *            The enum type to add to this set
     */
    public void addType(EnumType enumType) {
        EnumTypeNode node = theEnumsByName.get(enumType.getName());
        if (node != null) {
            if (node.theType == enumType) {
				return;
			} else {
				throw new IllegalArgumentException("An enum type named " + enumType.getName() + " already exists in this set");
			}
        }
        node = new EnumTypeNode(enumType);
        theEnumsByName.put(enumType.getName(), node);
    }

    private void addNode(EnumTypeNode node) {
        theEnumsByName.put(node.theType.getName(), node);
        if (node.theClassMapping != null) {
			theEnumsByClassMapping.put(node.theClassMapping, node);
		}
    }

    /**
     * @param entity
     *            The entity type to remove from this set
     */
    public void removeType(EntityType entity) {
        EntityTypeNode node = theEntitiesByName.get(entity.getName());
        if (node == null) {
			throw new IllegalArgumentException("No such entity " + entity.getName() + " in this type set");
		}
        if (node.theType != entity) {
			throw new IllegalArgumentException("An entity type named " + entity.getName()
                    + " exists in this set but is not equivalent to the given entity");
		}
        if (!node.theChildren.isEmpty()) {
			throw new IllegalArgumentException(entity.getName() + " is extended by " + node.theChildren
                    + "; it cannot be removed before the subtype is removed");
		}
        Collection<EntityReference> refs = getReferences(entity);
        for (EntityReference ref : refs) {
			// Can still remove if the reference type is a super-type of the type to be removed
            if (ref.getReferenceType() != entity && entity.isAssignableFrom((EntityType) ref.getReferenceType())) {
				throw new IllegalArgumentException("Field " + ref.getReferenceField() + " uses type " + entity.getName()
						+ "; type " + entity.getName() + " cannot be removed before this field is removed or modified");
			}
		}

        removeNode(node);
    }

    private void removeNode(EntityTypeNode node) {
        theTree.remove(node.theType);
        theEntitiesByName.remove(node.theType.getName());
        Iterator<EntityTypeNode> mappedEntities = theNodesByClassMapping.values().iterator();
        while (mappedEntities.hasNext()) {
			if (mappedEntities.next() == node) {
				mappedEntities.remove();
			}
		}
    }

    /**
     * @param enumType
     *            The enum type to remove from this set
     */
    public void removeType(EnumType enumType) {
        EnumTypeNode node = theEnumsByName.get(enumType.getName());
        if (node == null) {
			throw new IllegalArgumentException("No such enum " + enumType.getName() + " in this type set");
		}
        if (node.theType != enumType) {
			throw new IllegalArgumentException(
                    "An enum type named " + enumType.getName() + " exists in this set but is not equivalent to the given entity");
		}
        Collection<EntityReference> refs = getReferences(enumType);
        for (EntityReference ref : refs) {
			// Can still remove if the reference type is a super-type of the type to be removed
            if (ref.getReferenceType() != enumType && enumType.equals(ref.getReferenceType())) {
				throw new IllegalArgumentException("Field " + ref.getReferenceField() + " uses type " + enumType.getName()
                        + "; it cannot be removed before this field is removed or modified");
			}
		}

        removeNode(node);
    }

    private void removeNode(EnumTypeNode node) {
        theEnumsByName.remove(node.theType.getName());
        Iterator<EnumTypeNode> mappedEntities = theEnumsByClassMapping.values().iterator();
        while (mappedEntities.hasNext()) {
			if (mappedEntities.next() == node) {
				mappedEntities.remove();
			}
		}
    }

    /**
     * @param type
     *            The type to replace
     * @return A type identical to the given type but whose component entity and enum types are supplied by this type set
     */
    public Type replaceType(Type type) {
        if (type instanceof EntityType) {
			return getEntityType(((EntityType) type).getName());
		} else if (type instanceof EnumType) {
            return getEnumType(((EnumType) type).getName());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] args = new Type[pt.getActualTypeArguments().length];
            boolean different = false;
            for (int i = 0; i < args.length; i++) {
                args[i] = replaceType(pt.getActualTypeArguments()[i]);
                if (args[i] != pt.getActualTypeArguments()[i]) {
					different = true;
				}
            }
            if (different) {
				return PersistenceUtils.parameterize((Class<?>) pt.getRawType(), args);
			} else {
				return type;
			}
        } else {
			return type;
		}
    }

    /** Removes all entity types from this type set */
    public void clear() {
        theTree.clear();
        theEntitiesByName.clear();
        theNodesByClassMapping.clear();
        theEnumsByName.clear();
        theEnumsByClassMapping.clear();
    }

    /**
     * @param migrator
     *            The migrator to apply to this type set
     * @param forward
     *            Whether to migrate forward through the migration or apply it in reverse
     */
    public void migrate(EntityTypeModificationMigrator migrator, boolean forward) {
        EntityTypeNode node = theEntitiesByName.get(migrator.getEntityName());
        switch (migrator.getType()) {
        case creation:
            EntityCreatedMigrator eCreate = (EntityCreatedMigrator) migrator;
            if (forward) {
				addType(eCreate.entity.clone());
			} else {
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
				}
                removeType(node.theType);
            }
            break;
        case deletion:
            EntityRemovedMigrator eRemove = (EntityRemovedMigrator) migrator;
            if (forward) {
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
				}
                removeType(node.theType);
            } else {
				addType(eRemove.entity);
			}
            break;
        case rename:
            EntityRenameMigrator eRename = (EntityRenameMigrator) migrator;
            if (forward) {
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
				}
                removeNode(node);
                node.theType.setName(eRename.afterName);
                addNode(node);
            } else {
                node = theEntitiesByName.get(eRename.afterName);
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized entity type " + eRename.afterName + " for migrator " + migrator);
				}
                removeNode(node);
                node.theType.setName(migrator.getEntityName());
                addNode(node);
            }
            break;
        case replaceSuper:
            ReplaceSuperMigrator eReplace = (ReplaceSuperMigrator) migrator;
            if (forward) {
                if (node == null) {
					throw new IllegalArgumentException(
                            "Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
				}
				theTree.remove(node.theType);
                if (node.theParent != null) {
					node.theParent.getChildren().remove(node);
				}
				node.theType.setSuperType(eReplace.newSuperType);
				_reAddType(node);
            } else {
				throw new IllegalArgumentException("replace-super does not currently support reverse migration");
			}
            break;
        case fieldAddition:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            FieldAddedMigrator fCreate = (FieldAddedMigrator) migrator;
            if (forward) {
				node.theType.addField(fCreate.field, replaceType(fCreate.type), fCreate.nullable, fCreate.map,
                        fCreate.sorting);
			} else {
				node.theType.removeField(fCreate.field);
			}
            break;
        case fieldRemoval:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            FieldRemovedMigrator fRemove = (FieldRemovedMigrator) migrator;
            // Need to ensure no mapped fields refer to this field
            for (EntityType entity : this) {
				for (EntityField field : entity) {
					if (fRemove.field.equals(field.getMappingField()) && PersistenceUtils.getMappedType(field.getType()).equals(entity)) {
						throw new IllegalArgumentException("Field " + fRemove.getEntityName() + "." + fRemove.field
                                + " is referred to by the mapping field of " + field);
					}
				}
			}

            if (forward) {
				node.theType.removeField(fRemove.field);
			} else {
				node.theType.addField(fRemove.field, fRemove.type, fRemove.nullable, fRemove.map, fRemove.sorting);
			}
            break;
        case fieldRename:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            FieldRenameMigrator fRename = (FieldRenameMigrator) migrator;
            // If any mapped fields refer to this field, the mapped field names must be changed as well
            for (EntityType entity : this) {
				for (EntityField field : entity) {
					if (fRename.field.equals(field.getMappingField()) && PersistenceUtils.getMappedType(field.getType()).equals(entity)) {
                        entity.removeField(field.getName());
                        entity.addField(field.getName(), field.getType(), field.isNullable(), fRename.afterName, field.getSorting());
                    }
				}
			}
            if (forward) {
				node.theType.renameField(fRename.beforeName, fRename.afterName);
			} else {
				node.theType.renameField(fRename.afterName, fRename.beforeName);
			}
            break;
        case fieldNullability:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized entity type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            NullabilityMigrator nul = (NullabilityMigrator) migrator;
            node.theType.getField(nul.field).setNullable(nul.nullable);
            break;
        }
    }

    /**
     * @param migrator
     *            The migrator to apply to this type set
     * @param forward
     *            Whether to migrate forward through the migration or apply it in reverse
     */
    public void migrate(EnumTypeModificationMigrator migrator, boolean forward) {
        EnumTypeNode node = theEnumsByName.get(migrator.getEntityName());
        switch (migrator.getType()) {
        case creation:
            EnumCreatedMigrator eCreate = (EnumCreatedMigrator) migrator;
            if (forward) {
				addType(eCreate.enumType.clone());
			} else {
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized enum type " + migrator.getEntityName() + " for migrator " + migrator);
				}
                removeType(node.theType);
            }
            break;
        case deletion:
            EnumRemovedMigrator eRemove = (EnumRemovedMigrator) migrator;
            if (forward) {
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized enum type " + migrator.getEntityName() + " for migrator " + migrator);
				}
                removeType(node.theType);
            } else {
				addType(eRemove.enumType);
			}
            break;
        case rename:
            EnumRenameMigrator eRename = (EnumRenameMigrator) migrator;
            if (forward) {
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized enum type " + migrator.getEntityName() + " for migrator " + migrator);
				}
                removeNode(node);
                node.theType.setName(eRename.afterName);
                addNode(node);
            } else {
                node = theEnumsByName.get(eRename.afterName);
                if (node == null) {
					throw new IllegalArgumentException("Unrecognized enum type " + eRename.afterName + " for migrator " + migrator);
				}
                removeNode(node);
                node.theType.setName(migrator.getEntityName());
                addNode(node);
            }
            break;
        case valueAddition:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized enum type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            EnumValueAddedMigrator fCreate = (EnumValueAddedMigrator) migrator;
            if (forward) {
				node.theType.addValue(fCreate.value);
			} else {
				node.theType.removeValue(fCreate.value);
			}
            break;
        case valueRemoval:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized enum type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            EnumValueRemovedMigrator fRemove = (EnumValueRemovedMigrator) migrator;
            if (forward) {
				node.theType.removeValue(fRemove.value);
			} else {
				node.theType.addValue(fRemove.value);
			}
            break;
        case valueRename:
            if (node == null) {
				throw new IllegalArgumentException("Unrecognized enum type " + migrator.getEntityName() + " for migrator " + migrator);
			}
            EnumValueRenameMigrator fRename = (EnumValueRenameMigrator) migrator;
            if (forward) {
                if (node.theType.removeValue(fRename.beforeName) == null) {
					throw new IllegalArgumentException(
                            "Unrecognized enum value " + migrator.getEntityName() + "." + fRename.beforeName + " for migrator " + migrator);
				}
                node.theType.addValue(fRename.afterName);
            } else {
                if (node.theType.removeValue(fRename.afterName) == null) {
					throw new IllegalArgumentException(
                            "Unrecognized enum value " + migrator.getEntityName() + "." + fRename.afterName + " for migrator " + migrator);
				}
                node.theType.addValue(fRename.beforeName);
            }
            break;
        }
    }

    @Override
    public EntityTypeSet clone() {
        EntityTypeSet ret;
        try {
            ret = (EntityTypeSet) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        ret.theTree = theTree.copy(EntityTypeNode::copy);
        ret.theEntitiesByName = new TreeMap<>();
        ret.theNodesByClassMapping = new LinkedHashMap<>();
        ret.theEnumsByName = new TreeMap<>();
        ret.theEnumsByClassMapping = new LinkedHashMap<>();

        for (EnumTypeNode node : theEnumsByName.values()) {
            EnumTypeNode nodeCopy = new EnumTypeNode(node.theType.clone());
            nodeCopy.theClassMapping = node.theClassMapping;
            ret.addNode(nodeCopy);
        }
        for (EntityTypeNode node : ret.theTree.nodes()) {
            ret.theEntitiesByName.put(node.theType.getName(), node);
            if (node.theClassMapping != null) {
				ret.theNodesByClassMapping.put(node.theClassMapping, node);
			}
        }

        for (EntityTypeNode node : ret.theTree.nodes()) {
			node.theType.replaceTypes(ret);
		}

        return ret;
    }

    /**
     * Imports entity information from an XML file written with {@link #save(Writer)}
     *
     * @param in
     *            The file to read
     * @param typeGetter
     *            The type getter to allow injection of types not accessible here
     * @throws IOException
     *             If an error occurs reading the file
     * @throws JDOMException
     *             If an error occurs parsing the file
     */
    public void read(Reader in, TypeGetter typeGetter) throws IOException, JDOMException {
        clear();
        Element root = new SAXBuilder().build(in).getRootElement();
        if (!root.getName().equals("entity-versions")) {
			throw new IllegalArgumentException("File " + in + " is not an entity version file");
		}

        Element enumsEl = root.getChild("enums");
        if (enumsEl != null) {
            for (Element enumEl : root.getChild("enums").getChildren()) {
                EnumType ev = new EnumType(enumEl.getName());
                addType(ev);
                ev.populateValues(enumEl);
            }
        } else if (typeGetter instanceof EnumInitializingTypeGetter) {
            // Hack for legacy data sets that did not support enums well
            for (EnumInitializingTypeGetter.EnumStruct enumStruct : ((EnumInitializingTypeGetter) typeGetter).getInitialEnums()) {
                EnumType enumType = new EnumType(enumStruct.name);
                for (String value : enumStruct.values) {
					enumType.addValue(value);
				}
                addType(enumType);
            }
        }
        for (Element entityEl : root.getChild("entities").getChildren()) {
            EntityType ev = new EntityType(null, entityEl.getName());
            addType(ev);
        }
        for (Element entityEl : root.getChild("entities").getChildren()) {
			EntityType entityType = getEntityType(entityEl.getName());
			if (entityEl.getAttributeValue("extends") == null) {
				entityType.populateFields(entityEl, this, typeGetter);
			} else {
				EntityTypeNode oldNode = theEntitiesByName.get(entityType.getName());
				if (oldNode.theParent != null) {
					oldNode.theParent.theChildren.remove(oldNode);
				}
				theTree.remove(entityType);
				entityType.populateFields(entityEl, this, typeGetter);
				_reAddType(oldNode);
			}
		}
    }

	private void _reAddType(EntityTypeNode oldNode) {
		theEntitiesByName.remove(oldNode.theType.getName());
		if (oldNode.theClassMapping != null) {
			theNodesByClassMapping.remove(oldNode.theClassMapping);
		}
		addType(oldNode.theType);
		for (EntityTypeNode child : oldNode.theChildren) {
			_reAddType(child);
		}
	}

	/**
	 * Serializes the version support to a file
	 *
	 * @param out
	 *            The file to write the XML data to
	 * @throws IOException
	 *             If an error occurs writing to the file
	 */
    public void save(Writer out) throws IOException {
        Element root = new Element("entity-versions");
        Document doc = new Document(root);
        Element enums = new Element("enums");
        root.addContent(enums);
        for (EnumType enumType : enums()) {
			enums.addContent(enumType.toElement());
		}
        Element entities = new Element("entities");
        root.addContent(entities);
        for (EntityType entity : this) {
			entities.addContent(entity.toElement());
		}
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        outputter.getFormat().setIndent("\t");
        outputter.output(doc, out);
    }

    /**
     * @param entityClasses
     *            The set of classes to create entity types for
     * @param dissecter
     *            The dissecter to understand data types
     * @return The entity type set corresponding to the given java type set
     */
    public static EntityTypeSet createTypesForClasses(Collection<Class<?>> entityClasses, TypeSetDissecter dissecter) {
        System.out.println("Parsing version information from classes");
        EntityTypeSet classVersion = new EntityTypeSet(new Date());
        for (Class<?> clazz : entityClasses) {
            EntityType classType = new EntityType(null, PersistenceUtils.getElementNameFromClass(clazz.getName()));
            classVersion.addType(classType);
            classVersion.map(clazz, classType);
        }
        for (Class<?> clazz : entityClasses) {
            EntityType classType = classVersion.getEntityType(clazz);
            classType.populateFields(clazz, classVersion, (ValueDissecter) dissecter.getDissecter(clazz).dissect(clazz, null));
            if (entityClasses.contains(clazz.getSuperclass())) {
                EntityType superType = classVersion.getEntityType(clazz.getSuperclass());
                if (superType == null) {
					throw new IllegalStateException("No entity mapped to super class " + clazz.getSuperclass().getName() + " of "
                            + clazz.getName());
				}
                classType.internalSetSuperType(superType);
            }
        }
        for (Class<?> clazz : entityClasses) {
			classVersion.getEntityType(clazz).checkFields();
		}

        return classVersion;
    }

    private static class EntityTypeNode implements Tree<EntityType, EntityTypeNode, NavigableSet<EntityTypeNode>> {
        static final Comparator<EntityTypeNode> NODE_COMPARE = new Comparator<EntityTypeNode>() {
            @Override
            public int compare(EntityTypeNode node1, EntityTypeNode node2) {
                return TYPE_COMPARE.compare(node1.theType, node2.theType);
            }
        };

        final EntityTypeNode theParent;
        final EntityType theType;
        Class<?> theClassMapping;
        final NavigableSet<EntityTypeNode> theChildren;

        EntityTypeNode(EntityType type, EntityTypeNode parent) {
            theParent = parent;
            theType = type;
            theChildren = new TreeSet<>(NODE_COMPARE);
        }

        @Override
        public EntityType getValue() {
            return theType;
        }

        @Override
        public NavigableSet<EntityTypeNode> getChildren() {
            return theChildren;
        }

        EntityTypeNode copy(EntityTypeNode parent) {
            EntityType typeCopy = theType.clone();
            if (parent != null) {
				typeCopy.internalSetSuperType(parent.theType);
			}
            EntityTypeNode ret = new EntityTypeNode(typeCopy, parent);
            ret.theClassMapping = theClassMapping;
            for (EntityTypeNode child : theChildren) {
				ret.theChildren.add(child.copy(this));
			}
            return ret;
        }

        @Override
        public String toString() {
            return theType.toString();
        }
    }

    private static class EnumTypeNode {
        final EnumType theType;
        Class<? extends Enum<?>> theClassMapping;

        EnumTypeNode(EnumType type) {
            theType = type;
        }
    }
}
