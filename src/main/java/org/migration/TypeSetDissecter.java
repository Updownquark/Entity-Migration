package org.migration;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.migration.generic.EntityType;
import org.migration.generic.GenericEntity;
import org.migration.util.BogusNavigableMap;
import org.migration.util.BogusNavigableSet;
import org.migration.util.PersistenceUtils;
import org.migration.util.ReflectionUtils;
import org.qommons.QommonsUtils;
import org.qommons.SubClassMap;
import org.qommons.collect.SimpleMapEntry;

/** Contains knowledge of how to pull apart and put together objects of any of a set of types */
public class TypeSetDissecter {
	/** The date format to use for exporting dates */
	public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("ddMMMyyyy HH:mm:ss.SSS");

	/** The set of simple types that this class recognizes. Custom dissection of these classes is not allowed. */
	private static final SubClassMap<Object, SimpleFormat> SIMPLE_TYPES;

	private static final SubClassMap<Object, DissecterGenerator> RECOGNIZED_TYPES;

	static {
		SubClassMap<Object, SimpleFormat> simple = new SubClassMap<>();
		class DefaultFormat implements SimpleFormat {
			private final Function<String, ?> theParser;

			DefaultFormat(Function<String, ?> parser) {
				theParser = parser;
			}

			@Override
			public String format(Object value) {
				return String.valueOf(value);
			}

			@Override
			public Object parse(Class<?> type, String formatted) {
				return theParser.apply(formatted);
			}
		}
		simple.put(Boolean.TYPE, new DefaultFormat(Boolean::valueOf));
		simple.put(Boolean.class, new DefaultFormat(Boolean::valueOf));
		simple.put(Character.TYPE, new DefaultFormat(str -> str.charAt(0)));
		simple.put(Character.class, new DefaultFormat(str -> str.charAt(0)));
		Function<String, Byte> byteParser = txt -> {
			String lower = txt.toLowerCase();
			if ("max".equals(lower))
				return Byte.MAX_VALUE;
			else if ("min".equals(lower))
				return Byte.MIN_VALUE;
			else
				return Byte.valueOf(txt);
		};
		simple.put(Byte.TYPE, new DefaultFormat(byteParser));
		simple.put(Byte.class, new DefaultFormat(byteParser));
		Function<String, Short> shortParser = txt -> {
			String lower = txt.toLowerCase();
			if ("max".equals(lower))
				return Short.MAX_VALUE;
			else if ("min".equals(lower))
				return Short.MIN_VALUE;
			else
				return Short.valueOf(txt);
		};
		simple.put(Short.TYPE, new DefaultFormat(shortParser));
		simple.put(Short.class, new DefaultFormat(shortParser));
		Function<String, Integer> intParser = txt -> {
			String lower = txt.toLowerCase();
			if ("max".equals(lower))
				return Integer.MAX_VALUE;
			else if ("min".equals(lower))
				return Integer.MIN_VALUE;
			else
				return Integer.valueOf(txt);
		};
		simple.put(Integer.TYPE, new DefaultFormat(intParser));
		simple.put(Integer.class, new DefaultFormat(intParser));
		Function<String, Long> longParser = txt -> {
			String lower = txt.toLowerCase();
			if ("max".equals(lower)) {
				return Long.MAX_VALUE;
			} else if ("min".equals(lower)) {
				return Long.MIN_VALUE;
			} else {
				return Long.valueOf(txt);
			}
		};
		simple.put(Long.TYPE, new DefaultFormat(longParser));
		simple.put(Long.class, new DefaultFormat(longParser));
		Function<String, Float> floatParser = txt -> {
			String lower = txt.toLowerCase();
			if ("max".equals(lower))
				return Float.MAX_VALUE;
			else if ("-max".equals(lower))
				return -Float.MAX_VALUE;
			else if ("min".equals(lower))
				return Float.MIN_VALUE;
			else if ("-min".equals(lower))
				return -Float.MIN_VALUE;
			else if ("min-normal".equals(lower))
				return Float.MIN_NORMAL;
			else if ("-min-normal".equals(lower))
				return -Float.MIN_NORMAL;
			else if ("inf".equals(lower))
				return Float.POSITIVE_INFINITY;
			else if ("-inf".equals(lower))
				return Float.NEGATIVE_INFINITY;
			else
				return Float.valueOf(txt);
		};
		simple.put(Float.TYPE, new DefaultFormat(floatParser));
		simple.put(Float.class, new DefaultFormat(floatParser));
		Function<String, Double> doubleParser = txt -> {
			String lower = txt.toLowerCase();
			if ("max".equals(lower))
				return Double.MAX_VALUE;
			else if ("-max".equals(lower))
				return -Double.MAX_VALUE;
			else if ("min".equals(lower))
				return Double.MIN_VALUE;
			else if ("-min".equals(lower))
				return -Double.MIN_VALUE;
			else if ("min-normal".equals(lower))
				return Double.MIN_NORMAL;
			else if ("-min-normal".equals(lower))
				return -Double.MIN_NORMAL;
			else if ("inf".equals(lower))
				return Double.POSITIVE_INFINITY;
			else if ("-inf".equals(lower))
				return Double.NEGATIVE_INFINITY;
			else
				return Double.valueOf(txt);
		};
		simple.put(Double.TYPE, new DefaultFormat(doubleParser));
		simple.put(Double.class, new DefaultFormat(doubleParser));
		simple.put(String.class, new DefaultFormat(str -> str));
		simple.put(Date.class, new SimpleFormat() {
			@Override
			public String format(Object value) {
				return DATE_FORMAT.format((Date) value);
			}

			@Override
			public Object parse(Class<?> type, String formatted) {
				try {
					return DATE_FORMAT.parse(formatted);
				} catch (ParseException e) {
					throw new IllegalStateException("Malformatted date: " + formatted, e);
				}
			}
		});
		simple.put(Timestamp.class, new SimpleFormat() {
			@Override
			public String format(Object value) {
				return DATE_FORMAT.format((Date) value);
			}

			@Override
			public Object parse(Class<?> type, String formatted) {
				try {
					return new Timestamp(DATE_FORMAT.parse(formatted).getTime());
				} catch (ParseException e) {
					throw new IllegalStateException("Malformatted date: " + formatted, e);
				}
			}
		});
		simple.put(Instant.class, new SimpleFormat() {
			@Override
			public String format(Object value) {
				return DATE_FORMAT.format(new Date(((Instant) value).toEpochMilli()));
			}

			@Override
			public Object parse(Class<?> type, String formatted) {
				try {
					return Instant.ofEpochMilli(DATE_FORMAT.parse(formatted).getTime());
				} catch (ParseException e) {
					throw new IllegalStateException("Malformatted date: " + formatted, e);
				}
			}
		});
		simple.put(Duration.class, new SimpleFormat() {
			@Override
			public String format(Object value) {
				return QommonsUtils.printTimeLength(((Duration) value).toMillis());
			}

			@Override
			public Object parse(Class<?> type, String formatted) {
				return Duration.ofMillis(QommonsUtils.parseEnglishTime(formatted));
			}
		});
		simple.put(byte[].class, new ByteArrayFormat());

		simple.seal();
		SIMPLE_TYPES = simple;

		SubClassMap<Object, DissecterGenerator> recognized = new SubClassMap<>();
		recognized.put(Map.Entry.class, (type, subType) -> new MapEntryDissecter(type));
		recognized.put(Object[].class,
			(Type type, String subType) -> new ArrayDissecter(type, o -> Arrays.asList((Object[]) o).iterator()));
		recognized.put(boolean[].class, (type, subType) -> new ArrayDissecter(type, b -> new BooleanIterator((boolean[]) b)));
		recognized.put(char[].class, (type, subType) -> new ArrayDissecter(type, c -> new CharacterIterator((char[]) c)));
		recognized.put(short[].class, (type, subType) -> new ArrayDissecter(type, s -> new ShortIterator((short[]) s)));
		recognized.put(int[].class, (type, subType) -> new ArrayDissecter(type, i -> new IntIterator((int[]) i)));
		recognized.put(long[].class, (type, subType) -> new ArrayDissecter(type, l -> new LongIterator((long[]) l)));
		recognized.put(float[].class, (type, subType) -> new ArrayDissecter(type, f -> new FloatIterator((float[]) f)));
		recognized.put(double[].class, (type, subType) -> new ArrayDissecter(type, d -> new DoubleIterator((double[]) d)));
		recognized.put(Collection.class, (type, subType) -> new JavaCollectionDissecter(type, coll -> (Collection<?>) coll));
		recognized.put(Map.class, (type, subType) -> new JavaCollectionDissecter(type, map -> ((Map<?, ?>) map).entrySet()));

		recognized.seal();
		RECOGNIZED_TYPES = recognized;
	}

