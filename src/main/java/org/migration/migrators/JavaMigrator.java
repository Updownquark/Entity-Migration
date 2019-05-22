package org.migration.migrators;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.migration.MigrationSet;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityType;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;
import org.migration.generic.MigratorFactory;

/** Migrates entities with custom java code */
public abstract class JavaMigrator implements CustomMigrator {
	private MigratorFactory theFactory;
    private MigrationSet theMigration;

    private String theEntityName;
	private EntityType theEntity;

    private Element theConfig;

    @Override
    public JavaMigrator init(String entity, Element config, MigrationSet migration, MigratorFactory factory) {
        theEntityName = entity;
        theConfig = config;
        theMigration = migration;
		theFactory = factory;
        return this;
    }

    @Override
	public JavaMigrator init(EntityTypeSet entities, TypeSetDissecter dissecter) {
		if (theEntityName != null) {
			theEntity = entities.getEntityType(theEntityName);
			if (theEntity == null) {
				throw new IllegalStateException("Unrecognized entity type: " + theEntityName);
			}
		}
		return this;
	}

	@Override
    public String getEntityName() {
        return theEntityName;
    }

	/** @return The type of entity that this migrator will operate on */
	public EntityType getEntity() {
		return theEntity;
	}

	/** @return The factory to use to create sub-migrators if needed */
	public MigratorFactory getFactory(){
		return theFactory;
	}

	/** @return The migration set that this migrator is a part of */
    public MigrationSet getMigration() {
        return theMigration;
    }

    @Override
    public abstract GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter);

    @Override
    public Element serialize() {
        return theConfig;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder(getClass().getSimpleName());
        boolean hasAtt = false;
        for (Attribute att : theConfig.getAttributes()) {
            if (att.getName().equals("type")) {
				continue;
			}
            if (!hasAtt) {
				ret.append(": ");
			} else {
				ret.append(" ");
			}
            hasAtt = true;
            ret.append(att.getName()).append("=\"").append(att.getValue()).append('"');
        }
        if (theConfig.getTextTrim() != null && theConfig.getTextTrim().length() > 0) {
			ret.append(" (").append(theConfig.getTextTrim()).append(")");
		}
        return ret.toString();
    }
}
