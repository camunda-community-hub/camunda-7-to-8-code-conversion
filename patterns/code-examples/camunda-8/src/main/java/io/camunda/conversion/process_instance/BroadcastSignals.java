package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BroadcastSignalResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class BroadcastSignals {

    @Autowired
    private CamundaClient camundaClient;

    public BroadcastSignalResponse broadcastSignal(String signalName, String tenantId, Map<String, Object> variableMap) {
        return camundaClient.newBroadcastSignalCommand()
                .signalName(signalName)
                .tenantId(tenantId)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
}
