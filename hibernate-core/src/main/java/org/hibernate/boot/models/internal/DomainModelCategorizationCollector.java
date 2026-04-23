/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.boot.models.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.hibernate.AnnotationException;
import org.hibernate.annotations.CompositeType;
import org.hibernate.boot.jaxb.mapping.spi.JaxbEntityMappingsImpl;
import org.hibernate.boot.models.spi.GlobalRegistrations;
import org.hibernate.boot.models.xml.spi.XmlDocumentContext;
import org.hibernate.models.spi.AnnotationTarget;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.MemberDetails;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.models.spi.TypeDetails;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hibernate.boot.model.internal.EmbeddableBinder.isEmbeddable;
import static org.hibernate.boot.model.internal.EntityBinder.isEntity;
import static org.hibernate.boot.model.internal.EntityBinder.isMappedSuperclass;

/**
 * In-flight holder for various things as we process metadata sources
 *
 * @author Steve Ebersole
 */
public class DomainModelCategorizationCollector {
	private final GlobalRegistrationsImpl globalRegistrations;
	private final ModelsContext modelsContext;

	private final Set<ClassDetails> rootEntities = new HashSet<>();
	private final Set<ClassDetails> entitySubclasses = new HashSet<>();
	private final Map<String,ClassDetails> mappedSuperclasses = new HashMap<>();
	private final Map<String,ClassDetails> embeddables = new HashMap<>();
	private final Set<String> idClasses = new HashSet<>();
	private final Set<ClassDetails> entityListenerClasses = new HashSet<>();
	private final Set<String> packageNames = new HashSet<>();
	private final Set<String> enumTypes = new HashSet<>();
	private final Set<String> javaTypes = new HashSet<>();

	public DomainModelCategorizationCollector(
			GlobalRegistrations globalRegistrations,
			ModelsContext modelsContext) {
		this.globalRegistrations = (GlobalRegistrationsImpl) globalRegistrations;
		this.modelsContext = modelsContext;
	}

	public GlobalRegistrationsImpl getGlobalRegistrations() {
		return globalRegistrations;
	}

	public Set<ClassDetails> getRootEntities() {
		return rootEntities;
	}

	public Set<ClassDetails> getEntitySubclasses() {
		return entitySubclasses;
	}

	public Set<ClassDetails> getAllEntities() {
		Set<ClassDetails> all = new HashSet<>( rootEntities );
		all.addAll( entitySubclasses );
		return all;
	}

	public Map<String, ClassDetails> getMappedSuperclasses() {
		return mappedSuperclasses;
	}

	public Map<String, ClassDetails> getEmbeddables() {
		return embeddables;
	}

	public Set<String> getIdClasses() {
		return idClasses;
	}

	/**
	 * Classes that have methods annotated with JPA lifecycle callback annotations
	 * ({@code @PrePersist}, {@code @PostPersist}, {@code @PreRemove}, {@code @PostRemove},
	 * {@code @PreUpdate}, {@code @PostUpdate}, {@code @PostLoad}).
	 * <p>
	 * These are classes that act as entity listeners (either standalone listener classes
	 * referenced via {@code @EntityListeners} or entity classes with callback methods).
	 */
	public Set<ClassDetails> getEntityListenerClasses() {
		return entityListenerClasses;
	}

	/**
	 * Package names discovered from {@code package-info} classes encountered
	 * during categorization.
	 */
	public Set<String> getPackageNames() {
		return packageNames;
	}

	/**
	 * Enum types discovered as field types on entities, mapped superclasses,
	 * and embeddables. These need reflection registration for native image builds.
	 */
	public Set<String> getEnumTypes() {
		return enumTypes;
	}

	/**
	 * Types from the {@code java.*} package discovered in the class hierarchy
	 * (superclasses and interfaces) of entities, mapped superclasses, and embeddables.
	 * These need reflection registration for native image builds.
	 */
	public Set<String> getJavaTypes() {
		return javaTypes;
	}

