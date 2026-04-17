/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.boot.models.categorization;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.boot.models.internal.DomainModelCategorizationCollector;
import org.hibernate.boot.models.internal.GlobalRegistrationsImpl;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.testing.boot.BootstrapContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.orm.test.boot.models.SourceModelTestHelper.createBuildingContext;

/**
 * Tests that {@link DomainModelCategorizationCollector} correctly categorizes
 * entity inheritance hierarchies, including non-root entity subclasses.
 */
public class EntityInheritanceCategorizationTest {

	@Test
	void entitySubclassIsCollected() {
		final ModelsContext modelsContext = createBuildingContext(
				ParentEntity.class,
				ChildEntity.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );

			assertThat( collector.getRootEntities() )
					.extracting( ClassDetails::getClassName )
					.containsExactly( ParentEntity.class.getName() );

			assertThat( collector.getEntitySubclasses() )
					.extracting( ClassDetails::getClassName )
					.containsExactly( ChildEntity.class.getName() );

			assertThat( collector.getAllEntities() )
					.extracting( ClassDetails::getClassName )
					.containsExactlyInAnyOrder(
							ParentEntity.class.getName(),
							ChildEntity.class.getName()
					);
		}
	}

	@Test
	void mappedSuperclassDoesNotPreventRootEntity() {
		final ModelsContext modelsContext = createBuildingContext(
				BaseMappedSuperclass.class,
				RootEntityWithSuperclass.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );

			assertThat( collector.getRootEntities() )
					.extracting( ClassDetails::getClassName )
					.containsExactly( RootEntityWithSuperclass.class.getName() );

			assertThat( collector.getEntitySubclasses() ).isEmpty();

			assertThat( collector.getMappedSuperclasses() )
					.containsKey( BaseMappedSuperclass.class.getName() );
		}
	}

	@Entity
	public static class ParentEntity {
		@Id
		private Long id;
	}

	@Entity
	public static class ChildEntity extends ParentEntity {
		private String name;
	}

	@MappedSuperclass
	public static class BaseMappedSuperclass {
		@Id
		private Long id;
	}

	@Entity
	public static class RootEntityWithSuperclass extends BaseMappedSuperclass {
		private String name;
	}
}
