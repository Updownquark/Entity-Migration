package org.migration.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.migration.EntitySetPersistence;
import org.migration.generic.EntityType;
import org.qommons.io.HierarchicalResourceReader;
import org.qommons.io.HierarchicalResourceWriter;

public class CompositeEntitySetPersistence implements EntitySetPersistence {
	private final List<EntitySetPersistence> theComponents;
	private EntitySetPersistence theExportComponent;
	private EntitySetPersistence theLastImportComponent;

	public CompositeEntitySetPersistence() {
		theComponents = new ArrayList<>();
	}

	public CompositeEntitySetPersistence addComponent(EntitySetPersistence component, boolean asExport) {
		theComponents.add(component);
		if (asExport) {
			theExportComponent = component;
		}
		return this;
	}

	@Override
	public EntityWriter writeEntitySet(EntityType type, HierarchicalResourceWriter writer) throws IOException {
		if (theExportComponent == null) {
			throw new IllegalStateException("No export component specified");
		}
		return theExportComponent.writeEntitySet(type, writer);
	}

	@Override
	public EntityReader readEntitySet(EntityType type, HierarchicalResourceReader reader) throws IOException {
		if (theLastImportComponent != null) {
			/* An optimization.
			 * Data sets will generally all be of the same persistence scheme, so it is helpful
			 * to check against the last component that recognized a data set.
			 * Implementation detail: the operation to check whether component actually recognizes a data set is somewhat expensive
			 * because this relies on a file's existence in the export.
			 * The HierarchicalResourceReader interface does not currently support a file existence query,
			 * so the file has to actually be opened to do this check.
			 * This optimization eliminates this unfortunate performance problem.
			 */
			EntityReader entityReader = theLastImportComponent.readEntitySet(type, reader);
			if (entityReader != null) {
				return entityReader;
			}
		}
		for (EntitySetPersistence component : theComponents) {
			EntityReader entityReader = component.readEntitySet(type, reader);
			if (entityReader != null) {
				theLastImportComponent = component;
				return entityReader;
			}
		}
		return null;
	}
}