	/**
	 * @param type The type to check
	 * @return Whether the given type is recognized by all {@link TypeSetDissecter} instances
	 */
	public static boolean isRecognizedByDefault(Class<?> type) {
		if (Enum.class.isAssignableFrom(type))
			return true;
		else if (Map.Entry.class.isAssignableFrom(type))
			return true;
		else if (SIMPLE_TYPES.get(type) != null)
			return true;
		else if (RECOGNIZED_TYPES.get(type) != null)
			return true;
		return false;
	}

	private SubClassMap<Object, SimpleFormat> theCustomFormatters;
	private SubClassMap<Object, DissecterGenerator> theCustomDissecters;
	private Map<Class<?>, ReflectiveDissecterGenerator<?>> theEntityDissecters;

	/**
	 * @param reflectiveTypes The types to support with reflective dissecters
	 * @param typeGetter The type getter for reflective dissection
	 */
	public TypeSetDissecter(Collection<Class<?>> reflectiveTypes, TypeGetter typeGetter) {
		theCustomFormatters = new SubClassMap<>();
		theCustomDissecters = new SubClassMap<>();
		theEntityDissecters = new LinkedHashMap<>();
		for (Class<?> type : reflectiveTypes)
			theEntityDissecters.put(type, new ReflectiveDissecterGenerator<>(type, typeGetter));
	}

