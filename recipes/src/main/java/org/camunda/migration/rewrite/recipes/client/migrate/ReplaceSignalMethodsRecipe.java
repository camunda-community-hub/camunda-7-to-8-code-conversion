package org.camunda.migration.rewrite.recipes.client.migrate;

import java.util.List;

import org.camunda.migration.rewrite.recipes.utils.CamundaClientCodes;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class ReplaceSignalMethodsRecipe extends Recipe {

  /** Instantiates a new instance. */
  public ReplaceSignalMethodsRecipe() {
  }

  @Override
  public String getDisplayName() {
    return "Convert signal broadcasting methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 signal broadcasting methods with Camunda 8 client wrapper.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            new UsesType<>(RecipeConstants.Type.PROCESS_ENGINE, true),
            new UsesMethod<>(RecipeConstants.Method.GET_RUNTIME_SERVICE, true),
            Preconditions.or(
                new UsesMethod<>(
                        RecipeConstants.Method.SIGNAL_EVENT_RECEIVED
                        + RecipeConstants.Parameters.ANY,
                        true),
                new UsesMethod<>(RecipeConstants.Method.CREATE_SIGNAL_EVENT, true)));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          // engine - simple method
          final MethodMatcher engineBroadcastSignalGlobally =
              new MethodMatcher(
                      RecipeConstants.Method.SIGNAL_EVENT_RECEIVED
                      + RecipeConstants.Parameters.build("String"));
          final MethodMatcher engineBroadcastSignalToOneExecution =
              new MethodMatcher(
                      RecipeConstants.Method.SIGNAL_EVENT_RECEIVED
                      + RecipeConstants.Parameters.build("String", "String"));
          final MethodMatcher engineBroadcastSignalGloballyWithVariables =
              new MethodMatcher(
                      RecipeConstants.Method.SIGNAL_EVENT_RECEIVED
                      + RecipeConstants.Parameters.build("String", "java.util.Map"));
          final MethodMatcher engineBroadcastSignalToOneExecutionWithVariables =
              new MethodMatcher(
                      RecipeConstants.Method.SIGNAL_EVENT_RECEIVED
                      + RecipeConstants.Parameters.build("String", "String", "java.util.Map"));

          // engine - builder pattern
          final MethodMatcher engineBroadcastSignalGloballyViaBuilderSend =
              new MethodMatcher(RecipeConstants.Method.SIGNAL_BUILDER_SEND);
          final MethodMatcher engineBroadcastSignalGloballyViaBuilderSetVariables =
              new MethodMatcher(RecipeConstants.Method.SIGNAL_BUILDER_SET_VARIABLES);
          final MethodMatcher engineBroadcastSignalGloballyViaBuilderTenantId =
              new MethodMatcher(RecipeConstants.Method.SIGNAL_BUILDER_TENANT_ID);
          final MethodMatcher engineBroadcastSignalGloballyViaBuilderExecutionId =
              new MethodMatcher(RecipeConstants.Method.SIGNAL_BUILDER_EXECUTION_ID);
          final MethodMatcher engineBroadcastSignalGloballyViaBuilderCreateSignalEvent =
              new MethodMatcher(RecipeConstants.Method.CREATE_SIGNAL_EVENT);

          // wrapper - simple methods
          final JavaTemplate wrapperBroadcastSignal =
              RecipeUtils.createSimpleJavaTemplate(CamundaClientCodes.BROADCAST_SIGNAL);

          final JavaTemplate wrapperBroadcastSignalWithVariables =
              RecipeUtils.createSimpleJavaTemplate(CamundaClientCodes.BROADCAST_SIGNAL_WITH_VARIABLES);

          final JavaTemplate wrapperBroadcastSignalWithTenantId =
              RecipeUtils.createSimpleJavaTemplate(CamundaClientCodes.BROADCAST_SIGNAL_WITH_TENANT_ID);

          final JavaTemplate wrapperBroadcastSignalWithTenantIdWithVariables =
              RecipeUtils.createSimpleJavaTemplate(CamundaClientCodes.BROADCAST_SIGNAL_WITH_TENANT_ID_WITH_VARIABLES);

          /**
           * One method is replaced with another method, thus visiting J.MethodInvocations works.
           * The base identifier of the method invocation changes from engine.getRuntimeService to
           * the camundaClientWrapper. This new identifier needs to be constructed manually,
           * providing simple name and java type.
           */
          @Override
          public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
            J.Identifier camundaClientWrapper =
                RecipeUtils.createSimpleIdentifier("camundaClient", RecipeConstants.Type.CAMUNDA_CLIENT);

            // a comment is added in case the executionId was removed
            TextComment removedExecutionIdComment =
                new TextComment(
                    false,
                    " executionId was removed",
                    "\n" + elem.getPrefix().getIndent(),
                    Markers.EMPTY);

            // replace simple methods
            if (engineBroadcastSignalGlobally.matches(elem)) {
              return wrapperBroadcastSignal.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0));
            }

            if (engineBroadcastSignalToOneExecution.matches(elem)) {
              return wrapperBroadcastSignal
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0))
                  .withComments(List.of(removedExecutionIdComment));
            }

            if (engineBroadcastSignalGloballyWithVariables.matches(elem)) {
              return wrapperBroadcastSignalWithVariables.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0),
                  elem.getArguments().get(1));
            }

            if (engineBroadcastSignalToOneExecutionWithVariables.matches(elem)) {
              return wrapperBroadcastSignalWithVariables
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0),
                      elem.getArguments().get(2))
                  .withComments(List.of(removedExecutionIdComment));
            }

            // replace builder pattern
            if (engineBroadcastSignalGloballyViaBuilderSend.matches(elem)) {
              Expression signalName = null;
              Expression variableMap = null;
              Expression tenantId = null;
              Expression executionId = null;

              // loop through builder pattern select methods to extract information
              Expression current = elem;
              while (current instanceof J.MethodInvocation mi) {
                if (engineBroadcastSignalGloballyViaBuilderSetVariables.matches(mi)) {
                  variableMap = mi.getArguments().get(0);
                } else if (engineBroadcastSignalGloballyViaBuilderTenantId.matches(mi)) {
                  tenantId = mi.getArguments().get(0);
                } else if (engineBroadcastSignalGloballyViaBuilderExecutionId.matches(mi)) {
                  executionId = mi.getArguments().get(0);
                } else if (engineBroadcastSignalGloballyViaBuilderCreateSignalEvent.matches(mi)) {
                  signalName = mi.getArguments().get(0);
                }
                current = mi.getSelect();
              }

              if (executionId != null) {
                if (tenantId != null && variableMap != null) {
                  return wrapperBroadcastSignalWithTenantIdWithVariables
                      .apply(
                          getCursor(),
                          elem.getCoordinates().replace(),
                          camundaClientWrapper,
                          signalName,
                          tenantId,
                          variableMap)
                      .withComments(List.of(removedExecutionIdComment));
                } else if (tenantId != null) {
                  return wrapperBroadcastSignalWithTenantId
                      .apply(
                          getCursor(),
                          elem.getCoordinates().replace(),
                          camundaClientWrapper,
                          signalName,
                          tenantId)
                      .withComments(List.of(removedExecutionIdComment));
                } else if (variableMap != null) {
                  return wrapperBroadcastSignalWithVariables
                      .apply(
                          getCursor(),
                          elem.getCoordinates().replace(),
                          camundaClientWrapper,
                          signalName,
                          variableMap)
                      .withComments(List.of(removedExecutionIdComment));
                } else {
                  return wrapperBroadcastSignal
                      .apply(
                          getCursor(),
                          elem.getCoordinates().replace(),
                          camundaClientWrapper,
                          signalName)
                      .withComments(List.of(removedExecutionIdComment));
                }
              } else {
                if (tenantId != null && variableMap != null) {
                  return wrapperBroadcastSignalWithTenantIdWithVariables.apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      signalName,
                      tenantId,
                      variableMap);
                } else if (tenantId != null) {
                  return wrapperBroadcastSignalWithTenantId.apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      signalName,
                      tenantId);
                } else if (variableMap != null) {
                  return wrapperBroadcastSignalWithVariables.apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      signalName,
                      variableMap);
                } else {
                  return wrapperBroadcastSignal.apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      signalName);
                }
              }
            }

            // no match, continue tree traversal
            return super.visitMethodInvocation(elem, ctx);
          }
        });
  }
}
