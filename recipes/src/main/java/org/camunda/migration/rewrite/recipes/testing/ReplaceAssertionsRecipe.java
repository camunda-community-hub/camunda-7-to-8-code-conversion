package org.camunda.migration.rewrite.recipes.testing;

import java.util.Collections;
import java.util.List;

import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils.BuilderReplacementSpec;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils.ReturnReplacementSpec;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class ReplaceAssertionsRecipe extends AbstractMigrationRecipe {

  @Override
  public String getDisplayName() {
    return "Convert test assertions";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 test assertions with Camunda 8 CPT assertions.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    //return new UsesMethod<>("org.camunda.bpm.engine.test.assertions.ProcessEngineTests assertThat(..)", true);
    return new UsesMethod<>("org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert isWaitingAt(..)");    
  }
  
  // assertThat(pi).isCompleted().hasActiveElements();

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
      return List.of(
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher("org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert isWaitingAt(..)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "assertThat(#{processInstance:any(java.lang.Object)}).hasActiveElements(#{elementId:any(String)})",
                  "io.camunda.assertions.CamundaAssert.assertThat"),
              null, // RecipeUtils.createSimpleIdentifier("CamundaAssert", "io.camunda.assertions.CamundaAssert"),
              "io.camunda.process.test.api.assertions.ProcessInstanceAssert",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg("processInstance", 0),
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg("elementId", 1)
              ),
              List.of(
                  )
          )
      );
  }

  @Override
  protected List<BuilderReplacementSpec> builderMethodInvocations() {
    return Collections.emptyList(); // not used
  }

  @Override
  protected List<ReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList(); // not used
  }


}
