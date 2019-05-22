package org.migration.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.migration.CollectionDissecter;
import org.migration.Dissecter;
import org.migration.DissecterGenerator;
import org.migration.SimpleFormat;
import org.migration.TypeSetDissecter;
import org.migration.TypedField;
import org.migration.ValueDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EnumType;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.qommons.io.CsvParser;
import org.qommons.io.HierarchicalResourceReader;
import org.qommons.io.TextParseException;
import org.qommons.json.JsonStreamWriter;
import org.qommons.json.SAJParser;

/** Reads and writes entities from/to CSV files, using JSON for complex values */
public class CsvEntitySetPersistence extends AbstractTextEntitySetPersistence {
	private static class CsvEntityWriter extends AbstractTextEntityWriter {
		private final BufferedWriter theWriter;
		private final CsvValueWriter theValueWriter;
		private final JsonStreamWriter theJsonWriter;
		private int isSimpleField;

		CsvEntityWriter(EntityType type, TypeSetDissecter dissecter, BufferedWriter streamWriter) throws IOException {
			super(type, dissecter);
			theWriter = streamWriter;
			theWriter.write(getType().getIdField().getName());
			for (EntityField field : getType()) {
				if (field.isId() || !isSerializable(field)) {
					continue;
				}
				theWriter.write(',');
				theWriter.write(field.getName());
			}
			theWriter.newLine();
			theValueWriter = new CsvValueWriter(theWriter);
			theJsonWriter = new JsonStreamWriter(theValueWriter);
			theJsonWriter.setFormal(true);
			theJsonWriter.setFormatIndent(null);
		}

		@Override
		protected void beginEntity(GenericEntity entity) throws IOException {
		}

		@Override
		protected void endEntity(GenericEntity entity) throws IOException {
			theWriter.newLine();
		}

		@Override
		protected boolean writeFieldValue(String fieldName, Type fieldType, boolean maySort, EntityField field, Object fieldValue)
				throws IOException {
			if (isSimpleField == 0) {
				theWriter.write(',');
			}
			return super.writeFieldValue(fieldName, fieldType, maySort, field, fieldValue);
		}

		@Override
		protected void writeNullValue(String fieldName) throws IOException {
			if (isSimpleField == 0) {
				theWriter.write("null");
			} else {
				theJsonWriter.writeNull();
			}
		}

		@Override
		protected void writeReference(String fieldName, GenericEntity value, String serializedId) throws IOException {
			if (isSimpleField == 0) {
				theValueWriter.write(serializedId);
			} else if (isNumber(value.getCurrentType().getIdField().getType())) {
				theJsonWriter.writeNumber((Number) value.getIdentity());
			} else {
				theJsonWriter.writeString(serializedId);
			}
		}

		@Override
		protected void writeSimpleField(String fieldName, Type type, String subType, EntityField field, String serializedValue)
				throws IOException {
			if (isSimpleField == 0) {
				boolean escape = serializedValue.indexOf(',') >= 0//
						|| serializedValue.indexOf('\n') >= 0//
						|| serializedValue.indexOf('\"') >= 0;
				if (escape) {
					theWriter.write('"');
				}
				theValueWriter.write(serializedValue);
				if (escape) {
					theWriter.write('"');
				}
			} else if (isNumber(type) || isBoolean(type)) {
				// The serialized value should be recognizable as JSON, so no need to quote it
				theJsonWriter.writeCustomValue();
				theValueWriter.write(serializedValue);
			} else {
				theJsonWriter.writeString(serializedValue);
			}
		}

