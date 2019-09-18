package org.migration.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.migration.util.PersistenceUtils;
import org.qommons.ArrayUtils;

/** A comparison between two versions of an entity type */
public class EntityDifference {
    /** The left side of the comparison */
    public final EntityType left;

    /** The right side of the comparison */
    public final EntityType right;

    private final boolean supersDifferent;

    private Set<EntityField> theLeftAdditions;

    private Set<EntityField> theRightAdditions;

    private Map<EntityField, EntityField> theFieldDifferences;

    /**
     * @param e1
     *            The left side of the comparison
     * @param e2
     *            The right side of the comparison
     */
    public EntityDifference(EntityType e1, EntityType e2) {
        left = e1;
        right = e2;
        if (left == null || right == null) {
            supersDifferent = false;
            return;
        }
        theLeftAdditions = new LinkedHashSet<>();
        theRightAdditions = new LinkedHashSet<>();
        theFieldDifferences = new LinkedHashMap<>();

        supersDifferent = !java.util.Objects.equals(left.getSuperType(), right.getSuperType());
        List<EntityField> leftFields = new ArrayList<>();
        left.forEach(f -> leftFields.add(f));
        List<EntityField> rightFields = new ArrayList<>();
        right.forEach(f -> rightFields.add(f));
        ArrayUtils.adjust(leftFields, rightFields, new ArrayUtils.DifferenceListener<EntityField, EntityField>() {
            @Override
            public boolean identity(EntityField o1, EntityField o2) {
                return o1.getName().equals(o2.getName());
            }

            @Override
            public EntityField added(EntityField o, int mIdx, int retIdx) {
                theRightAdditions.add(o);
                return o;
            }

            @Override
            public EntityField removed(EntityField o, int oIdx, int incMod, int retIdx) {
                theLeftAdditions.add(o);
                return null;
            }

            @Override
            public EntityField set(EntityField o1, int idx1, int incMod, EntityField o2, int idx2, int retIdx) {
                if (o1.getDeclaringType() == e1 && !o1.equals(o2))
                    theFieldDifferences.put(o1, o2);
                return o1;
            }
        });

        theLeftAdditions = Collections.unmodifiableSet(theLeftAdditions);
        theRightAdditions = Collections.unmodifiableSet(theRightAdditions);
        theFieldDifferences = Collections.unmodifiableMap(theFieldDifferences);
    }

    /** @return Whether the two versions are different */
    public boolean isDifferent() {
        if (left == null || right == null || !left.equals(right))
            return true;
        return supersDifferent || !theLeftAdditions.isEmpty() || !theRightAdditions.isEmpty() || !theFieldDifferences.isEmpty();
    }

    /** @return The fields that are only present on the left side of the comparison */
    public Iterable<EntityField> getLeftAdditions() {
        if (theLeftAdditions == null) {
            return Collections.EMPTY_SET;
        }
        return theLeftAdditions;
    }

    /** @return The fields that are only present on the right side of the comparison */
    public Iterable<EntityField> getRightAdditions() {
        if (theRightAdditions == null) {
            return Collections.EMPTY_SET;
        }
        return theRightAdditions;
    }

    /** @return The fields present in both versions that have differences between the two versions */
    public Iterable<Map.Entry<EntityField, EntityField>> getFieldDifferences() {
        if (theFieldDifferences == null) {
            return Collections.EMPTY_SET;
        }
        return theFieldDifferences.entrySet();
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
            return "Entity " + right.getName() + " found in " + rightVersion + " but not in " + leftVersion;
        } else if (right == null) {
            return "Entity " + left.getName() + " found in " + leftVersion + " but not in " + rightVersion;
        }
        StringBuilder ret = new StringBuilder();
        if (supersDifferent) {
            ret.append("\nSuper types different: ").append(left.getSuperType()).append(" in ").append(leftVersion).append(" vs. ")
                    .append(right.getSuperType()).append(" in ").append(rightVersion);
        }
        for (EntityField field : theLeftAdditions) {
            ret.append("\nField ").append(field).append(" found in ").append(leftVersion).append(" but not in ").append(rightVersion);
        }
        for (EntityField field : theRightAdditions) {
            ret.append("\nField ").append(field).append(" found in ").append(rightVersion).append(" but not in ").append(leftVersion);
        }
        for (Map.Entry<EntityField, EntityField> field : theFieldDifferences.entrySet()) {
            ret.append("\nField ").append(field.getKey()).append(" changed from ").append(leftVersion).append(" to ").append(rightVersion)
                    .append(": ");
            boolean firstMod = true;
            if (!field.getKey().getType().equals(field.getValue().getType())) {
                if (!firstMod)
                    ret.append(", ");
                else
                    firstMod = false;
                ret.append("Type changed from ").append(PersistenceUtils.toString(field.getKey().getType())).append(" to ")
                        .append(PersistenceUtils.toString(field.getValue().getType()));
            }
        }
        if (ret.length() > 0) {
            ret.delete(0, 1); // Delete the first \n
        }
        return ret.toString();
    }

    @Override
    public String toString() {
        if (left == null) {
            return "Additional entity " + right.getName();
        } else if (right == null) {
            return "Missing entity " + left.getName();
        }
        StringBuilder ret = new StringBuilder();
        if (supersDifferent) {
            ret.append("\nSuper types different: " + left.getSuperType() + " vs. " + right.getSuperType());
        }
        for (EntityField field : theLeftAdditions) {
            ret.append("\nMissing field " + field);
        }
        for (EntityField field : theRightAdditions) {
            ret.append("\nAdditional field " + field);
        }
        for (Map.Entry<EntityField, EntityField> field : theFieldDifferences.entrySet()) {
            ret.append("\nModified field " + field.getKey() + ": ");
            if (!field.getKey().getType().equals(field.getValue().getType())) {
                ret.append("Type changed from ").append(PersistenceUtils.toString(field.getKey().getType())).append(" to ")
                        .append(PersistenceUtils.toString(field.getValue().getType()));
            }
        }
        if (ret.length() > 0) {
            ret.delete(0, 1);
        }
        return ret.toString();
    }
}
