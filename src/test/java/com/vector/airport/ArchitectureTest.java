package com.vector.airport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.vector.airport.data.format.Format;
import com.vector.airport.data.source.Downloader;
import com.vector.airport.data.source.Web;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The structure CLAUDE.md describes, checked on every build: two features, {@code agent} and {@code data}, that meet in
 * views.sql's views and data's event; families that stand alone; implementations reached through their registry; one
 * owner per library.
 */
@AnalyzeClasses(packages = "com.vector.airport", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule noRoleSuffixes = noClasses()
            .should()
            .haveSimpleNameEndingWith("Manager")
            .orShould()
            .haveSimpleNameEndingWith("Helper")
            .orShould()
            .haveSimpleNameEndingWith("Util")
            .orShould()
            .haveSimpleNameEndingWith("Utils")
            .orShould()
            .haveSimpleNameEndingWith("Processor")
            .orShould()
            .haveSimpleNameEndingWith("Handler")
            .orShould()
            .haveSimpleNameEndingWith("Service")
            .orShould()
            .haveSimpleNameEndingWith("Renderer")
            .orShould()
            .haveSimpleNameEndingWith("Probe")
            .because("a class is named for what it is, not for a role (CLAUDE.md, Names)");

    @ArchTest
    static final ArchRule noNestedTypes = noClasses()
            .should()
            .beMemberClasses()
            .orShould()
            .beLocalClasses()
            .because("each class, interface, enum and record goes in its own file (CLAUDE.md, Packages)");

    @ArchTest
    static final ArchRule noLayerPackages = noClasses()
            .should()
            .resideInAnyPackage("..exception..", "..exceptions..", "..util..", "..utils..", "..helper..", "..common..")
            .because("a sub-package is a family or its impl, and an exception sits beside its thrower (CLAUDE.md,"
                    + " Packages)");

    @ArchTest
    static final ArchRule packagesHaveNoCycles = slices().matching("com.vector.airport.(**)")
            .should()
            .beFreeOfCycles()
            .because("implementations depend on the interfaces they implement, never the other way round");

    @ArchTest
    static final ArchRule dataNeverUsesTheAgent = noClasses()
            .that()
            .resideInAPackage("com.vector.airport.data..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.vector.airport.agent..")
            .because("data builds the tables for any reader: the agent, the MCP server, a SQL client");

    @ArchTest
    static final ArchRule agentNeverUsesDataInternals = noClasses()
            .that()
            .resideInAPackage("com.vector.airport.agent..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.vector.airport.data.*..")
            .because("the agent reads views.sql's views and may hear data's events; data's families are its own"
                    + " (Spring Modulith: a module's API is its base package)");

    @ArchTest
    static final ArchRule familiesNeverUseData = noClasses()
            .that()
            .resideInAnyPackage("..data.source..", "..data.format..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.vector.airport.data")
            .because("a source and a file format each stand alone; data puts them together (CLAUDE.md, Packages)");

    @ArchTest
    static final ArchRule agentNeverNamesATool = noClasses()
            .that()
            .resideInAPackage("com.vector.airport.agent")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.vector.airport.agent.tool..")
            .because("the agent takes every ToolCallbackProvider bean, so a new tool is one class in agent.tool");

    @ArchTest
    static final ArchRule toolsAreToolCallbackProviders = methods()
            .that()
            .areAnnotatedWith(Tool.class)
            .should()
            .beDeclaredInClassesThat()
            .implement(ToolCallbackProvider.class)
            .because("the agent and the MCP server see a @Tool method only through its class's ToolCallbackProvider");

    @ArchTest
    static final ArchRule downloadersSitInImpl = classes()
            .that()
            .implement(Downloader.class)
            .should()
            .resideInAPackage("com.vector.airport.data.source.impl")
            .because("implementations of a package's interface go in its impl sub-package (CLAUDE.md, Packages)");

    @ArchTest
    static final ArchRule formatsSitInImpl = classes()
            .that()
            .implement(Format.class)
            .should()
            .resideInAPackage("com.vector.airport.data.format.impl")
            .because("implementations of a package's interface go in its impl sub-package (CLAUDE.md, Packages)");

    @ArchTest
    static final ArchRule implementationsAreReachedThroughTheirRegistry = noClasses()
            .that()
            .resideOutsideOfPackage("com.vector.airport..impl")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.vector.airport..impl")
            .because("a caller gets a Downloader or a Format by name or media type (Downloaders, Formats) and makes one"
                    + " call, never branching on the type");

    @ArchTest
    static final ArchRule beansAreMadeInConfigurations = methods()
            .that()
            .areAnnotatedWith(Bean.class)
            .should()
            .beDeclaredInClassesThat()
            .areMetaAnnotatedWith(Configuration.class)
            .allowEmptyShould(true)
            .because("each feature's beans are found in its configuration class, e.g. DataConfiguration");

    @ArchTest
    static final ArchRule onlyWebUsesHttp = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(Web.class.getName())
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.net.http..", "org.springframework.web.client..")
            .because("every download goes through Web; a live API for the agent is an @HttpExchange interface");

    @ArchTest
    static final ArchRule onlyFormatsUseTika =
            onlyIn("..data.format..", "org.apache.tika..", "a file's kind and whether it holds rows belong to Formats");

    @ArchTest
    static final ArchRule onlySourcesUseJsoup =
            onlyIn("..data.source..", "org.jsoup..", "reading a site's pages is how a source downloads");

    @ArchTest
    static final ArchRule onlyTheAgentUsesSpringAi =
            onlyIn("..agent..", "org.springframework.ai..", "the LLM is the agent's; data never knows one exists");

    @ArchTest
    static final ArchRule noDriverOrProviderInCode = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.duckdb..",
                    "com.google.genai..",
                    "com.anthropic..",
                    "org.springframework.ai.google..",
                    "org.springframework.ai.anthropic..")
            .because("the database and the LLM provider are config: DB_URL, DB_DIALECT and LLM_PROVIDER (DESIGN.md)");

    private static ArchRule onlyIn(String owner, String library, String reason) {
        return noClasses()
                .that()
                .resideOutsideOfPackage(owner)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(library)
                .because(reason);
    }
}
