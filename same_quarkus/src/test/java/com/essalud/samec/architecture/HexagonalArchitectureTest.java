package com.essalud.samec.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.essalud.samec.shared.domain.port.CrudRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Hexagonal rules: the domain (models and ports) is pure Java; persistence adapters live in
 * infrastructure and implement the ports.
 */
class HexagonalArchitectureTest {

    private static final String[] HEXAGONAL_DOMAINS = {
            "com.essalud.samec.shared.domain..",
            "com.essalud.samec.catalog.domain..",
            "com.essalud.samec.assessment.domain..",
            "com.essalud.samec.filestorage.domain.."
    };

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.essalud.samec");

    @Test
    void domainDoesNotDependOnFrameworks() {
        noClasses().that().resideInAnyPackage(HEXAGONAL_DOMAINS)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..", "javax.persistence..", "org.hibernate..", "io.quarkus..", "io.smallrye..",
                        "com.fasterxml..", "lombok..")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnInfrastructureOrLegacyModules() {
        noClasses().that().resideInAnyPackage(HEXAGONAL_DOMAINS)
                .should().dependOnClassesThat(
                        resideInAnyPackage("..infrastructure..", "..application..",
                                "com.essalud.samec.usuarios..", "com.essalud.samec.organizacion..",
                                "com.essalud.samec.security..")
                                .or(resideInAPackage("com.essalud.samec.shared..")
                                        .and(not(resideInAPackage("com.essalud.samec.shared.domain..")))))
                .check(classes);
    }

    /** Use cases speak through ports: no web, no JPA, no entities, no legacy modules. */
    @Test
    void applicationDoesNotDependOnAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("com.essalud.samec..application..")
                .should().dependOnClassesThat(
                        resideInAnyPackage("..infrastructure..", "jakarta.ws.rs..", "jakarta.persistence..",
                                "org.hibernate..", "org.jboss.resteasy..", "io.quarkus..",
                                "com.essalud.samec.usuarios..", "com.essalud.samec.organizacion..",
                                "com.essalud.samec.security.."))
                .check(classes);
    }

    @Test
    void controllersOnlyTalkToUseCases() {
        noClasses().that().resideInAPackage("..infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage("..domain.port..", "..infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void catalogDoesNotKnowAssessment() {
        noClasses().that().resideInAPackage("com.essalud.samec.catalog..")
                .should().dependOnClassesThat().resideInAPackage("com.essalud.samec.assessment..")
                .check(classes);
    }

    @Test
    void repositoryPortsAreInterfacesInTheDomain() {
        classes().that().areAssignableTo(CrudRepository.class).and().areInterfaces()
                .should().resideInAnyPackage(HEXAGONAL_DOMAINS)
                .check(classes);
    }

    @Test
    void persistenceAdaptersLiveInInfrastructure() {
        classes().that().haveSimpleNameEndingWith("JpaAdapter")
                .should().resideInAPackage("..infrastructure.persistence..")
                .andShould().beAssignableTo(CrudRepository.class)
                .check(classes);
    }

    @Test
    void entitiesAreOnlyUsedByInfrastructure() {
        noClasses().that().resideInAnyPackage(HEXAGONAL_DOMAINS)
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Entity")
                .check(classes);
    }
}