	/**
	 * @param type The type to check
	 * @return Whether the type is a simple type that is formattable by this instance
	 */
	public boolean isSimple(Class<?> type) {
		if (SIMPLE_TYPES.get(type) != null)
			return true;
		else if (theCustomFormatters.get(type) != null)
			return true;
		return false;
	}

	/**
	 * @param type The type to check
	 * @return Whether this dissecter recognizes the given type
	 */
	public boolean isRecognized(Class<?> type) {
		if (isRecognizedByDefault(type))
			return true;
		else if (getFormat(type) != null)
			return true;
		else if (getDissecter(type) != null)
			return true;
		return false;
	}

	/**
	 * @param type The type to set the custom format for
	 * @param format The format to use for objects of the given type
	 * @return This dissecter, for chaining
	 */
	public TypeSetDissecter withSimple(Class<?> type, SimpleFormat format) {
		// if (isRecognizedByDefault(type))
		// throw new IllegalArgumentException("Cannot override the dissection of recognized type " + type.getSimpleName());
		theCustomFormatters.put(type, format);
		theEntityDissecters.remove(type); // In case we're overriding a type we've reflectively dissected before
		return this;
	}

	/**
	 * @param type The type to set the custom dissecter for
	 * @param dissecter The dissecter to use for objects of the given type
	 * @return This dissecter, for chaining
	 */
	public TypeSetDissecter withDissecter(Class<?> type, DissecterGenerator dissecter) {
		// if (isRecognizedByDefault(type))
		// throw new IllegalArgumentException("Cannot override the dissection of recognized type " + type.getSimpleName());
		theCustomDissecters.put(type, dissecter);
		theEntityDissecters.remove(type); // In case we're overriding a type we've reflectively dissected before
		return this;
	}

	/**
	 * @param type The type to dissect
	 * @return The dissecter to use for the given type, or null if the type is not a recognized type
	 */
	public DissecterGenerator getDissecter(Class<?> type) {
		if (isSimple(type))
			return null;

		DissecterGenerator ret;
		ret = theCustomDissecters.get(type);
		if (ret == null)
			ret = RECOGNIZED_TYPES.get(type);
		if (ret == null)
			ret = theEntityDissecters.get(type);
		return ret;
	}

	/**
	 * @param type The type to format
	 * @return The formatter to use for the given type, or null if the type is not a recognized simple type
	 */
	public SimpleFormat getFormat(Class<?> type) {
		SimpleFormat ret;
		ret = theCustomFormatters.get(type);
		if (ret != null)
			return ret;
		ret = SIMPLE_TYPES.get(type);
		return ret;
	}

