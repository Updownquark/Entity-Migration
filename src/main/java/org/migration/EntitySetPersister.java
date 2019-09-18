package org.migration;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.migration.EntitySetPersistence.EntityReader;
import org.migration.EntitySetPersistence.EntityWriter;
import org.migration.generic.EntityType;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.qommons.io.HierarchicalResourceReader;
import org.qommons.io.HierarchicalResourceWriter;

/** Persists {@link GenericEntitySet}s to serial storage and reads them back */
public class EntitySetPersister {
	private final EntitySetPersistence thePersistence;

    /**
	 * @param persistence
	 *            THe entity persistence scheme to use
	 */
	public EntitySetPersister(EntitySetPersistence persistence) {
		thePersistence = persistence;
    }

    /**
	 * Saves an entity set to serial storage, printing errors to System.err. This method does not save the type information.
	 * 
	 * @param entitySet
	 *            The entity set to save
	 * @param writer
	 *            The resource writer to save the entity data to
	 * @param inProgressMonitor
	 *            Notified when persistence begins for a type
	 * @param finishedMonitor
	 *            Notified when persistence finishes for a type
	 * @return Whether the save was completely successful
	 */
	public boolean save(GenericEntitySet entitySet, HierarchicalResourceWriter writer, Consumer<EntityType> inProgressMonitor,
			Consumer<EntityType> finishedMonitor) {
        boolean success = true;
        for (EntityType type : entitySet.getTypes()) {
            if (inProgressMonitor != null) {
				inProgressMonitor.accept(type);
			}
			success &= exportType(entitySet, type, writer);
            if (finishedMonitor != null) {
				finishedMonitor.accept(type);
			}
        }
        return success;
    }

    /**
	 * Parses serially-saved entity data into generic entities
	 * 
	 * @param entitySet
	 *            The entity set to populate with data from XML
	 * @param reader
	 *            The serial persistence interface to read the XML from
	 * @return Whether the parsing was completely successful
	 */
	public boolean read(GenericEntitySet entitySet, HierarchicalResourceReader reader) {
        boolean success = true;
        // Create all the entities first, so we can link them up during the field-parsing
        for (EntityType type : entitySet.getTypes()) {
			success &= createEntitiesFromExport(entitySet, type, reader);
		}
        // Parse and populate all the field values
        for (EntityType type : entitySet.getTypes()) {
			success &= importFieldValues(entitySet, type, reader);
		}
        return success;
    }

	private boolean exportType(GenericEntitySet entitySet, EntityType type, HierarchicalResourceWriter writer) {
		int success = 0;
		int total;
		try (EntityWriter entityPersister = thePersistence.writeEntitySet(type, writer)) {
			Collection<GenericEntity> beans = entitySet.queryAll(type);
            // Filter out sub-types
            beans = beans.stream().filter(bean -> bean.getType().getName().equals(type.getName())).collect(Collectors.toList());
			total = beans.size();
			for (GenericEntity bean : beans) {
				if (entityPersister.writeEntity(bean)) {
					success++;
				}
            }
		} catch (IOException e) {
            System.err.println("Export failed on entity " + type.getName());
            e.printStackTrace();
            return false;
		}

        String msg = success + " of " + total + " " + type.getName() + " rows exported";
        if (success == total) {
			System.out.println(msg);
		} else {
			System.err.println(msg);
		}
        return success == total;
    }

	private boolean createEntitiesFromExport(GenericEntitySet entitySet, EntityType type, HierarchicalResourceReader reader) {
		try {
			EntityReader entityReader = thePersistence.readEntitySet(type, reader);
			return entityReader.readEntityIdentities(entitySet, null);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    }

	private boolean importFieldValues(GenericEntitySet entitySet, EntityType type, HierarchicalResourceReader reader) {
		try {
			EntityReader entityReader = thePersistence.readEntitySet(type, reader);
			return entityReader.populateEntityFields(entitySet, null);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    }
}
