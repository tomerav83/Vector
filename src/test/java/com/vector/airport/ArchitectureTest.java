package com.vector.airport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the core portable: it may use jOOQ and Spring AI abstractions, never a concrete database, LLM provider, country
 * adapter or HTTP client. Swapping any of those = new adapter only.
 */
@AnalyzeClasses(packages = "com.vector.airport", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreIsPortable = noClasses()
            .that()
            .resideInAnyPackage("..core..", "..ports..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..adapters..", // concrete implementations (US data, live APIs, file cache)
                    "org.duckdb..", // database engine – go through jOOQ
                    "com.google.genai..", // LLM providers – go through Spring AI ChatClient
                    "org.springframework.ai.google..",
                    "com.anthropic..",
                    "org.springframework.ai.anthropic..",
                    "java.net.http..") // live HTTP calls belong in adapters
            .allowEmptyShould(true); // core/ports are filled in from stage 2 on
}