	private static class ByteArrayFormat implements SimpleFormat {
		@Override
		public String format(Object value) {
			final String hex = "0123456789ABCDEF";
			StringBuilder serialized = new StringBuilder();
			int len = Array.getLength(value);
			for (int i = 0; i < len; i++) {
				int val = (((Byte) Array.get(value, i)).byteValue() + 256) % 256;
				serialized.append(hex.charAt(val / 16)).append(hex.charAt(val % 16));
			}
			return serialized.toString();
		}

		@Override
		public Object parse(Class<?> type, String formatted) {
			final String hex = "0123456789ABCDEF";
			String serialized = formatted.toLowerCase();
			int len = serialized.length() / 2;
			Object ret = Array.newInstance(type.getComponentType(), len);
			for (int i = 0; i < len; i++) {
				int val = (hex.indexOf(serialized.charAt(i * 2)) << 4) | hex.indexOf(serialized.charAt(i * 2 + 1));
				Array.set(ret, i, (byte) val);
			}
			return ret;
		}
	}

	private static class MapEntryDissecter implements ValueDissecter {
		private final Type theType;

		MapEntryDissecter(Type type) {
			theType = type;
		}

		@Override
		public TypedField[] getFields() {
			Class<?> raw = PersistenceUtils.getRawType(theType);
			if (!(Map.Entry.class.isAssignableFrom(raw)))
				throw new IllegalArgumentException("Unrecognized type: " + theType);
			Type keyType;
			Type valType;
			if (theType == raw) {
				keyType = Object.class;
				valType = Object.class;
			} else {
				ParameterizedType pt = (ParameterizedType) theType;
				keyType = pt.getActualTypeArguments()[0];
				valType = pt.getActualTypeArguments()[1];
			}
			return new TypedField[] { TypedField.builder(raw, "key", keyType).build(), TypedField.builder(raw, "value", valType).build() };
		}

		@Override
		public Object getFieldValue(Object entity, String field) {
			if (field.equals("key"))
				return ((Map.Entry<?, ?>) entity).getKey();
			else if (field.equals("value"))
				return ((Map.Entry<?, ?>) entity).getValue();
			else
				throw new IllegalArgumentException("Unrecognized field " + field);
		}

		@Override
		public Object createWith(Map<String, Object> fieldValues) {
			return new SimpleMapEntry<>(fieldValues.get("key"), fieldValues.get("value"));
		}

		@Override
		public void setFieldValue(Object entity, String field, Object fieldValue) {
			throw new UnsupportedOperationException();
		}
	}

	private static class ArrayDissecter implements CollectionDissecter {
		private final Class<?> theType;
		private final Function<Object, Iterator<?>> iteratorFn;

		ArrayDissecter(Type type, Function<Object, Iterator<?>> iter) {
			theType = ((Class<?>) type).getComponentType();
			iteratorFn = iter;
		}

		@Override
		public Type getComponentType() {
			return theType;
		}

		@Override
		public Iterable<?> getElements(Object collection) {
			return new Iterable<Object>() {
				@Override
				public Iterator<Object> iterator() {
					return (Iterator<Object>) iteratorFn.apply(collection);
				}
			};
		}

		@Override
		public String getElementName() {
			return "element";
		}

		@Override
		public Object createFrom(Collection<?> elements, TypedField field) {
			Object ret = Array.newInstance(theType, elements.size());
			System.arraycopy(elements.toArray(), 0, ret, 0, elements.size());
			return ret;
		}
	}

	private static class BooleanIterator implements Iterator<Boolean> {
		private final boolean[] b;
		private int index;

		BooleanIterator(boolean[] b) {
			this.b = b;
		}

		@Override
		public boolean hasNext() {
			return index < b.length;
		}

		@Override
		public Boolean next() {
			Boolean ret = Boolean.valueOf(b[index]);
			index++;
			return ret;
		}
	}

	private static class CharacterIterator implements Iterator<Character> {
		private final char[] c;
		private int index;

		CharacterIterator(char[] b) {
			this.c = b;
		}

		@Override
		public boolean hasNext() {
			return index < c.length;
		}

