package org.migration.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.qommons.ArrayUtils;

/** A comparison between two versions of an enum type */
public class EnumDifference {
    /** The left side of the comparison */
    public final EnumType left;

    /** The right side of the comparison */
    public final EnumType right;

    private Set<EnumValue> theLeftAdditions;

    private Set<EnumValue> theRightAdditions;

    /**
     * @param e1
     *            The left side of the comparison
     * @param e2
     *            The right side of the comparison
     */
    public EnumDifference(EnumType e1, EnumType e2) {
        left = e1;
        right = e2;
        if (left == null || right == null) {
            return;
        }
        theLeftAdditions = new LinkedHashSet<>();
        theRightAdditions = new LinkedHashSet<>();

        List<EnumValue> leftFields = new ArrayList<>();
        left.forEach(f -> leftFields.add(f));
        List<EnumValue> rightFields = new ArrayList<>();
        right.forEach(f -> rightFields.add(f));
        ArrayUtils.adjust(leftFields, rightFields, new ArrayUtils.DifferenceListener<EnumValue, EnumValue>() {
            @Override
            public boolean identity(EnumValue o1, EnumValue o2) {
                return o1.getName().equals(o2.getName());
            }

            @Override
            public EnumValue added(EnumValue o, int mIdx, int retIdx) {
                theRightAdditions.add(o);
                return o;
            }

            @Override
            public EnumValue removed(EnumValue o, int oIdx, int incMod, int retIdx) {
                theLeftAdditions.add(o);
                return null;
            }

            @Override
            public EnumValue set(EnumValue o1, int idx1, int incMod, EnumValue o2, int idx2, int retIdx) {
                return o1;
            }
        });

        theLeftAdditions = Collections.unmodifiableSet(theLeftAdditions);
        theRightAdditions = Collections.unmodifiableSet(theRightAdditions);
    }

    /** @return Whether the two versions are different */
    public boolean isDifferent() {
        if (left == null || right == null || !left.equals(right))
            return true;
        return !theLeftAdditions.isEmpty() || !theRightAdditions.isEmpty();
    }

    /** @return The values that are only present on the left side of the comparison */
    public Iterable<EnumValue> getLeftAdditions() {
        if (theLeftAdditions == null) {
            return Collections.EMPTY_SET;
        }
        return theLeftAdditions;
    }

    /** @return The values that are only present on the right side of the comparison */
    public Iterable<EnumValue> getRightAdditions() {
        if (theRightAdditions == null) {
            return Collections.EMPTY_SET;
        }
        return theRightAdditions;
    }

    /**
     * A better diff string than {@link #toString()}, this method includes the given version descriptions to give the reader of the message
     * a better idea of exactly what changed.
     * 
     * @param leftVersion
     *            A short description if the left entity type's version
     * @param rightVersion
     *            A short description if the left entity type's version
     * @return A description of this entity difference, or the empty string if this difference is not, in fact, {@link #isDifferent()
     *         different}
     */
    public String toString(String leftVersion, String rightVersion) {
        if (left == null) {
            return "Enum " + right.getName() + " found in " + rightVersion + " but not in " + leftVersion;
        } else if (right == null) {
            return "Enum " + left.getName() + " found in " + leftVersion + " but not in " + rightVersion;
        }
        StringBuilder ret = new StringBuilder();
        for (EnumValue value : theLeftAdditions) {
			ret.append("\nConstant ").append(left.getName()).append('.').append(value).append(" found in ").append(leftVersion)
					.append(" but not in ").append(rightVersion);
        }
        for (EnumValue value : theRightAdditions) {
			ret.append("\nConstant ").append(right.getName()).append('.').append(value).append(" found in ").append(rightVersion)
					.append(" but not in ").append(leftVersion);
        }
        if (ret.length() > 0) {
            ret.delete(0, 1); // Delete the first \n
        }
        return ret.toString();
    }

    @Override
    public String toString() {
        if (left == null) {
            return "Additional enum " + right.getName();
        } else if (right == null) {
            return "Missing enum " + left.getName();
        }
        StringBuilder ret = new StringBuilder();
        for (EnumValue value : theLeftAdditions) {
            ret.append("\nMissing value " + value);
        }
        for (EnumValue value : theRightAdditions) {
            ret.append("\nAdditional value " + value);
        }
        if (ret.length() > 0) {
            ret.delete(0, 1);
        }
        return ret.toString();
    }
}
