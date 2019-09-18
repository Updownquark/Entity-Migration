package org.migration.generic;

public class FieldTag<T> {
	public final String name;
	public final Class<T> type;
	public final T defaultValue;

	public FieldTag(String name, Class<T> type, T defaultValue) {
		this.name = name;
		this.type = type;
		this.defaultValue = defaultValue;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FieldTag && name.equals(((FieldTag<?>) obj).name);
	}

	@Override
	public String toString() {
		return name + " (" + type.getSimpleName() + ")";
	}
}