		@Override
		public Character next() {
			Character ret = Character.valueOf(c[index]);
			index++;
			return ret;
		}
	}

	private static class ShortIterator implements Iterator<Short> {
		private final short[] s;
		private int index;

		ShortIterator(short[] b) {
			this.s = b;
		}

		@Override
		public boolean hasNext() {
			return index < s.length;
		}

		@Override
		public Short next() {
			Short ret = Short.valueOf(s[index]);
			index++;
			return ret;
		}
	}

	private static class IntIterator implements Iterator<Integer> {
		private final int[] i;
		private int index;

		IntIterator(int[] b) {
			this.i = b;
		}

		@Override
		public boolean hasNext() {
			return index < i.length;
		}

		@Override
		public Integer next() {
			Integer ret = Integer.valueOf(i[index]);
			index++;
			return ret;
		}
	}

	private static class LongIterator implements Iterator<Long> {
		private final long[] l;
		private int index;

		LongIterator(long[] b) {
			this.l = b;
		}

		@Override
		public boolean hasNext() {
			return index < l.length;
		}

		@Override
		public Long next() {
			Long ret = Long.valueOf(l[index]);
			index++;
			return ret;
		}
	}

	private static class FloatIterator implements Iterator<Float> {
		private final float[] f;
		private int index;

		FloatIterator(float[] b) {
			this.f = b;
		}

		@Override
		public boolean hasNext() {
			return index < f.length;
		}

		@Override
		public Float next() {
			Float ret = Float.valueOf(f[index]);
			index++;
			return ret;
		}
	}

	private static class DoubleIterator implements Iterator<Double> {
		private final double[] d;
		private int index;

		DoubleIterator(double[] b) {
			this.d = b;
		}

		@Override
		public boolean hasNext() {
			return index < d.length;
		}

		@Override
		public Double next() {
			Double ret = Double.valueOf(d[index]);
			index++;
			return ret;
		}
	}

	private static class JavaCollectionDissecter implements CollectionDissecter {
		private final ParameterizedType theType;
		private final Function<Object, Iterable<?>> iterableFn;

		public JavaCollectionDissecter(Type type, Function<Object, Iterable<?>> iterableFn) {
			theType = (ParameterizedType) type;
			this.iterableFn = iterableFn;
		}

		@Override
		public Type getComponentType() {
			Class<?> raw = PersistenceUtils.getRawType(theType);
			if (Collection.class.isAssignableFrom(raw))
				return theType.getActualTypeArguments()[0];
			else if (Map.class.isAssignableFrom(raw))
				return PersistenceUtils.parameterize(Map.Entry.class, theType.getActualTypeArguments()[0],
					theType.getActualTypeArguments()[1]);
			else
				throw new IllegalStateException("Unrecognized entity field type: " + theType);
		}

		@Override
		public Iterable<?> getElements(Object collection) {
			return iterableFn.apply(collection);
		}

		@Override
		public String getElementName() {
			return "element";
		}

		@Override
		public Object createFrom(Collection<?> elements, TypedField field) {
			if (elements == null) {
				System.err.println("No value set for collection " + field);
				return null;
			}
			Class<?> raw = (Class<?>) theType.getRawType();
			if (Collection.class.isAssignableFrom(raw)) {
				Type elType = theType.getActualTypeArguments()[0];
				Collection<Object> copy;
				if (SortedSet.class.isAssignableFrom(raw)) {
					if (field != null) {
						if (elType instanceof EntityType)
							copy = new TreeSet<>((Comparator<Object>) (Comparator<?>) getGenericEntityComparator(field.ordering));
						else
							copy = new TreeSet<>(getRealEntityComparator(field, PersistenceUtils.getRawType(elType)));
					} else if (!(elType instanceof EntityType) && Comparable.class.isAssignableFrom(PersistenceUtils.getRawType(elType)))
						copy = new TreeSet<>();
					else
						copy = new BogusNavigableSet<>();
				} else if (Set.class.isAssignableFrom(raw))
					copy = new LinkedHashSet<>();
				else if (List.class.isAssignableFrom(raw))
					copy = new ArrayList<>();
				else
					copy = new ArrayList<>();
				for (Object value : elements)
					copy.add(value);
				return copy;
			} else if (Map.class.isAssignableFrom(raw)) {
				Type keyType = theType.getActualTypeArguments()[0];
				Collection<Map.Entry<Object, Object>> entries = (Collection<Entry<Object, Object>>) elements;
				Map<Object, Object> copy;
				if (SortedMap.class.isAssignableFrom(raw)) {
					if (field != null) {
						if (keyType instanceof EntityType)
							copy = new TreeMap<>((Comparator<Object>) (Comparator<?>) getGenericEntityComparator(field.ordering));
						else
							copy = new TreeMap<>(getRealEntityComparator(field, PersistenceUtils.getRawType(keyType)));
					} else if (!(keyType instanceof EntityType)
						&& Comparable.class.isAssignableFrom(PersistenceUtils.getRawType(keyType))) {
						copy = new TreeMap<>();
					} else
						copy = new BogusNavigableMap<>(); // Can't assume the key is comparable
				} else
					copy = new LinkedHashMap<>();
				for (Map.Entry<Object, Object> entry : entries) {
					Object key = entry.getKey();
					Object value = entry.getValue();
					copy.put(key, value);
				}
				return copy;
			} else {
				throw new IllegalStateException("Unrecognized entity field type: " + theType);
			}
		}

