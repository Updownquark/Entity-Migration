package org.migration.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.migration.CollectionDissecter;
import org.migration.Dissecter;
import org.migration.DissecterGenerator;
import org.migration.EntitySetPersistence;
import org.migration.SimpleFormat;
import org.migration.TypeSetDissecter;
import org.migration.TypedField;
import org.migration.ValueDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityType;
import org.migration.generic.EnumValue;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.qommons.io.HierarchicalResourceReader;
import org.qommons.io.HierarchicalResourceWriter;

public abstract class AbstractTextEntitySetPersistence implements EntitySetPersistence {
	protected static abstract class AbstractTextEntityWriter implements EntityWriter {
		private final EntityType theType;
		private final TypeSetDissecter theDissecter;

		protected AbstractTextEntityWriter(EntityType type, TypeSetDissecter dissecter) {
			theType = type;
			theDissecter = dissecter;
		}

		public EntityType getType() {
			return theType;
		}

		public TypeSetDissecter getDissecter() {
			return theDissecter;
		}

		@Override
		public boolean writeEntity(GenericEntity entity) throws IOException {
			beginEntity(entity);
			boolean success = true;
			{ // Write the ID attribute
				EntityField idField = theType.getIdField();
				Object id = entity.get(idField.getName());
				SimpleFormat format = theDissecter.getFormat(id.getClass());
				writeSimpleField(idField.getName(), long.class, null, idField, format.format(id));
			}
			for (EntityField field : theType) {
				if (!field.isId() && isSerializable(field)) {
					success &= writeFieldValue(field.getName(), field.getType(),
							field.getSorting() == null || field.getSorting().length == 0,
							field, entity.get(field.getName()));
				}
			}
			endEntity(entity);
			return success;
		}

		protected boolean writeFieldValue(String fieldName, Type fieldType, boolean maySort, EntityField field, Object fieldValue)
				throws IOException {
			if (fieldValue == null) {
				writeNullValue(fieldName);
				return true;
			}
			if (fieldValue instanceof GenericEntity) {
				String idField = ((GenericEntity) fieldValue).getCurrentType().getIdField().getName();
				Object id = ((GenericEntity) fieldValue).get(idField);
				SimpleFormat format = theDissecter.getFormat(id.getClass());
				writeReference(fieldName, (GenericEntity) fieldValue, format.format(id));
				return true;
			} else if (fieldValue instanceof EnumValue) {
				writeSimpleField(fieldName, fieldType, null, field, ((EnumValue) fieldValue).getName());
				return true;
			}
			SimpleFormat format = theDissecter.getFormat(fieldValue.getClass());
			if (format != null) {
				writeSimpleField(fieldName, fieldType, null, field, format.format(fieldValue));
				return true;
			}
			DissecterGenerator gen = theDissecter.getDissecter(fieldValue.getClass());
			if (gen == null) {
				throw new IllegalArgumentException("Unrecognized type: " + PersistenceUtils.toString(field.getType()));
			}
			String subType = gen.getSubType(fieldValue.getClass());
			// No need to record the sub-type if it's the same as the type
			boolean writeType = subType != null && !subType.equals(gen.getSubType(PersistenceUtils.getRawType(fieldType)));
			Dissecter dissecter = gen.dissect(fieldType, subType);
			if (dissecter instanceof ValueDissecter) {
				ValueDissecter vd = (ValueDissecter) dissecter;
				TypedField[] subFields = vd.getFields();
				Object[] subFieldValues = new Object[subFields.length];
				for (int i = 0; i < subFields.length; i++) {
					subFieldValues[i] = vd.getFieldValue(fieldValue, subFields[i].name);
				}
				return writeComplexField(fieldName, writeType ? subType : null, subFields, field, subFieldValues);
			} else if (dissecter instanceof CollectionDissecter) {
				CollectionDissecter cd = (CollectionDissecter) dissecter;
				Type componentType = cd.getComponentType();
				Iterable<?> elements = cd.getElements(fieldValue);
				if (componentType instanceof EntityType && maySort && ((EntityType) componentType).getIdField().getType() instanceof Class
						&& isComparable((Class<?>) ((EntityType) componentType).getIdField().getType())) {
					// If there's not an explicit order to the collection, re-order by ID for consistency in the XML
					List<GenericEntity> sorted = new ArrayList<>();
					Comparator<GenericEntity> compare = new Comparator<GenericEntity>() {
						@Override
						public int compare(GenericEntity o1, GenericEntity o2) {
							return ((Comparable<Object>) o1.getIdentity()).compareTo(o2.getIdentity());
						}
					};
					for (Object el : elements) {
						int index = Collections.binarySearch(sorted, (GenericEntity) el, compare);
						if (index >= 0) {
							do {
								index++;
							} while (index < sorted.size() && compare.compare((GenericEntity) el, sorted.get(index)) == 0);
						} else {
							index = -(index + 1);
						}
						sorted.add(index, (GenericEntity) el);
					}
					elements = sorted;
				}
				return writeCollectionField(fieldName, writeType ? subType : null, fieldType, cd, field, elements);
			} else {
				throw new IllegalArgumentException("Unrecognized type: " + PersistenceUtils.toString(fieldType));
			}
		}

