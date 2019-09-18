package org.migration.generic;

import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedSet;

import org.migration.MigrationSet;
import org.migration.migrators.EntityMigrator;
import org.migration.migrators.EntityTypeModificationMigrator;

/** Some utility methods for easily migrating {@link EntityTypeSet}s and {@link GenericEntitySet}s */
public class MigrationUtil {
    /**
     * @param fromVersionDate
     *            The version to migrate from
     * @param toVersionDate
     *            The version to migrate to
     * @param versionSupport
     *            The version support to get the migrations from
     * @return An iterable iterating through migrations to get from the "from" version to the "to" version
     */
    public static Iterable<MigrationSet> getMigration(final Date fromVersionDate, final Date toVersionDate,
            EntityVersionSupport versionSupport) {
        final boolean forward = toVersionDate.compareTo(fromVersionDate) >= 0;
        final SortedSet<MigrationSet> migSets;
        if (forward) {
            migSets = versionSupport.getMigrationSets();
        } else {
            migSets = versionSupport.getDescendingMigrationSets();
        }
        return new Iterable<MigrationSet>() {
            @Override
            public Iterator<MigrationSet> iterator() {
                return new Iterator<MigrationSet>() {
                    private final Iterator<MigrationSet> theBacking = migSets.iterator();

                    private MigrationSet theNext;
                    private boolean isDone;

                    @Override
                    public boolean hasNext() {
                        while (theNext == null && !isDone && theBacking.hasNext()) {
                            theNext = theBacking.next();
                            if (forward) {
                                if (theNext.getDate().compareTo(fromVersionDate) <= 0) {
                                    theNext = null;
                                } else if (theNext.getDate().compareTo(toVersionDate) > 0) {
                                    isDone = true;
                                    theNext = null;
                                }
                            } else {
                                if (theNext.getDate().compareTo(fromVersionDate) >= 0) {
                                    theNext = null;
                                } else if (theNext.getDate().compareTo(toVersionDate) < 0) {
                                    isDone = true;
                                    theNext = null;
                                }
                            }
                        }
                        return theNext != null;
                    }

                    @Override
                    public MigrationSet next() {
                        if (theNext == null && !hasNext()) {
                            throw new NoSuchElementException();
                        }
                        MigrationSet ret = theNext;
                        theNext = null;
                        return ret;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Transitions a set of entity types to another version
     *
     * @param types
     *            The entity types to transition
     * @param toVersionDate
     *            The version to transition the entity types to
     * @param versionSupport
     *            The version support to get the migrations from
     */
    public static void transition(EntityTypeSet types, Date toVersionDate, EntityVersionSupport versionSupport) {
        boolean forward = toVersionDate.compareTo(types.getVersionDate()) >= 0;
        for (MigrationSet migSet : getMigration(types.getVersionDate(), toVersionDate, versionSupport)) {
            for (EntityMigrator mig : migSet.getMigrators()) {
                if (mig instanceof EntityTypeModificationMigrator) {
                    types.migrate((EntityTypeModificationMigrator) mig, forward);
                }
            }
            types.setVersionDate(migSet.getDate());
        }
    }
}
