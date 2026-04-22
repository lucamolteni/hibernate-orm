/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.boot.models.categorization;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.AnnotationException;
import org.hibernate.boot.models.internal.DomainModelCategorizationCollector;
import org.hibernate.boot.models.internal.GlobalRegistrationsImpl;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.testing.boot.BootstrapContextImpl;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.orm.test.boot.models.SourceModelTestHelper.createBuildingContext;

/**
 * Tests that {@link DomainModelCategorizationCollector#validateEmbeddedFields} validates
 * that types used with {@code @Embedded} and {@code @EmbeddedId} are annotated with {@code @Embeddable}.
 */
public class EmbeddedValidationCategorizationTest {

	@Test
	void embeddedWithoutEmbeddableThrows() {
		final ModelsContext modelsContext = createBuildingContext(
				EntityWithBadEmbedded.class,
				NotEmbeddable.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );

			assertThatThrownBy( () -> modelsContext.getClassDetailsRegistry()
					.forEachClassDetails( DomainModelCategorizationCollector::validateEmbeddedFields ) )
					.isInstanceOf( AnnotationException.class )
					.hasMessageContaining( "must be annotated with @Embeddable" )
					.hasMessageContaining( NotEmbeddable.class.getName() )
					.hasMessageContaining( "bad" );
		}
	}

	@Test
	void embeddedIdWithoutEmbeddableThrows() {
		final ModelsContext modelsContext = createBuildingContext(
				EntityWithBadEmbeddedId.class,
				NotEmbeddable.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );

			assertThatThrownBy( () -> modelsContext.getClassDetailsRegistry()
					.forEachClassDetails( DomainModelCategorizationCollector::validateEmbeddedFields ) )
					.isInstanceOf( AnnotationException.class )
					.hasMessageContaining( "must be annotated with @Embeddable" )
					.hasMessageContaining( NotEmbeddable.class.getName() );
		}
	}

	@Test
	void validEmbeddableDoesNotThrow() {
		final ModelsContext modelsContext = createBuildingContext(
				EntityWithGoodEmbedded.class,
				ValidEmbeddable.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );
			modelsContext.getClassDetailsRegistry()
					.forEachClassDetails( DomainModelCategorizationCollector::validateEmbeddedFields );

			assertThat( collector.getEmbeddables() )
					.containsKey( ValidEmbeddable.class.getName() );
		}
	}

	@Test
	void genericEmbeddableDoesNotThrow() {
		final ModelsContext modelsContext = createBuildingContext(
				EntityWithGenericEmbedded.class,
				GenericEmbeddable.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );
			modelsContext.getClassDetailsRegistry()
					.forEachClassDetails( DomainModelCategorizationCollector::validateEmbeddedFields );

			assertThat( collector.getEmbeddables() )
					.containsKey( GenericEmbeddable.class.getName() );
		}
	}

	@Test
	void nestedEmbeddableDoesNotThrow() {
		final ModelsContext modelsContext = createBuildingContext(
				EntityWithNestedEmbedded.class,
				ValidEmbeddable.class,
				NestingEmbeddable.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );
			modelsContext.getClassDetailsRegistry()
					.forEachClassDetails( DomainModelCategorizationCollector::validateEmbeddedFields );

			assertThat( collector.getEmbeddables() )
					.containsKey( ValidEmbeddable.class.getName() )
					.containsKey( NestingEmbeddable.class.getName() );
		}
	}

	@Test
	void nestedEmbeddedWithoutEmbeddableThrows() {
		final ModelsContext modelsContext = createBuildingContext(
				EntityWithNestedBadEmbedded.class,
				NotEmbeddable.class,
				EmbeddableWithBadNested.class
		);

		try (BootstrapContextImpl bootstrapContext = new BootstrapContextImpl()) {
			final GlobalRegistrationsImpl globalRegistrations =
					new GlobalRegistrationsImpl( modelsContext, bootstrapContext );
			final DomainModelCategorizationCollector collector =
					new DomainModelCategorizationCollector( globalRegistrations, modelsContext );

			modelsContext.getClassDetailsRegistry().forEachClassDetails( collector::apply );

			assertThatThrownBy( () -> modelsContext.getClassDetailsRegistry()
					.forEachClassDetails( DomainModelCategorizationCollector::validateEmbeddedFields ) )
					.isInstanceOf( AnnotationException.class )
					.hasMessageContaining( "must be annotated with @Embeddable" )
					.hasMessageContaining( NotEmbeddable.class.getName() )
					.hasMessageContaining( EmbeddableWithBadNested.class.getName() );
		}
	}

	// --- Test model classes ---

	public static class NotEmbeddable {
		private String value;
	}

	@Embeddable
	public static class ValidEmbeddable {
		private String text;
	}

	@Embeddable
	public static class GenericEmbeddable<T> {
		private T value;
	}

	@Embeddable
	public static class NestingEmbeddable {
		private String text;

		@Embedded
		private ValidEmbeddable nested;
	}

	@Embeddable
	public static class EmbeddableWithBadNested {
		private String text;

		@Embedded
		private NotEmbeddable bad;
	}

	@Entity
	public static class EntityWithBadEmbedded {
		@Id
		private Long id;

		@Embedded
		private NotEmbeddable bad;
	}

	@Entity
	public static class EntityWithBadEmbeddedId {
		@EmbeddedId
		private NotEmbeddable id;
	}

	@Entity
	public static class EntityWithGoodEmbedded {
		@Id
		private Long id;

		@Embedded
		private ValidEmbeddable embedded;
	}

	@Entity
	public static class EntityWithGenericEmbedded {
		@Id
		private Long id;

		@Embedded
		private GenericEmbeddable<String> embedded;
	}

	@Entity
	public static class EntityWithNestedEmbedded {
		@Id
		private Long id;

		@Embedded
		private NestingEmbeddable embedded;
	}

	@Entity
	public static class EntityWithNestedBadEmbedded {
		@Id
		private Long id;

		@Embedded
		private EmbeddableWithBadNested embedded;
	}
}
