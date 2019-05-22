package org.migration;

/** Knows how to format values to strings and parse them back */
public interface SimpleFormat {
    /**
     * @param value
     *            The value to format
     * @return The formatted value
     */
    String format(Object value);

    /**
     * @param type
     *            The type of the value to parse
     * @param formatted
     *            The formatted value (from {@link #format(Object)})
     * @return The parsed value
     */
    Object parse(Class<?> type, String formatted);
}
