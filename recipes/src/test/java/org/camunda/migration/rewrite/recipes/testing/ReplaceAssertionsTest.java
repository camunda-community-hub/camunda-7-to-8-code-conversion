package org.camunda.migration.rewrite.recipes.testing;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

public class ReplaceAssertionsTest implements RewriteTest {

    @Test
    void replaceSignalMethodsTest() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceAssertionsRecipe()),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;
                                import org.camunda.bpm.engine.RuntimeService;
                                import org.springframework.boot.test.context.SpringBootTest;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import org.camunda.bpm.engine.variable.Variables;
                                import org.junit.jupiter.api.Test;
                                import java.util.Map;

                                @SpringBootTest
                                public class Testcases {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private RuntimeService runtimeService;
                                    
                                    @Test
                                    void somePath() {
                                      ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                                              "sample-process-solution-process",
                                              Variables.createVariables().putValue("x", 7));
                                      assertThat(processInstance).isWaitingAt("xxx");
                                      assertThat(processInstance).isEnded().hasPassed("yyy");
                                    }
                                }
                                """,
            """
                                package org.camunda.community.migration.example;

                                import static io.camunda.assertions.CamundaAssert.assertThat;
                                import org.camunda.bpm.engine.RuntimeService;
                                import org.springframework.boot.test.context.SpringBootTest;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import org.camunda.bpm.engine.variable.Variables;
                                import org.junit.jupiter.api.Test;
                                import java.util.Map;

                                @SpringBootTest
                                public class Testcases {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private RuntimeService runtimeService;
                                    
                                    @Test
                                    void somePath() {
                                      ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                                              "sample-process-solution-process",
                                              Variables.createVariables().putValue("x", 7));
                                      assertThat(processInstance).hasActiveElements("xxx");
                                      assertThat(processInstance).isCompleted().hasCompletedElements("yyy");
                                    }
                                }
                                """));
    }
}
