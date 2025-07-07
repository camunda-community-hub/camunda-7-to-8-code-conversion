package org.camunda.migration.rewrite.recipes.client.migrate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

public class ReplaceStartProcessInstanceMethodsRecipe extends Recipe {

  /** Instantiates a new instance. */
  public ReplaceStartProcessInstanceMethodsRecipe() {}

  @Override
  public String getDisplayName() {
    return "Convert create process instance methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 create process instance methods with Camunda 8 client methods.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    return Preconditions.check(
        StartProcessInstanceMethodsRules.preconditions,
        new JavaIsoVisitor<ExecutionContext>() {

          /**
           * Variable declarations are visited. Types are adjusted appropriately. Initializers are
           * replaced by wrapper methods + class methods.
           */
          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = firstVar.getInitializer();

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation) {

              // join specs - possible because we don't touch the method invocations
              List<RecipeUtils.MethodInvocationReplacementSpec> commonSpecs =
                  Stream.concat(
                          StartProcessInstanceMethodsRules.simpleMethodInvocations.stream()
                              .map(spec -> (RecipeUtils.MethodInvocationReplacementSpec) spec),
                          StartProcessInstanceMethodsRules.builderMethodInvocations.stream()
                              .map(spec -> (RecipeUtils.MethodInvocationReplacementSpec) spec))
                      .toList();

              // run through prepared migration rules
              for (RecipeUtils.MethodInvocationReplacementSpec spec : commonSpecs) {

                // if match is found for the invocation, check returnTypeFqn to adjust variable
                // declaration type
                if (spec.matcher().matches(invocation)) {

                  // Create simple java template to adjust variable declaration type, but keep
                  // invocation as is
                  J.VariableDeclarations modifiedDeclarations =
                      RecipeUtils.createSimpleJavaTemplate(
                              spec.returnTypeFqn()
                                      .substring(spec.returnTypeFqn().lastIndexOf('.') + 1)
                                  + " "
                                  + originalName.getSimpleName()
                                  + " = #{any()}",
                              spec.returnTypeFqn())
                          .apply(getCursor(), declarations.getCoordinates().replace(), invocation);

                  maybeAddImport(spec.returnTypeFqn());

                  // ensure comments are added here, not on method invocation
                  getCursor().putMessage(invocation.getId().toString(), "comments added");

                  // record fqn of identifier for later uses
                  getCursor()
                      .dropParentUntil(parent -> parent instanceof J.Block)
                      .putMessage(originalName.toString(), spec.returnTypeFqn());

                  // merge comments
                  modifiedDeclarations =
                      modifiedDeclarations.withComments(
                          Stream.concat(
                                  declarations.getComments().stream(),
                                  spec.textComments().stream()
                                      .map(
                                          text ->
                                              RecipeUtils.createSimpleComment(declarations, text)))
                              .toList());

                  // visit method invocations
                  modifiedDeclarations = super.visitVariableDeclarations(modifiedDeclarations, ctx);

                  return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
                }
              }
            }

            maybeRemoveImport(RecipeConstants.Type.ENGINE_PROCESS_INSTANCE);

            return super.visitVariableDeclarations(declarations, ctx);
          }

          /** Method invocations are visited and replaced */
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {

            // visit simple method invocations
            for (RecipeUtils.MethodInvocationSimpleReplacementSpec spec :
                StartProcessInstanceMethodsRules.simpleMethodInvocations) {
              if (spec.matcher().matches(invocation)) {

                return maybeAutoFormat(
                    invocation,
                    (J.MethodInvocation)
                        RecipeUtils.applyTemplate(
                            spec.template(),
                            invocation,
                            getCursor(),
                            RecipeUtils.createArgs(invocation, spec.argumentIndexes()),
                            getCursor().getNearestMessage(invocation.getId().toString()) != null
                                ? Collections.emptyList()
                                : spec.textComments()),
                    ctx);
              }
            }

            // map builder patterns
            Map<MethodMatcher, List<RecipeUtils.MethodInvocationBuilderReplacementSpec>>
                builderSpecMap =
                    StartProcessInstanceMethodsRules.builderMethodInvocations.stream()
                        .collect(
                            Collectors.groupingBy(
                                RecipeUtils.MethodInvocationBuilderReplacementSpec::matcher));

            // loop through builder pattern groups
            for (Map.Entry<MethodMatcher, List<RecipeUtils.MethodInvocationBuilderReplacementSpec>>
                entry : builderSpecMap.entrySet()) {
              MethodMatcher matcher = entry.getKey();
              if (matcher.matches(invocation)) {
                Map<String, Expression> collectedArgs = new HashMap<>();
                Expression current = invocation.getSelect();

                // extract arguments
                while (current instanceof J.MethodInvocation mi) {
                  String name = mi.getSimpleName();
                  if (!mi.getArguments().isEmpty()
                      && !(mi.getArguments().get(0) instanceof J.Empty)) {
                    collectedArgs.put(name, mi.getArguments().get(0));
                  }
                  current = mi.getSelect();
                }

                // loop through pattern options
                for (RecipeUtils.MethodInvocationBuilderReplacementSpec spec : entry.getValue()) {
                  if (collectedArgs.keySet().equals(spec.methodNamesToExtractParameters())) {
                    Object[] args =
                        RecipeUtils.prependCamundaClient(
                            spec.extractedParametersToApply().stream()
                                .map(collectedArgs::get)
                                .toArray());

                    return maybeAutoFormat(
                        invocation,
                        (J.MethodInvocation)
                            RecipeUtils.applyTemplate(
                                spec.template(),
                                invocation,
                                getCursor(),
                                args,
                                getCursor().getNearestMessage(invocation.getId().toString()) != null
                                    ? Collections.emptyList()
                                    : spec.textComments()),
                        ctx);
                  }
                }
              }
            }

            // TODO: standardize and extract rules
            if (invocation.getSelect() != null
                && TypeUtils.isOfType(
                    invocation.getSelect().getType(),
                    JavaType.ShallowClass.build(RecipeConstants.Type.ENGINE_PROCESS_INSTANCE))
                && (invocation.getSimpleName().equals("getProcessInstanceId")
                    || invocation.getSimpleName().equals("getId"))) {

              JavaTemplate getProcessInstanceKeyToString =
                  RecipeUtils.createSimpleJavaTemplate(
                      "String.valueOf(#{any()}.getProcessInstanceKey())");

              maybeRemoveImport(RecipeConstants.Type.ENGINE_PROCESS_INSTANCE);

              J.Identifier currentSelect = (J.Identifier) invocation.getSelect();

              // get returnTypeFqn from cursor message
              String returnTypeFqn = getCursor().getNearestMessage(currentSelect.getSimpleName());

              J.Identifier newSelect =
                  RecipeUtils.createSimpleIdentifier(currentSelect.getSimpleName(), returnTypeFqn);

              return maybeAutoFormat(
                  invocation,
                  getProcessInstanceKeyToString.apply(
                      getCursor(), invocation.getCoordinates().replace(), newSelect),
                  ctx);
            }

            // no match, continue tree traversal
            return super.visitMethodInvocation(invocation, ctx);
          }
        });
  }
}