		private static Comparator<GenericEntity> getGenericEntityComparator(String[] ordering) {
			if (ordering.length == 0) {
				return new Comparator<GenericEntity>() {
					@Override
					public int compare(GenericEntity o1, GenericEntity o2) {
						int diff = compareBy(o1, o2, o1.getType().getIdField().getName());
						if (diff < 0)
							return -1;
						else if (diff > 0)
							return 1;
						else
							return 0;
					}
				};
			} else {
				return new Comparator<GenericEntity>() {
					@Override
					public int compare(GenericEntity o1, GenericEntity o2) {
						for (String order : ordering) {
							int diff = compareBy(o1, o2, order);
							if (diff < 0)
								return -1;
							else if (diff > 0)
								return 1;
						}
						return 0;
					}
				};
			}
		}

		private static int compareBy(GenericEntity o1, GenericEntity o2, String order) {
			Comparable<Object> value1 = (Comparable<Object>) o1.get(order);
			Comparable<Object> value2 = (Comparable<Object>) o2.get(order);
			return value1.compareTo(value2);
		}

		private static Comparator<Object> getRealEntityComparator(TypedField field, Class<?> elType) {
			String[] sortColumns = field.ordering;
			if (sortColumns != null && sortColumns.length > 0) {
				Method[] sortGetters = new Method[sortColumns.length];
				for (int i = 0; i < sortColumns.length; i++) {
					Method sortGetter = ReflectionUtils.getGetter(elType, sortColumns[i]);
					if (sortGetter == null)
						throw new IllegalStateException("No such field " + sortColumns + " defined for sorting on " + field);
					Class<?> sortFieldType = sortGetter.getReturnType();
					if (sortFieldType == Boolean.TYPE
						|| (!sortFieldType.isPrimitive() && !(Comparable.class.isAssignableFrom(sortFieldType))))
						throw new IllegalStateException(
							"Sorting field " + sortColumns + " on " + field + " is not comparable (" + sortFieldType.getSimpleName() + ")");
				}
				Class<?> elementType = elType;
				return new Comparator<Object>() {
					@Override
					public int compare(Object o1, Object o2) {
						o1 = elementType.cast(o1);
						o2 = elementType.cast(o2);

						for (Method sortGetter : sortGetters) {
							Object c1, c2;
							try {
								c1 = sortGetter.invoke(o1);
								c2 = sortGetter.invoke(o2);
							} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
								throw new IllegalStateException(
									"Could not invoke sort field getter " + sortGetter + " on element " + o1 + " or " + o2);
							}
							int ret = ((Comparable<Object>) c1).compareTo(c2);
							if (ret != 0) {
								return ret;
							}
						}
						return 0;
					}
				};
			}

			if (field.sorting != null)
				return (Comparator<Object>) field.sorting;
			else if (elType != null && Comparable.class.isAssignableFrom(elType))
				return null; // Values are comparable; use natural ordering
			throw new IllegalStateException("No sorting defined for field " + field);
		}
	}
}
