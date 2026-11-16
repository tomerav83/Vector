package com.vector.airport;

import com.vector.airport.agent.ScriptedModel;
import com.vector.airport.data.Announcements;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole app on the committed fixture data, with a {@link ScriptedModel} for an LLM and {@link Announcements}
 * hearing each build: no network, no API key. Every class with it shares one app, which Spring's test context cache
 * builds once.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.ai.model.chat=none")
@ActiveProfiles({"us", "fixtures"})
@Import({ScriptedModel.class, Announcements.class})
public @interface FixtureDataTest {}
