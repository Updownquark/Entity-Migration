package org.migration.migrators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;
import org.migration.TypeSetDissecter;
import org.migration.generic.EntityField;
import org.migration.generic.EntityTypeSet;
import org.migration.generic.GenericEntity;
import org.migration.generic.GenericEntitySet;

public class SwitchMigrator extends JavaMigrator {
	private final Map<Object, List<EntityMigrator>> theValueMigrators = new HashMap<>();
	private List<EntityMigrator> theDefaultMigrators;
	private List<EntityField> theSwitchPath;

	@Override
	public JavaMigrator init(EntityTypeSet entities, TypeSetDissecter dissecter) {
		super.init(entities, dissecter);

		theSwitchPath = ValuePullMigrator.getFieldPath(getEntity(), serialize().getAttributeValue("value").trim());
		EntityField pathLast = theSwitchPath.get(theSwitchPath.size() - 1);
		for (Element caseEl : serialize().getChildren("case")) {
			Object caseValue = DefaultFieldValueMigrator.createDefaultValue(pathLast.getType(), null, dissecter,
					caseEl.getAttributeValue("value"));

			List<EntityMigrator> caseMigrators = parseCaseMigrators(caseEl, entities, dissecter);
			if (theValueMigrators.put(caseValue, caseMigrators) != null)
				throw new IllegalStateException("Duplicate cases " + caseValue + " for " + this);
		}
		Element defaultEl = serialize().getChild("default");
		if (defaultEl != null)
			theDefaultMigrators = parseCaseMigrators(defaultEl, entities, dissecter);
		return this;
	}

	private List<EntityMigrator> parseCaseMigrators(Element caseEl, EntityTypeSet entities, TypeSetDissecter dissecter) {
		List<EntityMigrator> caseMigrators = new ArrayList<>(caseEl.getChildren().size());
		String entity = getEntityName();
		for (Element subMigEl : caseEl.getChildren()) {
			subMigEl.setAttribute("entity", entity);
			EntityMigrator migrator = getFactory().deserialize(subMigEl, entities, getMigration());
			if (migrator instanceof EntityTypeModificationMigrator || migrator instanceof EnumTypeModificationMigrator)
				throw new IllegalStateException("No type modifications allowed in switch");
			if (migrator instanceof CustomMigrator)
				((CustomMigrator) migrator).init(entities, dissecter);
			caseMigrators.add(migrator);
			subMigEl.removeAttribute("entity");
			if (migrator instanceof DescendToSubType)
				entity = ((DescendToSubType) migrator).getSubType();
			else if (migrator instanceof AscendToSuperType)
				entity = ((AscendToSuperType) migrator).getSuperType();
		}
		return caseMigrators;
	}

	@Override
	public GenericEntity migrate(GenericEntity oldVersionEntity, GenericEntitySet allEntities, TypeSetDissecter dissecter) {
		Object caseValue = ValuePullMigrator.evaluateFieldPath(oldVersionEntity, theSwitchPath, false);
		List<EntityMigrator> caseMigrators = theValueMigrators.get(caseValue);
		if (caseMigrators == null)
			caseMigrators = theDefaultMigrators;
		if (caseMigrators == null)
			throw new IllegalStateException("Case value " + caseValue + " is not covered for " + this);
		GenericEntity replacement = oldVersionEntity;
		for (EntityMigrator migrator : caseMigrators) {
			GenericEntity newReplacement = migrator.migrate(replacement, allEntities, dissecter);
			if (newReplacement != replacement && replacement != oldVersionEntity)
				allEntities.removeEntity(replacement);
			replacement = newReplacement;
		}
		return replacement;
	}
}
