package org.camunda.migration.rewrite.recipes.client;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ProcessEngineToCamundaClientTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources(
            "org.camunda.migration.rewrite.recipes.AllClientRecipes")
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void variousProcessEngineFunctionsTest() {
    rewriteRun(
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariousProcessEngineFunctionsTestClass {

    @Autowired
    private RuntimeService runtimeService;

    public void variousProcessEngineFunctions(String processDefinitionKey, String signalName, String deleteReason) {

        ProcessInstance instance1 = runtimeService.startProcessInstanceByKey(processDefinitionKey);
        String processInstanceId = instance1.getProcessInstanceId();
        System.out.println(instance1.getProcessInstanceId());

        runtimeService.createSignalEvent(signalName).send();
        runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariousProcessEngineFunctionsTestClass {

    @Autowired
    private CamundaClient camundaClient;

    public void variousProcessEngineFunctions(String processDefinitionKey, String signalName, String deleteReason) {

        ProcessInstanceEvent instance1 =camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionKey)
                .latestVersion()
                .send()
                .join();
        String processInstanceId = String.valueOf(instance1.getProcessInstanceKey());
        System.out.println(String.valueOf(instance1.getProcessInstanceKey()));

        camundaClient
                .newBroadcastSignalCommand()
                .signalName(signalName)
                .send()
                .join();
        camundaClient
                .newCancelInstanceCommand(Long.valueOf(processInstanceId))
                .send()
                .join();
    }
}
"""));
  }
}