	public void apply(JaxbEntityMappingsImpl jaxbRoot, XmlDocumentContext xmlDocumentContext) {
		globalRegistrations.collectJavaTypeRegistrations( jaxbRoot.getJavaTypeRegistrations() );
		globalRegistrations.collectJdbcTypeRegistrations( jaxbRoot.getJdbcTypeRegistrations() );
		globalRegistrations.collectConverterRegistrations( jaxbRoot.getConverterRegistrations() );
		globalRegistrations.collectConverters( jaxbRoot.getConverters() );
		globalRegistrations.collectUserTypeRegistrations( jaxbRoot.getUserTypeRegistrations() );
		globalRegistrations.collectCompositeUserTypeRegistrations( jaxbRoot.getCompositeUserTypeRegistrations() );
		globalRegistrations.collectCollectionTypeRegistrations( jaxbRoot.getCollectionUserTypeRegistrations() );
		globalRegistrations.collectEmbeddableInstantiatorRegistrations( jaxbRoot.getEmbeddableInstantiatorRegistrations() );
		globalRegistrations.collectFilterDefinitions( jaxbRoot.getFilterDefinitions() );

		final var persistenceUnitMetadata = jaxbRoot.getPersistenceUnitMetadata();
		if ( persistenceUnitMetadata != null ) {
			final var persistenceUnitDefaults = persistenceUnitMetadata.getPersistenceUnitDefaults();
			if ( persistenceUnitDefaults != null ) {
				final var listenerContainer = persistenceUnitDefaults.getEntityListenerContainer();
				if ( listenerContainer != null ) {
					getGlobalRegistrations()
							.collectEntityListenerRegistrations( listenerContainer.getEntityListeners(), modelsContext );
				}
			}
		}

		getGlobalRegistrations().collectIdGenerators( jaxbRoot );

		getGlobalRegistrations().collectQueryReferences( jaxbRoot, xmlDocumentContext );

		getGlobalRegistrations().collectDataBaseObject( jaxbRoot.getDatabaseObjects() );
		// todo (7.0) : named graphs?
	}

	public void apply(ClassDetails classDetails) {
		globalRegistrations.collectJavaTypeRegistrations( classDetails );
		globalRegistrations.collectJdbcTypeRegistrations( classDetails );
		globalRegistrations.collectConverterRegistrations( classDetails );
		globalRegistrations.collectUserTypeRegistrations( classDetails );
		globalRegistrations.collectCompositeUserTypeRegistrations( classDetails );
		globalRegistrations.collectCollectionTypeRegistrations( classDetails );
		globalRegistrations.collectEmbeddableInstantiatorRegistrations( classDetails );
		globalRegistrations.collectFilterDefinitions( classDetails );

		globalRegistrations.collectIdGenerators( classDetails );

		globalRegistrations.collectImportRename( classDetails );

		// todo : named queries
		// todo : named graphs

		if ( isMappedSuperclass( classDetails ) ) {
			if ( classDetails.getClassName() != null ) {
				mappedSuperclasses.put( classDetails.getClassName(), classDetails );
			}
			collectFieldEnumTypes( classDetails );
			collectClassHierarchyJavaTypes( classDetails );
		}
		else if ( isEntity( classDetails ) ) {
			if ( isRootEntity( classDetails ) ) {
				rootEntities.add( classDetails );
			}
			else {
				entitySubclasses.add( classDetails );
			}
			collectFieldEnumTypes( classDetails );
			collectClassHierarchyJavaTypes( classDetails );
		}
		else if ( isEmbeddable( classDetails ) ) {
			if ( classDetails.getClassName() != null ) {
				embeddables.put( classDetails.getClassName(), classDetails );
			}
			collectFieldEnumTypes( classDetails );
			collectClassHierarchyJavaTypes( classDetails );
		}

		if ( hasIdClass( classDetails ) ) {
			idClasses.add( classDetails.getDirectAnnotationUsage( IdClass.class ).value().getName() );
		}

		if ( isConverter( classDetails ) ) {
			globalRegistrations.collectConverter( classDetails );
		}

		if ( hasJpaLifecycleCallbackMethods( classDetails ) ) {
			entityListenerClasses.add( classDetails );
		}

		if ( isPackageInfo( classDetails ) ) {
			String className = classDetails.getClassName();
			packageNames.add( className.substring( 0, className.lastIndexOf( '.' ) ) );
		}
	}

	private static boolean hasJpaLifecycleCallbackMethods(ClassDetails classDetails) {
		final boolean[] found = { false };
		classDetails.forEachMethod( (index, methodDetails) -> {
			if ( !found[0] && hasAnyDirectAnnotationUsage( methodDetails,
					PrePersist.class, PostPersist.class,
					PreRemove.class, PostRemove.class,
					PreUpdate.class, PostUpdate.class,
					PostLoad.class ) ) {
				found[0] = true;
			}
		} );
		return found[0];
	}