		protected abstract void beginEntity(GenericEntity entity) throws IOException;

		protected abstract void endEntity(GenericEntity entity) throws IOException;

		protected abstract void writeNullValue(String fieldName) throws IOException;

		protected abstract void writeReference(String fieldName, GenericEntity value, String serializedId) throws IOException;

		protected abstract void writeSimpleField(String fieldName, Type type, String subType, EntityField field, String serializedValue)
				throws IOException;

		protected abstract boolean writeComplexField(String fieldName, String subType, TypedField[] subFields, EntityField field,
				Object[] subFieldValues) throws IOException;

		protected abstract boolean writeCollectionField(String fieldName, String subType, Type collectionType, CollectionDissecter cd,
				EntityField field, Iterable<?> elements) throws IOException;
	}

	protected static abstract class AbstractTextEntityReader<E> implements EntityReader {
		private final EntityType theType;
		private final TypeSetDissecter theDissecter;
		private final HierarchicalResourceReader theReader;
		private final String theFileName;
		private InputStream theInput;

		protected AbstractTextEntityReader(EntityType type, TypeSetDissecter dissecter, HierarchicalResourceReader reader,
				String fileName) {
			theType = type;
			theDissecter = dissecter;
			theReader = reader;
			theFileName = fileName;
		}

		protected EntityType getType() {
			return theType;
		}

		protected TypeSetDissecter getDissecter() {
			return theDissecter;
		}

		protected boolean fileExists() throws IOException {
			if (theInput != null) {
				return true;
			}
			try {
				theInput = theReader.readResource(theFileName);
				return theInput != null;
			} catch (IOException e) {
				throw new IOException("Could not read " + theFileName, e);
			}
		}

