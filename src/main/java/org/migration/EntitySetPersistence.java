package org.migration;

import java.io.IOException;
import java.util.function.Consumer;

import org.migration.generic.EntityType;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.qommons.io.HierarchicalResourceReader;
import org.qommons.io.HierarchicalResourceWriter;

public interface EntitySetPersistence {
	interface EntityWriter extends AutoCloseable {
		boolean writeEntity(GenericEntity entity) throws IOException;

		@Override
		void close() throws IOException;
	}

	interface EntityReader {
		boolean readEntityIdentities(GenericEntitySet entities, Consumer<GenericEntity> onEntity) throws IOException;

		boolean populateEntityFields(GenericEntitySet entities, Consumer<GenericEntity> onCompleteEntity) throws IOException;
	}

	EntityWriter writeEntitySet(EntityType type, HierarchicalResourceWriter writer) throws IOException;

	EntityReader readEntitySet(EntityType type, HierarchicalResourceReader reader) throws IOException;

	class EmptyEntityWriter implements EntityWriter {
		@Override
		public boolean writeEntity(GenericEntity entity) throws IOException {
			return false;
		}

		@Override
		public void close() throws IOException {
		}
	}

	class EmptyEntityReader implements EntityReader {
		@Override
		public boolean readEntityIdentities(GenericEntitySet entities, Consumer<GenericEntity> onEntity) throws IOException {
			return true;
		}

		@Override
		public boolean populateEntityFields(GenericEntitySet entities, Consumer<GenericEntity> onCompleteEntity) throws IOException {
			return true;
		}
	}
}