	@SafeVarargs
	private static boolean hasAnyDirectAnnotationUsage(AnnotationTarget target, Class<? extends Annotation>... types) {
		for ( Class<? extends Annotation> type : types ) {
			if ( target.hasDirectAnnotationUsage( type ) ) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasIdClass(ClassDetails classDetails) {
		return classDetails.getDirectAnnotationUsage( IdClass.class ) != null;
	}

	private static boolean isPackageInfo(ClassDetails classDetails) {
		String className = classDetails.getClassName();
		return className != null && className.endsWith( ".package-info" );
	}

	private static boolean isConverter(ClassDetails classDetails) {
		return classDetails.getClassName() != null && !classDetails.isInterface()
			&& ( classDetails.isImplementor( AttributeConverter.class )
				|| classDetails.getDirectAnnotationUsage( Converter.class ) != null );
	}

	/**
	 * Validates that fields and methods annotated with {@code @Embedded} or {@code @EmbeddedId}
	 * reference types annotated with {@code @Embeddable}.
	 * <p>
	 * This check is not part of the normal categorization flow because Hibernate ORM tolerates
	 * {@code @Embedded} on types without {@code @Embeddable} (e.g. composite id classes, interface
	 * types with {@code @TargetEmbeddable}, etc.). Environments that require strict validation
	 * — such as Quarkus, where all classes must be in the Jandex index at build time — can call
	 * this method explicitly to catch missing {@code @Embeddable} annotations early.
	 * <p>
	 * Skips members that use {@code @CompositeType}, have unresolved generic type variables,
	 * or whose type is {@code null} (erased method return types).
	 *
	 * @param classDetails the class to validate
	 * @throws org.hibernate.AnnotationException if a member's type is missing {@code @Embeddable}
	 */
	public static void validateEmbeddedFields(ClassDetails classDetails) {
		classDetails.forEachField( (index, fieldDetails) -> validateEmbeddedMember( fieldDetails, classDetails ) );
		classDetails.forEachMethod( (index, methodDetails) -> validateEmbeddedMember( methodDetails, classDetails ) );
	}

	private static void validateEmbeddedMember(MemberDetails memberDetails, ClassDetails declaringClass) {
		if ( !memberDetails.isPersistable() ) {
			return;
		}

		if ( !memberDetails.hasDirectAnnotationUsage( Embedded.class )
				&& !memberDetails.hasDirectAnnotationUsage( EmbeddedId.class ) ) {
			return;
		}

		if ( memberDetails.hasDirectAnnotationUsage( CompositeType.class ) ) {
			return;
		}

		final TypeDetails type = memberDetails.getType();
		if ( type == null ) {
			return;
		}

		if ( type.getTypeKind() == TypeDetails.Kind.TYPE_VARIABLE
				|| type.getTypeKind() == TypeDetails.Kind.TYPE_VARIABLE_REFERENCE
				|| type.getTypeKind() == TypeDetails.Kind.WILDCARD_TYPE ) {
			return;
		}

		final ClassDetails fieldTypeClass = type.determineRawClass();
		if ( fieldTypeClass != null && !fieldTypeClass.hasDirectAnnotationUsage( Embeddable.class ) ) {
			throw new AnnotationException( String.format(
					"Type '%s' must be annotated with @Embeddable, because it is used as an embeddable."
							+ " This type is used in class '%s' for attribute '%s'.",
					fieldTypeClass.getName(),
					declaringClass.getName(),
					memberDetails.resolveAttributeName()
			) );
		}
	}

	private void collectFieldEnumTypes(ClassDetails classDetails) {
		classDetails.forEachField( (index, fieldDetails) -> {
			final TypeDetails type = fieldDetails.getType();
			if ( type == null || type.getTypeKind() != TypeDetails.Kind.CLASS ) {
				return;
			}
			final ClassDetails fieldTypeClass = type.determineRawClass();
			if ( fieldTypeClass != null && fieldTypeClass.isEnum() ) {
				enumTypes.add( fieldTypeClass.getClassName() );
			}
		} );
	}

	private void collectClassHierarchyJavaTypes(ClassDetails classDetails) {
		collectClassHierarchyJavaTypes( classDetails, new HashSet<>() );
	}

	private void collectClassHierarchyJavaTypes(ClassDetails classDetails, Set<String> visited) {
		if ( classDetails == null ) {
			return;
		}
		final String className = classDetails.getClassName();
		if ( className == null || !visited.add( className ) ) {
			return;
		}
		if ( "java.lang.Object".equals( className ) ) {
			return;
		}
		if ( className.startsWith( "java." ) ) {
			javaTypes.add( className );
			return;
		}
		collectClassHierarchyJavaTypes( classDetails.getSuperClass(), visited );
		for ( TypeDetails iface : classDetails.getImplementedInterfaces() ) {
			collectClassHierarchyJavaTypes( iface.determineRawClass(), visited );
		}
	}

	public static boolean isRootEntity(ClassDetails classInfo) {
		// perform a series of opt-out checks against the super-type hierarchy

		// an entity is considered a root of the hierarchy if:
		// 		1) it has no super-types
		//		2) its super types contain no entities (MappedSuperclasses are allowed)

		var current = classInfo.getSuperClass();
		while ( current != null ) {
			if ( isEntity( current ) && !current.isAbstract() ) {
				// a non-abstract super type has `@Entity` -> classInfo cannot be a root entity
				return false;
			}
			current = current.getSuperClass();
		}

		// if we hit no opt-outs we have a root
		return true;
	}
}