		@Override
		protected boolean writeComplexField(String fieldName, String subType, TypedField[] subFields, EntityField field,
				Object[] subFieldValues) throws IOException {
			boolean outerValue = isSimpleField == 0;
			isSimpleField++;
			boolean success = true;
			try {
				if (outerValue) {
					theWriter.write('"');
				}
				theJsonWriter.startObject();
				if (subType != null) {
					theJsonWriter.startProperty("type");
					theJsonWriter.writeString(subType);
				}
				for (int f = 0; f < subFields.length; f++) {
					theJsonWriter.startProperty(subFields[f].name);
					Object subFieldValue = subFieldValues[f];
					if (subFieldValue == null) {
						theJsonWriter.writeNull();
					} else if (subFieldValue instanceof Boolean) {
						theJsonWriter.writeBoolean((Boolean) subFieldValue);
					} else if (subFieldValue instanceof Number) {
						theJsonWriter.writeNumber((Number) subFieldValue);
					} else if (subFieldValue instanceof String) {
						theJsonWriter.writeString((String) subFieldValue);
					} else {
						success &= writeFieldValue(subFields[f].name, subFields[f].type, !subFields[f].isOrdered(), field, subFieldValue);
					}
				}
				theJsonWriter.endObject();
			} finally {
				isSimpleField--;
				if (outerValue) {
					theWriter.write('"');
				}
			}
			return success;
		}

		@Override
		protected boolean writeCollectionField(String fieldName, String subType, Type collectionType, CollectionDissecter cd,
				EntityField field, Iterable<?> elements) throws IOException {
			boolean outerValue = isSimpleField == 0;
			isSimpleField++;
			boolean success = true;
			try {
				if (outerValue) {
					theWriter.write('"');
				}
				theJsonWriter.startArray();
				if (subType != null) {
					theJsonWriter.writeString(subType);
				}
				for (Object element : elements) {
					success &= writeFieldValue(fieldName, cd.getComponentType(), false, field, element);
				}
				theJsonWriter.endArray();
			} finally {
				isSimpleField--;
				if (outerValue) {
					theWriter.write('"');
				}
			}
			return success;
		}

		@Override
		public void close() throws IOException {
			theWriter.close();
		}

		private static boolean isNumber(Type type) {
			if (!(type instanceof Class)) {
				return false;
			}
			Class<?> c = (Class<?>) type;
			if (c.isPrimitive()) {
				return type != boolean.class && type != char.class; // All other primitive types are numbers
			} else {
				return Number.class.isAssignableFrom(c);
			}
		}

		private static boolean isBoolean(Type type) {
			return type == boolean.class || type == Boolean.class;
		}
	}

	private static class CsvEntityReader extends AbstractTextEntityReader<String[]> {
		private final int theColumnCount;

		CsvEntityReader(EntityType type, TypeSetDissecter dissecter, HierarchicalResourceReader reader, String fileName) {
			super(type, dissecter, reader, fileName);
			int cc = 0;
			for (EntityField field : type) {
				if (isSerializable(field)) {
					cc++;
				}
			}
			theColumnCount = cc;
		}

		@Override
		protected void parseEntityStructures(BufferedReader reader, GenericEntitySet entities, Consumer<String[]> onEntity)
				throws IOException {
			CsvParser parser = new CsvParser(reader, ',');
			String[] columns = new String[theColumnCount];
			// Parse and check the header
			try {
				if (!parser.parseNextLine(columns)) {
					throw new IOException("No columns on first line of CSV file for entity " + getType());
				}
				if (!columns[0].equals(getType().getIdField().getName())) {
					throw new IOException("Header: Expected " + getType().getIdField().getName() + ", but encountered " + columns[0]);
				}
				int c = 1;
				for (EntityField field : getType()) {
					if (field.isId() || !isSerializable(field)) {
						continue;
					}
					if (!columns[c].equals(field.getName())) {
						throw new IOException("Header: Expected " + field.getName() + ", but encountered " + columns[c]);
					}
					c++;
				}
				while (parser.parseNextLine(columns)) {
					onEntity.accept(columns);
				}
			} catch (TextParseException e) {
				throw new IOException("Could not parse CSV file for entity " + getType(), e);
			}
		}

		@Override
		protected String getIdentity(String[] entityStructure) {
			return entityStructure[0];
		}

		@Override
		protected boolean deserializeFieldsFor(GenericEntity item, String[] element, GenericEntitySet entities) {
			int c = 1;
			Object[] fieldValue = new Object[1];
			boolean success = true;
			for (EntityField field : getType()) {
				if (field.isId() || !isSerializable(field)) {
					continue;
				}
				boolean fieldSuccess = getValue(field.getType(), field, entities, element[c], fieldValue);
				if (fieldSuccess) {
					item.set(field.getName(), fieldValue[0]);
				} else {
					success =false;
				}
				c++;
			}
			return success;
		}

