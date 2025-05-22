package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HandleUserTasks {

    @Autowired
    private ProcessEngine engine;

    public List<Task> searchUserTasksByBPMNModelIdentifier(String processDefinitionId) {
        return engine.getTaskService().createTaskQuery()
                .processDefinitionId(processDefinitionId)
                .list();
    }

    public void claimUserTask(String taskId, String userId) {
        engine.getTaskService().claim(taskId, userId);
    }

    public void completeUserTask(String taskId, Map<String, Object> variableMap) {
        engine.getTaskService().complete(taskId, variableMap);
    }

    public Object getVariableFromTaskJavaObjectAPI(String taskId, String variableName) {
        return engine.getTaskService().getVariable(taskId, variableName);
    }

    public TypedValue getVariableFromTaskTypedValueApi(String taskId, String variableName) {
        return engine.getTaskService().getVariableTyped(taskId, variableName);
    }
}
