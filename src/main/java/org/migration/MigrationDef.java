package org.migration;

import java.util.Date;
import java.util.Objects;

/** Represents a set of configured migrations or a reference to one */
public class MigrationDef implements Comparable<MigrationDef> {
    private final String theAuthor;
    private final Date theDate;

    /**
     * @param author
     *            The name of the author who wrote this migration
     * @param date
     *            When this migration was created
     */
    public MigrationDef(String author, Date date) {
        if (author == null || date == null)
            throw new NullPointerException();
        theAuthor = author;
        theDate = date;
    }

    /** @return The name of the author who wrote this migration */
    public String getAuthor() {
        return theAuthor;
    }

    /** @return When this migration was created */
    public Date getDate() {
        return theDate;
    }

    @Override
    public int compareTo(MigrationDef o) {
        return theDate.compareTo(o.theDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theAuthor, theDate);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MigrationDef && theAuthor.equals(((MigrationDef) obj).getAuthor())
                && theDate.equals(((MigrationDef) obj).getDate());
    }

    @Override
    public String toString() {
        return theAuthor + "@" + theDate;
    }
}
