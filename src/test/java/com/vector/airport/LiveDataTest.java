package com.vector.airport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The whole app on the real files of the locked periods, downloaded into data/cache when missing, without an LLM.
 * Tagged {@code live}, so only {@code mvn test -Dgroups=live -DexcludedGroups=none} (the live-data workflow) runs it:
 * it needs the network, and it catches upstream changes such as a revised file or a renamed column.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("live")
@SpringBootTest(properties = "spring.ai.model.chat=none")
public @interface LiveDataTest {}
