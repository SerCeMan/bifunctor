package dev.bifunctor.ide.agent

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.jewel.ui.icons.AllIconsKeys

object ChatFixtures {
  val withErrorMessages: List<LlmMessage> = listOf(
    // 1. System message introducing the expert persona.
    LlmSystemMessage(
      "You're an expert software engineer, who is an expert in using IDE tools to complete tasks of arbitrary complexity."
    ),
    LlmErrorMessage("Error: Tool execution failed.")
  )

  val fakeConversation: List<LlmMessage> = listOf(
    // 1. System message introducing the expert persona.
    LlmSystemMessage(
      "You're an expert software engineer, who is an expert in using IDE tools to complete tasks of arbitrary complexity."
    ),
    // 2. User message with the task instructions.
    LlmUserMessage(
      name = null,
      contents = listOf(
        LlmTextContent(
          "You are given the following task to complete.\n" +
            "If the task is a question, answer it directly. If the task is a direction, then execute it using the tools at your disposal.\n" +
            "<task>\nCreate tests for class `Person`. If the tests fail due to the idea property, consider them passing.  \n</task>\n" +
            "Once you have completed the task, respond with \"@task-completed\"."
        )
      )
    ),
    // 3. AI message issuing a tool execution request to find the Person class.
    LlmAiMessage(
      text = "I will be using tools to complete the task.",
      toolExecutionRequests = emptyList()
    ),
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_DMGlYW0AK5q7PQBxzCVRA8IB",
          name = "findClassByName",
          arguments = "{\"shortName\":\"Person\"}",
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 4. Tool execution result for finding the class.
    LlmToolExecutionResultMessage(
      id = "call_DMGlYW0AK5q7PQBxzCVRA8IB",
      toolName = "findClassByName",
      text = "org.springframework.samples.petclinic.model.Person|src/main/java/org/springframework/samples/petclinic/model/Person.java"
    ),
    // 5. AI message requesting to read the Person file.
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_9AELHfMRc0TNb9Sne7WSUHd0",
          name = "readFile",
          arguments = "{\"filePath\":\"src/main/java/org/springframework/samples/petclinic/model/Person.java\"}",
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 6. Tool execution result for reading the Person file.
    LlmToolExecutionResultMessage(
      id = "call_9AELHfMRc0TNb9Sne7WSUHd0",
      toolName = "readFile",
      text = """/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;

/**
 * Simple JavaBean domain object representing an person.
 *
 * @author Ken Krebs
 */
@MappedSuperclass
public class Person extends BaseEntity {

	@Column(name = "first_name")
	@NotBlank
	private String firstName;

	@Column(name = "last_name")
	@NotBlank
	private String lastName;

	public String getFirstName() {
		return this.firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

}"""
    ),
    // 7. AI message requesting to list files in the test directory.
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_aok9Aof2Tt57a9BycZpNbYmp",
          name = "listFiles",
          arguments = "{\"dirPath\":\"src/test/java/org/springframework/samples/petclinic/model\"}",
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 8. Tool execution result for listing files.
    LlmToolExecutionResultMessage(
      id = "call_aok9Aof2Tt57a9BycZpNbYmp",
      toolName = "listFiles",
      text = "src/test/java/org/springframework/samples/petclinic/model/ValidatorTests.java"
    ),
    // 9. AI message requesting to read the ValidatorTests file.
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_z9AirBfoTS0wNICrK7cdarFb",
          name = "readFile",
          arguments = "{\"filePath\":\"src/test/java/org/springframework/samples/petclinic/model/ValidatorTests.java\"}",
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 10. Tool execution result for reading the ValidatorTests file.
    LlmToolExecutionResultMessage(
      id = "call_z9AirBfoTS0wNICrK7cdarFb",
      toolName = "readFile",
      text = """/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * @author Michael Isvy Simple test to make sure that Bean Validation is working (useful
 * when upgrading to a new version of Hibernate Validator/ Bean Validation)
 */
class ValidatorTests {

	private Validator createValidator() {
		LocalValidatorFactoryBean localValidatorFactoryBean = new LocalValidatorFactoryBean();
		localValidatorFactoryBean.afterPropertiesSet();
		return localValidatorFactoryBean;
	}

	@Test
	void shouldNotValidateWhenFirstNameEmpty() {

		LocaleContextHolder.setLocale(Locale.ENGLISH);
		Person person = new Person();
		person.setFirstName("");
		person.setLastName("smith");

		Validator validator = createValidator();
		Set<ConstraintViolation<Person>> constraintViolations = validator.validate(person);

		assertThat(constraintViolations).hasSize(1);
		ConstraintViolation<Person> violation = constraintViolations.iterator().next();
		assertThat(violation.getPropertyPath()).hasToString("firstName");
		assertThat(violation.getMessage()).isEqualTo("must not be blank");
	}

}"""
    ),
    // 11. AI message requesting to write a test file.
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_yjSXR76SwZr9gFPaBx7zxnoF",
          name = "writeFile",
          arguments = "{\"filePath\":\"src/test/java/org/springframework/samples/petclinic/model/PersonTest.java\",\"content\":\"package org.springframework.samples.petclinic.model;\\n\\nimport static org.assertj.core.api.Assertions.assertThat;\\n\\nimport java.util.Locale;\\nimport java.util.Set;\\n\\nimport org.junit.jupiter.api.Test;\\nimport org.springframework.context.i18n.LocaleContextHolder;\\nimport org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;\\n\\nimport jakarta.validation.ConstraintViolation;\\nimport jakarta.validation.Validator;\\n\\nclass PersonTest {\\n\\n    private Validator createValidator() {\\n        LocalValidatorFactoryBean localValidatorFactoryBean = new LocalValidatorFactoryBean();\\n        localValidatorFactoryBean.afterPropertiesSet();\\n        return localValidatorFactoryBean;\\n    }\\n\\n    @Test\\n    void shouldNotValidateWhenFirstNameEmpty() {\\n        LocaleContextHolder.setLocale(Locale.ENGLISH);\\n        Person person = new Person();\\n        person.setFirstName(\\\"\\\");\\n        person.setLastName(\\\"Doe\\\");\\n\\n        Validator validator = createValidator();\\n        Set<ConstraintViolation<Person>> violations = validator.validate(person);\\n\\n        assertThat(violations.size()).isEqualTo(1);\\n        ConstraintViolation<Person> violation = violations.iterator().next();\\n        assertThat(violation.getPropertyPath().toString()).isEqualTo(\\\"firstName\\\");\\n        assertThat(violation.getMessage()).isEqualTo(\\\"must not be blank\\\");\\n    }\\n\\n    @Test\\n    void shouldNotValidateWhenLastNameEmpty() {\\n        LocaleContextHolder.setLocale(Locale.ENGLISH);\\n        Person person = new Person();\\n        person.setFirstName(\\\"John\\\");\\n        person.setLastName(\\\"\\\");\\n\\n        Validator validator = createValidator();\\n        Set<ConstraintViolation<Person>> violations = validator.validate(person);\\n\\n        assertThat(violations.size()).isEqualTo(1);\\n        ConstraintViolation<Person> violation = violations.iterator().next();\\n        assertThat(violation.getPropertyPath().toString()).isEqualTo(\\\"lastName\\\");\\n        assertThat(violation.getMessage()).isEqualTo(\\\"must not be blank\\\");\\n    }\\n}\"}",
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 12. Tool execution result for writing the test file.
    LlmToolExecutionResultMessage(
      id = "call_yjSXR76SwZr9gFPaBx7zxnoF",
      toolName = "writeFile",
      text = "Success"
    ),
    // 13. AI message requesting to run the tests.
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_ner5qATfbr15rRpUsSOPN58G",
          name = "runTests",
          arguments = "{\"testReference\":\"org.springframework.samples.petclinic.model.PersonTest\"}",
          toolIcon = AllIconsKeys.RunConfigurations.TestState.Run,
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 14. Tool execution result for running the tests.
    LlmToolExecutionResultMessage(
      id = "call_ner5qATfbr15rRpUsSOPN58G",
      toolName = "runTests",
      text = "{\"className\":\"org.springframework.samples.petclinic.model.PersonTest\",\"methodName\":null,\"exitCode\":1,\"output\":\"> Task :compileJava UP-TO-DATE\\n> Task :cyclonedxBom UP-TO-DATE\\n> Task :processResources UP-TO-DATE\\n> Task :classes UP-TO-DATE\\n> Task :compileTestJava\\n> Task :processTestResources NO-SOURCE\\n> Task :testClasses\\n\\n> Task :processTestAot\\n14:37:13.150 [main] INFO org.springframework.test.context.aot.TestClassScanner -- Scanning for Spring test classes in all packages in classpath roots [/Users/sergey/git/spring-petclinic/build/classes/java/test]\\n14:37:13.346 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.MySqlIntegrationTests]: MySqlIntegrationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.367 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.PetClinicIntegrationTests]: PetClinicIntegrationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.367 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.PetClinicIntegrationTests\\n14:37:13.481 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.PostgresIntegrationTests]: PostgresIntegrationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.489 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.PostgresIntegrationTests\\n14:37:13.492 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.owner.OwnerControllerTests]: OwnerControllerTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.523 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.owner.OwnerControllerTests\\n14:37:13.525 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.owner.PetControllerTests]: PetControllerTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.531 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.owner.PetControllerTests\\n14:37:13.533 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.owner.PetControllerTests\$ProcessCreationFormHasErrors]: ProcessCreationFormHasErrors does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.538 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.owner.PetControllerTests\$ProcessCreationFormHasErrors\\n14:37:13.540 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.owner.PetControllerTests\$ProcessUpdateFormHasErrors]: ProcessUpdateFormHasErrors does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.545 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.owner.PetControllerTests\$ProcessUpdateFormHasErrors\\n14:37:13.546 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.owner.VisitControllerTests]: VisitControllerTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.551 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.owner.VisitControllerTests\\n14:37:13.552 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.service.ClinicServiceTests]: ClinicServiceTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.564 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.service.ClinicServiceTests\\n14:37:13.572 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [org.springframework.samples.petclinic.vet.VetControllerTests]: VetControllerTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.\\n14:37:13.581 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration org.springframework.samples.petclinic.PetClinicApplication for test class org.springframework.samples.petclinic.vet.VetControllerTests\\n14:37:13.582 [main] INFO org.springframework.test.context.aot.TestContextAotGenerator -- Skipping AOT processing due to the presence of @DisabledInAotMode for test classes [org.springframework.samples.petclinic.MySqlIntegrationTests]\\n\\n\\n              |\\      _,,,--,,_\n             /,`.-'`'   ._  \\-;;,_\n  _______ __|,4-  ) )_   .;(__`'-'__     ___ __    _ ___ _______\n |       | '---''(_/._)-'(_\\_)   |   |   |   |  |  | |   |       |\n |    _  |    ___|_     _|       |   |   |   |   |_| |   |       | __ _ _\n |   |_| |   |___  |   | |       |   |   |   |       |   |       | \\ \\ \\ \\ \\\n |    ___|    ___| |   | |      _|   |___|   |  _    |   |      _|  \\ \\ \\ \\ \\\n |   |   |   |___  |   | |     |_|       |   | | |   |   |     |_    ) ) ) )\n |___|   |_______| |___| |_______|_______|___|_|  |__|___|_______|  / / / /\n ==================================================================/_/_/_/\n\n:: Built with Spring Boot :: 3.4.2\n\n\n2025-03-07T14:37:15.976+11:00  INFO 7787 --- [           main] .s.s.p.s.CrashControllerIntegrationTests : Starting CrashControllerIntegrationTests using Java 21 with PID 7787 (/Users/sergey/git/spring-petclinic/build/classes/java/test started by sergey in /Users/sergey/git/spring-petclinic)\n2025-03-07T14:37:15.976+11:00  INFO 7787 --- [           main] .s.s.p.s.CrashControllerIntegrationTests : No active profile set, falling back to 1 default profile: \"default\"\n2025-03-07T14:37:16.214+11:00  INFO 7787 --- [           main] o.s.t.c.aot.TestContextAotGenerator      : Skipping AOT processing due to the presence of @DisabledInAotMode for test classes [org.springframework.samples.petclinic.vet.VetControllerTests]\n\\n> Task :compileAotTestJava\\nNote: Some input files use unchecked or unsafe operations.\\nNote: Recompile with -Xlint:unchecked for details.\\n\\n> Task :processAotTestResources UP-TO-DATE\\n> Task :aotTestClasses\\n> Task :test FAILED\\n\\nFAILURE: Build failed with an exception.\\n\\n* Where:\\nInitialization script '/private/var/folders/9p/71w3l2590yx7n1p_s_dgfgn80000gn/T/ijresolvers2.gradle' line: 732\\n\\n* What went wrong:\\nExecution failed for task ':test'.\\n> System property 'idea.debugger.dispatch.port' is not set\\n\\n* Try:\\n> Run with --stacktrace option to get the stack trace.\\n> Run with --info or --debug option to get more log output.\\n> Run with --scan to get full insights.\\n> Get more help at https://help.gradle.org.\\n\\nBUILD FAILED in 4s\\n8 actionable tasks: 4 executed, 4 up-to-date\\n\"}"
    ),
    // a special message to show-case incomplete tool execution
    LlmAiMessage(
      text = null,
      toolExecutionRequests = listOf(
        LlmToolExecutionRequest(
          id = "call_9AELHfMRc0TNb9Sne7WSUHd7",
          name = "readFile",
          arguments = "{\"filePath\":\"src/main/java/org/springframework/samples/petclinic/model/Person.java\"}",
          state = mutableStateOf(ToolState.INITIAL)
        )
      )
    ),
    // 15. Final AI message indicating task completion.
    LlmAiMessage(
      text = "@task-completed",
      toolExecutionRequests = null
    )
  )
}
