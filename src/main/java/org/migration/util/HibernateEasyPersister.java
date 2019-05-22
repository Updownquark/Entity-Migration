package org.migration.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.migration.util.EntityDependencyGraph.EntityReferenceSet;
import org.migration.util.EntityDependencyGraph.PersistedField;

/** Provides an easy way to persist a set of entities and their dependencies */
public class HibernateEasyPersister {
    private EntityDependencyGraph theDependencyGraph;

    /**
     * Creates the easy persister
     * 
     * @param classes
     *            The entity types to potentially persist entities for
     */
    public HibernateEasyPersister(Iterable<Class<?>> classes) {
        theDependencyGraph = new EntityDependencyGraph(classes);
    }

    /**
     * @param session
     *            The hibernate session to persist entities into
     * @param entities
     *            The entities to persist
     * @param preserveIDs
     *            Whether to attempt to use the ID present in the entity or generate a new ID
     */
    public void persist(Session session, boolean preserveIDs, Object... entities) {
        persist(session, preserveIDs, Arrays.asList(entities));
    }

    /**
     * @param session
     *            The hibernate session to persist entities into
     * @param preserveIDs
     *            Whether to attempt to use the ID present in the entity or generate a new ID
     * @param entities
     *            The entities to persist
     */
    public void persist(Session session, boolean preserveIDs, Iterable<Object> entities) {
        Set<Object> persisted = new IdentityHashSet<>();
        Set<Object> persisting = new IdentityHashSet<>();
        boolean newTransaction = session.getTransaction() == null || !session.getTransaction().isActive();
        if (newTransaction)
            session.beginTransaction();
        try {
            Set<Object> persist = new IdentityHashSet<>();
            for (Object entity : entities)
                persist.add(entity);
            Set<Object> optional = new IdentityHashSet<>();
            while (!persist.isEmpty()) {
                for (Object entity : persist) {
                    persistEntity(session, entity, persisted, persisting, preserveIDs, optional);
                }
                persist.clear();
                persist.addAll(optional);
                optional.clear();
            }
            if (newTransaction)
                session.getTransaction().commit();
        } catch (RuntimeException e) {
            if (newTransaction)
                session.getTransaction().rollback(); // If we didn't start this transaction, then assume someone else is responsible for it.
            throw e;
        }
    }

    private void persistIfEntity(Session session, Object entity, Set<Object> persisted, Set<Object> persisting, boolean preserveIDs,
            Set<Object> optional) {
        if (theDependencyGraph.getClasses().contains(entity)) {
            persistEntity(session, entity, persisted, persisting, preserveIDs, optional);
        }
    }

    private void persistEntity(Session session, Object entity, Set<Object> persisted, Set<Object> persisting, boolean preserveIDs,
            Set<Object> optional) {
        if (entity == null) {
            return;
        }
        if (entity instanceof Collection) {
            for (Object contained : (Collection<?>) entity) {
                persistEntity(session, contained, persisted, persisting, preserveIDs, optional);
            }
            return;
        } else if (entity instanceof Map) {
            for (Object key : ((Map<?, ?>) entity).keySet()) {
                persistIfEntity(session, key, persisted, persisting, preserveIDs, optional);
            }
            for (Object value : ((Map<?, ?>) entity).values()) {
                persistIfEntity(session, value, persisted, persisting, preserveIDs, optional);
            }
            return;
        }
        if (persisted.contains(entity)) {
            return;
        }
        if (persisting.contains(entity)) {
            throw new IllegalStateException("Could not resolve dependencies for " + entity.getClass() + ": " + persisting);
        }
        EntityReferenceSet strategy = theDependencyGraph.getReferenceSet(entity.getClass());
        persistByStrategy(session, entity, strategy, persisted, persisting, preserveIDs, optional);
    }

    /**
     * Persists an entity using the pre-generated strategy
     *
     * @param session
     *            The hibernate session to persist the entity into
     * @param entity
     *            The entity to persist
     * @param strategy
     *            The strategy to use to persist the entity
     * @param persisted
     *            The set of entities already persisted in this transaction
     * @param persisting
     *            The set of entities whose persistence is currently being attempted
     * @param preserveIDs
     *            Whether to attempt to use the ID present in the entity or generate a new ID
     * @param optional
     *            The set to which to add entities that are referenced by the given entity and need to be persisted, but are not required
     *            before the entity itself can be persisted
     */
    protected void persistByStrategy(Session session, Object entity, EntityReferenceSet strategy, Set<Object> persisted,
            Set<Object> persisting, boolean preserveIDs, Set<Object> optional) {
        Map<String, Object> optionalFields;
        persisting.add(entity);
        boolean exists = session.contains(entity);
        try {
            for (PersistedField pre : strategy.getPreRequisites()) {
                persistEntity(session, pre.get(entity), persisted, persisting, preserveIDs, optional);
            }
            if (!strategy.getOptional().isEmpty()) {
                optionalFields = new HashMap<>();
                for (PersistedField opt : strategy.getOptional()) {
                    Object optValue = opt.get(entity);
                    addToOptional(optValue, optional);
                    optionalFields.put(opt.getName(), optValue);
                    opt.set(entity, null);
                }
            } else {
                optionalFields = null;
            }
            if (!persisted.contains(entity)) {
                if (exists) {
                    session.update(entity);
                } else {
                    if (preserveIDs) {
                        session.replicate(entity, org.hibernate.ReplicationMode.EXCEPTION);
                    } else {
                        if (entity.getClass().getSimpleName().equals("EntityPlatformType")) {
                            try {
                                System.out.println("Persisting platform type " + entity.getClass().getMethod("getName").invoke(entity));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        session.save(entity);
                    }
                }
            }
        } finally {
            persisting.remove(entity);
        }
        if (optionalFields != null) {
            for (PersistedField opt : strategy.getOptional()) {
                opt.set(entity, optionalFields.get(opt.getName()));
            }
        }
        session.update(entity);
        persisted.add(entity);
    }

    private void addToOptional(Object optValue, Set<Object> optional) {
        if (optValue instanceof Collection) {
            for (Object element : ((Collection<?>) optValue))
                addToOptional(element, optional);
        } else if (optValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) optValue).entrySet()) {
                if (theDependencyGraph.getClasses().contains(entry.getKey().getClass()))
                    addToOptional(entry.getKey(), optional);
                if (entry.getValue() != null && theDependencyGraph.getClasses().contains(entry.getValue().getClass()))
                    addToOptional(entry.getValue(), optional);
            }
        } else {
            optional.add(optValue);
        }
    }
}
