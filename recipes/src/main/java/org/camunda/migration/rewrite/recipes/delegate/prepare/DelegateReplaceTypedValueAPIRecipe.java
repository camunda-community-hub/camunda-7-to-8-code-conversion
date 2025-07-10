package org.camunda.migration.rewrite.recipes.delegate.prepare;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

public class DelegateReplaceTypedValueAPIRecipe extends Recipe {

  /** Instantiates a new instance. */
  public DelegateReplaceTypedValueAPIRecipe() {}

  @Override
  public String getDisplayName() {
    return "Replaces delegate specific typed value methods";
  }

  @Override
  public String getDescription() {
    return "Replaces delegate specific typed value methods.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesMethod<ExecutionContext>(
                "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)"),
            new UsesMethod<ExecutionContext>(
                "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)"));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = firstVar.getInitializer();

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation) {

              if ((new MethodMatcher(
                              "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)")
                          .matches(invocation)
                      || new MethodMatcher(
                              "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)")
                          .matches(invocation))
                  && TypeUtils.isOfType(
                      originalName.getType(),
                      JavaType.buildType("org.camunda.bpm.engine.variable.value.TypedValue"))) {

                // get modifiers
                List<J.Modifier> modifiers = declarations.getModifiers();

                // Create simple java template to adjust variable declaration type, but keep
                // invocation as is
                J.VariableDeclarations modifiedDeclarations =
                    RecipeUtils.createSimpleJavaTemplate(
                            (modifiers == null || modifiers.isEmpty()
                                    ? ""
                                    : modifiers.stream()
                                        .map(J.Modifier::toString)
                                        .collect(Collectors.joining(" ", "", " ")))
                                + "Object "
                                + originalName.getSimpleName()
                                + " = #{any()}",
                            "java.lang.Object")
                        .apply(getCursor(), declarations.getCoordinates().replace(), invocation);

                maybeAddImport("java.lang.Object");

                // record fqn of identifier for later uses
                getCursor()
                    .dropParentUntil(parent -> parent instanceof J.Block)
                    .putMessage(originalName.toString(), "java.lang.Object");

                // merge comments
                modifiedDeclarations =
                    modifiedDeclarations.withComments(
                        Stream.concat(
                                declarations.getComments().stream(),
                                Stream.of(
                                    RecipeUtils.createSimpleComment(
                                        declarations, " type changed to java.lang.Object")))
                            .toList());

                // visit method invocations
                modifiedDeclarations =
                    (J.VariableDeclarations)
                        super.visitVariableDeclarations(modifiedDeclarations, ctx);

                return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
              }
            }
            return (J.VariableDeclarations) super.visitVariableDeclarations(declarations, ctx);
          }

          @Override
          public J visitMethodInvocation(J.MethodInvocation invoc, ExecutionContext ctx) {

            if (invoc.getSimpleName().equals("getVariableTyped")
                || invoc.getSimpleName().equals("getVariableLocalTyped")) {
              J.Identifier newIdent =
                  RecipeUtils.createSimpleIdentifier("getVariable", "java.lang.String");
              return invoc.withName(newIdent);
            }

            return super.visitMethodInvocation(invoc, ctx);
          }

          @Override
          public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {

            return (J.Identifier) RecipeUtils.updateType(getCursor(), identifier);
          }
        });
  }
}
