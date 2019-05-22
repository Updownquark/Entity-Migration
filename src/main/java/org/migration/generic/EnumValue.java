package org.migration.generic;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Represents an enum value */
public class EnumValue implements Comparable<EnumValue> {
    private final EnumType theEnumType;
    private final String theName;

    /**
     * @param currentType
     *            The enum type that this value is for
     * @param name
     *            The name of the value
     */
    protected EnumValue(EnumType currentType, String name) {
        theEnumType = currentType;
        theName = name;
    }

    /** @return The num type that this value is of */
    public EnumType getEnumType() {
        return theEnumType;
    }

    /** @return This value's name */
    public String getName() {
        return theName;
    }

	public <T> SwitchStatement<T> sWitch() {
		return new SwitchStatement<>(this);
	}

    @Override
    public int compareTo(EnumValue o) {
        return theName.compareTo(o.theName);
    }

    @Override
    public int hashCode() {
        return theEnumType.getName().hashCode() * 13 + theName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EnumValue && ((EnumValue) obj).theEnumType.equals(theEnumType) && ((EnumValue) obj).theName.equals(theName);
    }

    @Override
    public String toString() {
        return theName;
    }

	public static class SwitchStatement<T> {
		private final EnumValue theValue;
		private final Map<EnumValue, Supplier<T>> theCases;
		private Supplier<T> theDefault;

		public SwitchStatement(EnumValue value) {
			theValue = value;
			theCases = new LinkedHashMap<>();
		}

		public CaseStatement inCase(EnumValue... cases) {
			return new CaseStatement(cases);
		}

		public CaseStatement inCase(String... cases) {
			EnumValue[] values = new EnumValue[cases.length];
			for (int i = 0; i < cases.length; i++) {
				values[i] = theValue.getEnumType().getValue(cases[i]);
				if (values[i] == null)
					throw new IllegalArgumentException("No such constant " + theValue.getEnumType().getName() + "." + cases[i]);
			}
			return new CaseStatement(values);
		}

		public SwitchStatement<T> withDefault(Supplier<T> defaultAction) {
			theDefault = defaultAction;
			return this;
		}

		public T eval() {
			if (theDefault == null) {
				// If there's no default, then all values have to be accounted for by name
				Set<EnumValue> unaccounted = new LinkedHashSet<>(theValue.getEnumType().getValues());
				unaccounted.removeAll(theCases.keySet());
				if (!unaccounted.isEmpty())
					throw new IllegalStateException(
							unaccounted.size() + " case" + (unaccounted.size() == 1 ? "" : "s") + " unaccounted for:\n\t" + unaccounted);
			}
			return theCases.getOrDefault(theValue, theDefault).get();
		}

		public class CaseStatement {
			private final Set<EnumValue> theValues;
			private boolean isSpent;

			private CaseStatement(EnumValue[] cases) {
				theValues = new LinkedHashSet<>();
				for (EnumValue value : cases) {
					if (theCases.containsKey(value) || !theValues.add(value))
						throw new IllegalArgumentException("Case " + value + " already declared");
				}
			}

			public SwitchStatement<T> act(Supplier<T> action) {
				if (isSpent)
					throw new IllegalStateException("These cases are already acted upon");
				isSpent = true;
				for (EnumValue value : theValues) {
					theCases.put(value, action);
				}
				return SwitchStatement.this;
			}
		}
	}
}
