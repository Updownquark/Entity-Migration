package org.migration.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.persistence.Entity;

import org.hibernate.Query;
import org.hibernate.Session;
import org.migration.EntityMigration;
import org.migration.EntitySet;

/** Creates an {@link EntitySet} from entities in a hibernate session */
public class HibernateExtractionUtil {
    /**
     * @param session
     *            The session to query the database with
     * @return An extractor to use with {@link EntityMigration#extract(Function, java.util.function.Consumer, java.util.function.Consumer)}
     */
    public static Function<Class<?>, List<?>> extract(Session session) {
        return extract(session, type -> {
            String queryString = "FROM " + PersistenceUtils.getEntityName(type);
            queryString += " ORDER BY ";
            {
                Method idGetter = ReflectionUtils.getIdGetter(type);
                if (idGetter == null) {
                    throw new IllegalStateException("Could not find identifier for entity type " + type.getName());
                }
                queryString += PersistenceUtils.javaToXml(idGetter.getName().substring(3)).replaceAll("-", "_");
            }
            return queryString;
        });
    }

    /**
     * @param session
     *            The session to query the database with
     * @param entityQueryer
     *            A function to construct query strings for each entity type
     * @return An extractor to use with {@link EntityMigration#extract(Function, java.util.function.Consumer, java.util.function.Consumer)}
     */
    public static Function<Class<?>, List<?>> extract(Session session, Function<Class<?>, String> entityQueryer) {
        return extract(session, (type, session2) -> session2.createQuery(entityQueryer.apply(type)));
    }

    /**
     * @param session
     *            The session to query the database with
     * @param queryCreator
     *            A function to constuct hibernate queries for each entity type
     * @return An extractor to use with {@link EntityMigration#extract(Function, java.util.function.Consumer, java.util.function.Consumer)}
     */
    public static Function<Class<?>, List<?>> extract(Session session, BiFunction<Class<?>, Session, Query> queryCreator) {
        return clazz -> clazz.getAnnotation(Entity.class) != null ? queryCreator.apply(clazz, session).list() : Collections.emptyList();
    }
}
