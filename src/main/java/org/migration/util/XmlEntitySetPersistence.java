package org.migration.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.JDOMException;
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
import org.migration.generic.EnumValue;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.qommons.StringUtils;
import org.qommons.io.HierarchicalResourceReader;

/** Reads and writes entities from/to XML files */
public class XmlEntitySetPersistence extends AbstractTextEntitySetPersistence {
	private static class XmlEntityWriter extends AbstractTextEntityWriter {
		/** The maximum number of fields a data type can have for it to be represented in a single XML element */
		private static final int MAX_SIMPLE_FIELDS = 3;

		private final XMLStreamWriter theXmlWriter;
		private final String theRootName;
		private boolean hasWrittenAny;
		private int theIndent;

		XmlEntityWriter(EntityType type, TypeSetDissecter dissecter, Writer writer, XMLOutputFactory xml, String rootName)
				throws IOException {
			super(type, dissecter);
			theRootName = rootName;
			try {
				theXmlWriter = xml.createXMLStreamWriter(writer);
				theXmlWriter.writeStartDocument("UTF-8", "1.0");
				theXmlWriter.writeCharacters("\n");
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
			theIndent = 1;
		}

		@Override
		public boolean writeEntity(GenericEntity entity) throws IOException {
			try {
				if (!hasWrittenAny) {
					hasWrittenAny = true;
					theXmlWriter.writeStartElement(theRootName);
					theXmlWriter.writeCharacters("\n");
				}
				return super.writeEntity(entity);
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		public void close() throws IOException {
			try {
				if (!hasWrittenAny) {
					theXmlWriter.writeEmptyElement(theRootName);
				} else {
					theXmlWriter.writeEndElement();
				}
				theXmlWriter.writeEndDocument();
				theXmlWriter.writeCharacters("\n");
				theXmlWriter.close();
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		protected boolean writeFieldValue(String fieldName, Type fieldType, boolean maySort, EntityField field, Object fieldValue)
				throws IOException {
			theIndent++;
			try {
				return super.writeFieldValue(fieldName, fieldType, maySort, field, fieldValue);
			} finally {
				theIndent--;
			}
		}

		@Override
		protected void beginEntity(GenericEntity entity) throws IOException {
			hasWrittenAny = true;
			try {
				theXmlWriter.writeCharacters("\t");
				theXmlWriter.writeStartElement(getType().getName());
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		protected void endEntity(GenericEntity entity) throws IOException {
			try {
				theXmlWriter.writeCharacters("\n\t");
				theXmlWriter.writeEndElement();
				theXmlWriter.writeCharacters("\n");
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		private String indent() {
			StringBuilder indent = new StringBuilder("\n");
			for (int i = 0; i < theIndent; i++) {
				indent.append('\t');
			}
			return indent.toString();
		}

		@Override
		protected void writeNullValue(String fieldName) throws IOException {
			try {
				theXmlWriter.writeCharacters(indent());
				theXmlWriter.writeEmptyElement(fieldName);
				theXmlWriter.writeAttribute("null", "true");
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		protected void writeReference(String fieldName, GenericEntity value, String serializedId) throws IOException {
			try {
				theXmlWriter.writeCharacters(indent());
				theXmlWriter.writeEmptyElement(fieldName);
				theXmlWriter.writeAttribute(value.getType().getIdField().getName(), serializedId);
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		protected void writeSimpleField(String fieldName, Type type, String subType, EntityField field, String serializedValue)
				throws IOException {
			try {
				if (field.isId()) {
					theXmlWriter.writeAttribute(fieldName, serializedValue);
				} else {
					theXmlWriter.writeCharacters(indent());
					theXmlWriter.writeStartElement(fieldName);
					theXmlWriter.writeCharacters(serializedValue);
					theXmlWriter.writeEndElement();
				}
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		protected boolean writeComplexField(String fieldName, String subType, TypedField[] subFields, EntityField field,
				Object[] subFieldValues) throws IOException {
			try {
				String indent = indent();
				theXmlWriter.writeCharacters(indent);
				boolean compress = subFields.length <= MAX_SIMPLE_FIELDS;
				boolean success = true;
				if (compress) {
					for (TypedField subField : subFields) {
						if (!isSimpleField(subField.name, subField.type, getDissecter())) {
							compress = false;
							break;
						}
					}
				}
				if (compress) {
					theXmlWriter.writeEmptyElement(fieldName);
				} else {
					theXmlWriter.writeStartElement(fieldName);
				}
				if (subType != null) {
					theXmlWriter.writeAttribute("type", subType);
				}
				boolean hasElements = false;
				for (int f = 0; f < subFields.length; f++) {
					TypedField subField = subFields[f];
					Object fieldValue = subFieldValues[f];
					if (compress) {
						if (fieldValue instanceof EnumValue) {
							theXmlWriter.writeAttribute(subField.name, ((EnumValue) fieldValue).getName());
						} else {
							theXmlWriter.writeAttribute(subField.name,
									getDissecter().getFormat((Class<?>) subField.type).format(fieldValue));
						}
					} else {
						hasElements = true;
						theIndent++;
						try {
							success &= writeFieldValue(subField.name, subField.type, !subField.isOrdered(), field, fieldValue);
						} finally {
							theIndent--;
						}
					}
				}
				if (hasElements) {
					theXmlWriter.writeCharacters(indent);
				}
				if (!compress) {
					theXmlWriter.writeEndElement();
				}
				return success;
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}

		@Override
		protected boolean writeCollectionField(String fieldName, String subType, Type collectionType, CollectionDissecter cd,
				EntityField field,
				Iterable<?> elements) throws IOException {
			try {
				String indent = indent();
				theXmlWriter.writeCharacters(indent);
				String childName;
				if (field.getType() == collectionType) {
					childName = StringUtils.singularize(fieldName);
				} else {
					childName = cd.getElementName(); // A sub-field
				}
				Iterator<?> elementIter = elements.iterator();
				boolean hasElements = false;
				boolean success = true;
				if (elementIter.hasNext()) {
					hasElements = true;
					theXmlWriter.writeStartElement(fieldName);
					if (subType != null) {
						theXmlWriter.writeAttribute("type", subType);
					}
					while (elementIter.hasNext()) {
						theIndent++;
						try {
							success &= writeFieldValue(childName, cd.getComponentType(), false, field, elementIter.next());
						} finally {
							theIndent--;
						}
					}
				} else {
					theXmlWriter.writeEmptyElement(fieldName);
					if (subType != null) {
						theXmlWriter.writeAttribute("type", subType);
					}
				}
				if (hasElements) {
					theXmlWriter.writeCharacters(indent);
					theXmlWriter.writeEndElement();
				}
				return success;
			} catch (XMLStreamException e) {
				throw new IOException("XML output exception", e);
			}
		}
	}

	private static class XmlEntityReader extends AbstractTextEntityReader<Element> {
		XmlEntityReader(EntityType type, HierarchicalResourceReader reader, TypeSetDissecter dissecter, String fileName) {
			super(type, dissecter, reader, fileName);
		}

		@Override
		protected void parseEntityStructures(BufferedReader reader, GenericEntitySet entities, Consumer<Element> onEntity)
				throws IOException {
			try {
				PersistenceUtils.parseSerial(reader, new PersistenceUtils.ElementHandler() {
					@Override
					public void handle(Element element) {
						onEntity.accept(element);
					}
				});
			} catch (JDOMException e) {
				throw new IOException("Could not parse entity XML", e);
			}
		}

		@Override
		protected String getIdentity(Element entityStructure) {
			return entityStructure.getAttributeValue(getType().getIdField().getName());
		}

		@Override
		protected boolean deserializeFieldsFor(GenericEntity item, Element element, GenericEntitySet entities) {
			boolean success = true;

			Object[] fieldValue = new Object[1];
			for (Attribute att : element.getAttributes()) {
				EntityField field = item.getType().getField(att.getName());
				if (!isSimpleField(field.getName(), field.getType(), getDissecter()) || field.isId()) {
					continue;
				}
				if (field.getType() instanceof EnumType) {
					fieldValue[0] = ((EnumType) field.getType()).getValue(att.getValue());
					if (fieldValue[0] == null) {
						throw new IllegalArgumentException("No such enum value " + field.getType() + "." + att.getValue());
					}
				} else {
					fieldValue[0] = getDissecter().getFormat((Class<?>) field.getType()).parse((Class<?>) field.getType(), att.getValue());
				}
				item.set(field.getName(), fieldValue[0]);
			}
			for (Element fieldEl : element.getChildren()) {
				EntityField field = item.getType().getField(fieldEl.getName());
				if (field == null) {
					throw new IllegalStateException(
							"Bad serialized data: No such field " + item.getType() + "." + fieldEl.getName());
				}
				boolean fieldSuccess = getValue(fieldEl, fieldValue, field.getType(), field, entities);
				if (fieldSuccess) {
					item.set(field.getName(), fieldValue[0]);
				}
				success &= fieldSuccess;
			}
			return success;
		}

		private boolean getValue(Element fieldEl, Object[] fieldValue, Type type, EntityField field, GenericEntitySet entitySet) {
			if ("true".equals(fieldEl.getAttributeValue("null"))) {
				fieldValue[0] = null;
				return true;
			} else if (type instanceof EnumType) {
				fieldValue[0] = ((EnumType) type).getValue(fieldEl.getTextTrim());
				if (fieldValue[0] == null) {
					throw new IllegalArgumentException("No such enum value " + type + "." + fieldEl.getTextTrim());
				}
				return true;
			}
			if (type instanceof EntityType) {
				EntityField idField = ((EntityType) type).getIdField();
				SimpleFormat idFormat = getDissecter().getFormat((Class<?>) idField.getType());
				String idString = fieldEl.getAttributeValue(idField.getName());
				if (idString == null) {
					System.err.println("No " + idField.getName() + " attribute set for " + type + " " + field);
					return false;
				}
				Object idValue;
				try {
					idValue = idFormat.parse((Class<?>) idField.getType(), idString);
				} catch (RuntimeException e) {
					System.err.println("Could not parse " + idField + " from " + idString + " for " + field);
					e.printStackTrace();
					return false;
				}
				fieldValue[0] = entitySet.queryById((EntityType) type, idValue);
				if (fieldValue[0] == null) {
					System.err.println("No such " + type + " with " + idField.getName() + " " + idValue + " for " + field);
					return false;
				}
				return true;
			}
			Class<?> raw = PersistenceUtils.getRawType(type);
			SimpleFormat format = getDissecter().getFormat(raw);
			if (format != null) {
				try {
					fieldValue[0] = format.parse(raw, fieldEl.getText());
				} catch (RuntimeException e) {
					System.err
							.println("Could not parse " + PersistenceUtils.toString(type) + " from " + fieldEl.getText() + " for " + field);
					e.printStackTrace();
					return false;
				}
				return true;
			}
			DissecterGenerator gen = getDissecter().getDissecter(raw);
			if (gen == null) {
				System.err.println("Unrecognized type: " + PersistenceUtils.toString(type));
				return false;
			}
			Dissecter dissecter = gen.dissect(type, fieldEl.getAttributeValue("type"));
			if (dissecter instanceof ValueDissecter) {
				ValueDissecter vd = (ValueDissecter) dissecter;
				Map<String, Object> fieldValues = new LinkedHashMap<>();
				for (TypedField f : vd.getFields()) {
					Element subFieldEl = fieldEl.getChild(f.name);
					if (subFieldEl == null) {
						if (isSimpleField(f.name, f.type, getDissecter())) {
							String att = fieldEl.getAttributeValue(f.name);
							if (att == null) {
								System.err.println("No data configured for " + f + " for " + field);
								return false;
							}
							fieldValues.put(f.name, getDissecter().getFormat((Class<?>) f.type).parse((Class<?>) f.type, att));
						} else {
							Dissecter dissecter2 = getDissecter().getDissecter(PersistenceUtils.getRawType(f.type)).dissect(f.type, null);
							if (dissecter2 instanceof CollectionDissecter) {
								fieldValues.put(f.name, ((CollectionDissecter) dissecter2).createFrom(Collections.EMPTY_LIST, f));
							} else {
								System.err.println("No data configured for " + f + " for " + field);
								return false;
							}
						}
					} else {
						if (!getValue(subFieldEl, fieldValue, f.type, field, entitySet)) {
							return false;
						}
						fieldValues.put(f.name, fieldValue[0]);
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
			} else if (dissecter instanceof CollectionDissecter) {
				CollectionDissecter cd = (CollectionDissecter) dissecter;
				Type componentType = cd.getComponentType();
				ArrayList<Object> elements = new ArrayList<>();
				for (Element child : fieldEl.getChildren()) {
					if (getValue(child, fieldValue, componentType, field, entitySet)) {
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
	}

	private final XMLOutputFactory theXml;

	/**
	 * @param dissecter
	 *            The dissecter to understand the types
	 */
	public XmlEntitySetPersistence(TypeSetDissecter dissecter) {
		super(dissecter);
		theXml = XMLOutputFactory.newFactory();
	}

	@Override
	protected String getFileName(EntityType type) {
		return PersistenceUtils.xmlToJava(type.getName(), true) + ".xml";
	}

	@Override
	protected EntityWriter createEntityWriter(EntityType type, Writer streamWriter) throws IOException {
		String rootName = StringUtils.pluralize(type.getName());
		return new XmlEntityWriter(type, getDissecter(), streamWriter, theXml, rootName);
	}

	@Override
	protected AbstractTextEntityReader<?> createEntityReader(EntityType type, HierarchicalResourceReader reader, String fileName) {
		return new XmlEntityReader(type, reader, getDissecter(), fileName);
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