		private boolean getValue(Type type, EntityField field, GenericEntitySet entitySet, Object serializedValue, Object[] fieldValue) {
			if (serializedValue == null || "null".equals(serializedValue)) {
				fieldValue[0] = null;
				return true;
			} else if (type instanceof EnumType) {
				fieldValue[0] = ((EnumType) type).getValue((String) serializedValue);
				if (fieldValue[0] == null) {
					throw new IllegalArgumentException("No such enum value " + type + "." + serializedValue);
				}
				return true;
			} else if (type instanceof EntityType) {
				EntityField idField = ((EntityType) type).getIdField();
				SimpleFormat idFormat = getDissecter().getFormat((Class<?>) idField.getType());
				Object idValue;
				if (serializedValue instanceof String) {
					try {
						idValue = idFormat.parse((Class<?>) idField.getType(), (String) serializedValue);
					} catch (RuntimeException e) {
						System.err.println("Could not parse " + idField + " from " + serializedValue + " for " + field);
						e.printStackTrace();
						return false;
					}
				} else {
					idValue = cast(serializedValue, (Class<?>) idField.getType());
				}
				fieldValue[0] = entitySet.get(((EntityType) type).getName(), idValue);
				if (fieldValue[0] == null) {
					System.err.println("No such " + type + " with " + idField.getName() + " " + idValue + " for " + field);
					return false;
				}
				return true;
			}
			Class<?> raw = PersistenceUtils.getRawType(type);
			SimpleFormat format = getDissecter().getFormat(raw);
			if (format != null) {
				if (serializedValue instanceof String) {
					try {
						fieldValue[0] = format.parse(raw, (String) serializedValue);
					} catch (RuntimeException e) {
						System.err.println(
								"Could not parse " + PersistenceUtils.toString(type) + " from " + serializedValue + " for " + field);
						e.printStackTrace();
						return false;
					}
				} else {
					fieldValue[0] = cast(serializedValue, raw);
				}
				return true;
			}

			DissecterGenerator gen = getDissecter().getDissecter(raw);
			if (gen == null) {
				System.err.println("Unrecognized type: " + PersistenceUtils.toString(type));
				return false;
			}
			Object jsonThing;
			if (serializedValue instanceof JSONObject || serializedValue instanceof JSONArray) {
				jsonThing = serializedValue;
			} else if (serializedValue instanceof String) {
				try {
					jsonThing = SAJParser.parse((String) serializedValue);
				} catch (SAJParser.ParseException e) { // Error? So dumb.
					System.err.println("Could not parse " + PersistenceUtils.toString(type) + " from " + serializedValue + " for " + field);
					e.printStackTrace();
					return false;
				}
			} else {
				System.err.println("Unrecognized type: " + PersistenceUtils.toString(type));
				return false;
			}
			Dissecter dissecter;
			if (jsonThing instanceof JSONObject) {
				JSONObject json = (JSONObject) jsonThing;
				String subType = gen.needsSubType(type) ? (String) json.get("type") : null;
				dissecter = gen.dissect(type, subType);
				ValueDissecter vd = (ValueDissecter) dissecter;
				Map<String, Object> fieldValues = new LinkedHashMap<>();
				for (TypedField f : vd.getFields()) {
					Object att = json.get(f.name);
					if (isSimpleField(f.name, f.type, getDissecter())) {
						if (att == null) {
							System.err.println("No data configured for " + f + " for " + field);
							return false;
						} else if (att instanceof String) {
							fieldValues.put(f.name, getDissecter().getFormat((Class<?>) f.type).parse((Class<?>) f.type, (String) att));
						} else if (f.type instanceof Class) {
							fieldValues.put(f.name, cast(att, (Class<?>) f.type));
						} else {
							System.err.println(
									"Could not parse " + PersistenceUtils.toString(type) + " from " + serializedValue + " for " + field);
							return false;
						}
					} else {
						if (getValue(f.type, field, entitySet, att, fieldValue)) {
							fieldValues.put(f.name, fieldValue[0]);
						} else {
							return false;
						}
					}
				}
				try {
					fieldValue[0] = vd.createWith(fieldValues);
				} catch (RuntimeException e) {
					System.err.println("Could not assemble " + PersistenceUtils.toString(type) + " from " + fieldValues + " for " + field);
					e.printStackTrace();
					return false;
				}
				return true;
			} else if (jsonThing instanceof JSONArray) {
				JSONArray json = (JSONArray) jsonThing;
				String subType = gen.needsSubType(type) ? (String) json.get(0) : null;
				CollectionDissecter cd = (CollectionDissecter) gen.dissect(type, subType);
				Type componentType = cd.getComponentType();
				ArrayList<Object> elements = new ArrayList<>();
				for (Object child : json) {
					if (getValue(componentType, field, entitySet, child, fieldValue)) {
						elements.add(fieldValue[0]);
					} else {
						return false;
					}
				}
				try {
					fieldValue[0] = cd.createFrom(elements, null);
				} catch (RuntimeException e) {
					System.err.println("Could not assemble " + PersistenceUtils.toString(type) + " for " + field);
					e.printStackTrace();
					return false;
				}
				return true;
			}
			System.err.println("Unrecognized type: " + PersistenceUtils.toString(type));
			return false;
		}