		@Override
		public boolean readEntityIdentities(GenericEntitySet entities, Consumer<GenericEntity> onEntity) throws IOException {
			InputStream in = theInput;
			theInput = null;
			if (in == null) {
				try {
					in = theReader.readResource(theFileName);
				} catch (IOException e) {
					throw new IOException("Could not read " + theFileName, e);
				}
				if (in == null) {
					return true;
				}
			}
			boolean[] success = new boolean[] { true };
			try {
				System.out.println("\tCreating entities of " + theType.getName() + " from " + theFileName);
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
					parseEntityStructures(reader, entities, element -> {
						GenericEntity item;
						try {
							item = entities.addEntity(theType.getName());
						} catch (Exception e) {
							System.err.println("Could not instantiate entity of type " + theType.getName());
							e.printStackTrace();
							success[0] = false;
							return;
						}

						boolean itemSuccess = true;
						// Set the ID
						EntityField idField = theType.getIdField();
						String idString = getIdentity(element);
						Object idValue;
						try {
							SimpleFormat idFormat = theDissecter.getFormat((Class<?>) idField.getType());
							idValue = idFormat.parse((Class<?>) idField.getType(), idString);

							GenericEntity duplicate = entities.get(theType.getName(), idValue);
							if (duplicate != null && duplicate != item) {
								System.err.println(
										"Duplicate " + theType.getName() + " instances found with " + idField.getName() + " " + idValue);
								itemSuccess = false;
							}

							if (itemSuccess && idValue == null) {
								System.err.println("No ID set for " + theType);
								itemSuccess = false;
							}
							if (itemSuccess) {
								item.set(idField.getName(), idValue);
							}
						} catch (RuntimeException e) {
							System.err.println("Could not parse " + idField + " from " + idString);
							e.printStackTrace();
							itemSuccess = false;
						}

						if (!itemSuccess) {
							entities.removeEntity(item);
						}
						if (itemSuccess && onEntity != null) {
							onEntity.accept(item);
						}
						success[0] &= itemSuccess;
					});
				} catch (Exception e) {
					System.err.println("Could not finish parsing data for " + theType.getName());
					e.printStackTrace();
					success[0] = false;
				}
				return success[0];
			} finally {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public boolean populateEntityFields(GenericEntitySet entities, Consumer<GenericEntity> onCompleteEntity) throws IOException {
			InputStream in;
			try {
				in = theReader.readResource(theFileName);
			} catch (IOException e) {
				System.err.println("Could not read " + theFileName);
				e.printStackTrace();
				return false;
			}
			if (in == null) {
				return true;
			}
			boolean[] success = new boolean[] { true };
			try {
				System.out.println("\tParsing data for entity " + theType.getName() + " from " + theFileName);
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
					parseEntityStructures(reader, entities, element -> {
						EntityField idField = theType.getIdField();
						Object idValue;
						try {
							SimpleFormat idFormat = theDissecter.getFormat((Class<?>) idField.getType());
							String idString = getIdentity(element);
							idValue = idFormat.parse((Class<?>) idField.getType(), idString);
						} catch (RuntimeException e) {
							return; // Presumably, we caught this the last time
						}

						GenericEntity item = entities.get(theType.getName(), idValue);
						boolean itemSuccess = deserializeFieldsFor(item, element, entities);
						if (itemSuccess && onCompleteEntity != null) {
							onCompleteEntity.accept(item);
						}
						success[0] &= itemSuccess;
					});
				} catch (Exception e) {
					System.err.println("Could not finish parsing data for " + theType.getName());
					e.printStackTrace();
					success[0] = false;
				}
			} finally {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return success[0];
		}

		protected abstract void parseEntityStructures(BufferedReader reader, GenericEntitySet entities, Consumer<E> onEntity)
				throws IOException;

		protected abstract String getIdentity(E entityStructure);

		protected abstract boolean deserializeFieldsFor(GenericEntity item, E element, GenericEntitySet entities);
	}

	private final TypeSetDissecter theDissecter;
	
	public AbstractTextEntitySetPersistence(TypeSetDissecter dissecter){
		theDissecter=dissecter;
	}
	
	public TypeSetDissecter getDissecter() {
		return theDissecter;
	}

	protected abstract String getFileName(EntityType type);

	protected abstract EntityWriter createEntityWriter(EntityType type, Writer streamWriter) throws IOException;

	protected abstract AbstractTextEntityReader<?> createEntityReader(EntityType type, HierarchicalResourceReader reader, String fileName);

	@Override
	public EntityWriter writeEntitySet(EntityType type, HierarchicalResourceWriter writer) throws IOException {
		String tableFile = getFileName(type);
		Writer streamWriter = new OutputStreamWriter(new BufferedOutputStream(writer.writeResource(tableFile)), Charset.forName("UTF-8"));
		try {
			return createEntityWriter(type, streamWriter);
		} catch (Exception e) {
			System.err.println("Export failed on entity " + type.getName());
			e.printStackTrace();
			streamWriter.close();
			if (e instanceof IOException) {
				throw (IOException) e;
			}
			throw new IOException("Error persisting entity " + type, e);
		}
	}

	@Override
	public EntityReader readEntitySet(EntityType type, HierarchicalResourceReader reader) throws IOException {
		AbstractTextEntityReader<?> entityReader = createEntityReader(type, reader, getFileName(type));
		return entityReader.fileExists() ? entityReader : null;
	}

	public static boolean isSerializable(EntityField field) {
		return field.getMappingField() == null;
	}

	public static boolean isComparable(Class<?> type) {
		if (Comparable.class.isAssignableFrom(type)) {
			return true;
		}
		if (type.isPrimitive() && type != Boolean.TYPE) {
			return true;
		}
		return false;
	}
}
