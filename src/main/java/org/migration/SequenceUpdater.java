package org.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.persistence.SequenceGenerator;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.jdbc.Work;
import org.migration.util.ReflectionUtils;

/** Updates ID sequences to the max ID */
public class SequenceUpdater {
    /**
     * @param session
     *            The session to use to update sequences
     * @param entityClasses
     *            The entity classes to update ID sequences for
     */
    public static void updateIdSequences(Session session, Iterable<Class<?>> entityClasses) {
        for (Class<?> entityClass : entityClasses) {
			updateIdSequence(session, entityClass);
		}
    }

    /**
     * @param session
     *            The session to use to update sequences
     * @param entityClass
     *            The entity class to update the ID sequence for
     */
    public static void updateIdSequence(Session session, final Class<?> entityClass) {
        final String sequenceName = getSequenceName(entityClass);
        if (sequenceName == null) {
			return;
		}
        final Number maxId = (Number) session.createQuery(
                "SELECT MAX(" + ReflectionUtils.getFieldNameFromGetter(ReflectionUtils.getIdGetter(entityClass).getName()) + ") FROM "
                        + entityClass.getSimpleName()).uniqueResult();
        if (maxId == null || maxId.longValue() == 0) {
			return;
		}
        System.out.println("Advancing " + entityClass.getSimpleName() + " ID sequence " + sequenceName + " to " + maxId);
        final String incrementSql = ((SessionFactoryImplementor) session.getSessionFactory()).getDialect().getSequenceNextValString(
                sequenceName);
        session.doWork(new Work() {
            @Override
            public void execute(Connection connection) throws SQLException {
                try (PreparedStatement stmt = connection.prepareStatement(incrementSql)) {
                    ResultSet rs = stmt.executeQuery();
                    try {
                        rs.next();
                        while (rs.getLong(1) < maxId.longValue()) {
                            rs.close();
                            rs = stmt.executeQuery();
                            rs.next();
                        }
                    } finally {
                        rs.close();
                    }
                } catch (SQLException e) {
                    System.err.println("Unable to update sequence " + sequenceName + " for entity " + entityClass.getName() + ": " + e);
                }
            }
        });
    }

    /**
     * @param entityClass
     *            The entity class to get the ID sequence name for
     * @return The ID sequence name for the given entity class, or null if the entity's ID does not use a sequence
     */
    public static String getSequenceName(Class<?> entityClass) {
        SequenceGenerator seqGen = ReflectionUtils.getIdGetter(entityClass).getAnnotation(SequenceGenerator.class);
        if (seqGen == null) {
			seqGen = entityClass.getAnnotation(SequenceGenerator.class);
		}
        if (seqGen == null) {
			return null;
		}
        String name = seqGen.sequenceName();
        if (name == null || name.length() == 0) {
			return null;
		}
        return name;
    }
}