		private static Object cast(Object value, Class<?> type) {
			if (type == double.class || type == Double.class) {
				return ((Number) value).doubleValue();
			} else if (type == float.class || type == Float.class) {
				return ((Number) value).floatValue();
			} else if (type == long.class || type == Long.class) {
				return ((Number) value).longValue();
			} else if (type == int.class || type == Integer.class) {
				return ((Number) value).intValue();
			} else if (type == short.class || type == Short.class) {
				return ((Number) value).shortValue();
			} else if (type == byte.class || type == Byte.class) {
				return ((Number) value).shortValue();
			} else if (type == boolean.class || type == Boolean.class) {
				return Boolean.class.cast(value);
			} else if (type == String.class) {
				return String.class.cast(value);
			} else {
				throw new IllegalStateException("Unrecognized simple type: " + type);
			}
		}
	}

	private static class CsvValueWriter extends Writer {
		private final Writer theWrapped;

		CsvValueWriter(Writer wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public void write(int c) throws IOException {
			if (c == '"') {
				theWrapped.write('"');
			}
			theWrapped.write(c);
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			if (cbuf.length < len) {
				len = cbuf.length;
			}
			int written = 0;
			for (int i = 0; i < len; i++) {
				if (cbuf[i] == '"') {
					if (written < i) {
						theWrapped.write(cbuf, off + written, i - written);
					}
					written = i;
					theWrapped.write('"');
				}
			}
			if (written < len) {
				theWrapped.write(cbuf, off + written, len - written);
			}
		}

		@Override
		public void flush() throws IOException {
			theWrapped.flush();
		}

		@Override
		public void close() throws IOException {
			// Do nothing here. This writer just writes values; there may be more values or lines still to be written.
		}
	}

	/**
	 * Creates the CSV persistence
	 * 
	 * @param dissecter
	 *            The type dissecter to understand the data set's types
	 */
	public CsvEntitySetPersistence(TypeSetDissecter dissecter) {
		super(dissecter);
	}

	@Override
	protected String getFileName(EntityType type) {
		return PersistenceUtils.xmlToJava(type.getName(), true) + ".csv";
	}

	@Override
	protected EntityWriter createEntityWriter(EntityType type, Writer streamWriter) throws IOException {
		return new CsvEntityWriter(type, getDissecter(), new BufferedWriter(streamWriter));
	}

	@Override
	protected AbstractTextEntityReader<?> createEntityReader(EntityType type, HierarchicalResourceReader reader, String fileName) {
		return new CsvEntityReader(type, getDissecter(), reader, fileName);
	}

	static boolean isSimpleField(String name, Type type, TypeSetDissecter dissecter) {
		if (type instanceof EnumType) {
			return true;
		}
		if (!(type instanceof Class) || dissecter.getFormat((Class<?>) type) == null) {
			return false;
		}
		if (name.equals("type")) {
			return false;
		}
		return true;
	}
}
