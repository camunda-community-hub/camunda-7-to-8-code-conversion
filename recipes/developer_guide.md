# Developer Guide: Extending Open Rewrite Recipes For Migrating from Camunda 7 to Camunda 8

This guide is aimed at developers who are adjusting or extending the migration recipes. Such changes are likely to
affect the following aspects:

* **preconditions** of existing recipes to ensure recipes are applied
* **additional transformation rules** for existing recipes based on the abstract migration recipe
* **new recipes** based on the abstract migration recipe to cover more client code
* **bug fixes**

One important message is that it is totally OK to extend or even adjust the existing recipes, actually we encourage you to do so. This is because many Camunda 7 solutions are structured slightly differently, which is often easily adressed by adjusting recipes, but hard to capture generically. Also keep in mind, that a code refactoring is a one time effort that can be asily reviewed - the code does not need to run in production.

The Apache Open Source license this code is released under allows you to make any changes.

## Extending recipes

Let's start with an example of a change you might want to do. Assume your Java Delegates does not implement `org.camunda.bpm.engine.delegate.JavaDelegate` but extend your own
superclass `org.acme.MyJavaDelegate`. This would not be picked up by the out-of-the-box recipes.

However, you can could
extend [InjectJobWorkerRecipe.java](/recipes/src/main/java/org/camunda/migration/rewrite/recipes/delegate/prepare/InjectJobWorkerRecipe.java#L34)
where the preconditions include classes implementing the original JavaDelegate:

```java
public TreeVisitor<?, ExecutionContext> getVisitor(){

        // define preconditions
        TreeVisitor<?, ExecutionContext> check=
        Preconditions.and(
        Preconditions.not(new UsesType<>("io.camunda.client.api.response.ActivatedJob",true)),
        new UsesType<>("org.camunda.bpm.engine.delegate.DelegateExecution",true));
```

You could adjust this to

```java
public TreeVisitor<?, ExecutionContext> getVisitor(){

        // define preconditions
        TreeVisitor<?, ExecutionContext> check=
        Preconditions.and(
        Preconditions.not(new UsesType<>(RecipeConstants.Type.ACTIVATED_JOB,true)),
        Preconditions.or(
        new UsesType<>("org.camunda.bpm.engine.delegate.DelegateExecution",true),
        new UsesType<>("org.acme.MyJavaDelegate",true),
        )); 
```

Now the recipe would also pick up those delegates and add the Camunda 8 Job Worker.

You might need to do some more changes, as your `execute` method might have also been renamed or carry different parameters. We recommend not trying to perfectly extend our recipe code - but to check it out and change it on your own fork/branch. Remember that such refactoring code is only running once for the migration and can be dumped afterwards.

## Understanding Existing Recipes

In the [README](./README.md), the out-of-the-box recipes that are available for the user are presented, e.g., `AllClientPrepareRecipes`.
These recipes are themselves made up of multiple recipes that make granular changes. You can find all custom recipes in
the [source folder](./src/main/java/org/camunda/migration/rewrite/recipes), separated by type of code and transformation
phase (client, delegate, external, testing). These custom recipes are supplemented with existing OpenRewrite recipes and
composed into the aforementioned composed declarative recipes. You can inspect their composition in
the [META-INF/rewrite](./src/main/resources/META-INF/rewrite) folder. When adding a new custom or existing OpenRewrite
recipe, ensure that it is added to the correct composed recipe.

The [sharedRecipes folder](./src/main/java/org/camunda/migration/rewrite/recipes/sharedRecipes) contains two important
recipes:

* `AbstractMigrationRecipe`: extracted transformation logic for reusability purposes
* `ReplaceTypedValueAPIRecipe`: a combined recipe to transform TypedValueAPI types and method calls to JavaObjectAPI types
  and method calls

The [utils folder](./src/main/java/org/camunda/migration/rewrite/recipes/utils) contains two util classes:

* `RecipeUtils`: a collection of helper functions to create, change or apply OpenRewrite objects
* `ReplacementUtils`: the specification of rules used for the AbstractMigrationRecipe

### Prepare and Cleanup Recipes

Prepare and cleanup recipes are used to separate the code transformation into separate stages.

Here is what the prepare recipes are used for:

* all code: replace TypedValueAPI with JavaObjectAPI
* client: add an autowired CamundaClient dependencies to java classes that require it in the migrate phase
* delegate: inject a dummy job worker method beneath JavaDelegates methods
* external: inject a dummy job worker method beneath external worker methods

Here is what the cleanup recipes are used for:

* client: remove process engine, runtime service and other dependencies
* delegate: delete the JavaDelegate method and remove imports
* external: delete the external worker method and remove imports

Apart from the ReplaceTypedValueAPIRecipe, these recipes are small and very customized. The ReplaceTypedValueAPIRecipe
is a very complex recipe to transform all aspects of the TypedValueAPI for client and glue code alike.

### Abstract Migration Recipe

The [AbstractMigrationRecipe](./src/main/java/org/camunda/migration/rewrite/recipes/sharedRecipes/AbstractMigrationRecipe.java)
can be used to transform various types of java code patterns by providing transformation rules.

#### Variable Declarations, Assignments and Simple Method Invocations

Patterns:

* `TYPE IDENTIFIER = BASE_IDENTIFIER.METHOD_INVOCATION(PARAMETERS);`
* `IDENTIFIER = BASE_IDENTIFIER.METHOD_INVOCATION(PARAMETERS);`
* `BASE_IDENTIFIER.METHOD_INVOCATION(PARAMETERS);`

One set of rules is needed to transform these patterns. This specification can also be used to match constructors.

```java
record SimpleReplacementSpec(
        MethodMatcher matcher,
        JavaTemplate template,
        J.Identifier baseIdentifier,
        String returnTypeFqn,
        ReturnTypeStrategy returnTypeStrategy,
        List<NamedArg> argumentIndexes,
        List<String> textComments,
        List<String> maybeRemoveImports,
        List<String> maybeAddImports) {
}
```

The method matcher is used to find the method invocation, be it as part of a variable declaration, assignment or a
standalone method call. The abstract migration recipe finds the variable declarations and assignments where this method
invocation is the initializer, and can thus act on them.

The java template provides the code for the transformed method call. The first placeholder in this java template code is
the base identifier which is provided as the next argument of this specification.

The returnTypeFqn and returnTypeStrategy are used to set the correct type for the variable declaration type, but also
the identifier on the left hand sides of the variable declarations and assignments.

The argumentIndexes are used to match the parameters of the original method call to the placeholders found in the java
template code. If original parameters need to be ignored, they can be omitted and are thus not matched to placeholders.
If necessary, a new type can be provided to the argumentIndexes, e.g., if it is necessary to change the type of an
identifier during the transformation.

The textComments can be used to provide more information about the transformation, e.g., that an original parameter was
dropped. They are added above the transformed line, regardless of the pattern.

The lists maybeRemoveImports and maybeAddImports are currently WIP. In specific scenarios they help remove or add the
correct imports. Usually, all imports are handled dynamically anyway, and no additional information needs to be
provided.

You can find examples for this simple replacement spec in the client migrate recipes which extend the
AbstractMigrationRecipe.

#### Variable Declarations, Assignments and Builder Pattern Method Invocations

Patterns:

* `TYPE IDENTIFIER = BASE_IDENTIFIER.METHOD_INVOCATION(PARAMETER).METHOD_INVOCATION(PARAMETER);`
* `IDENTIFIER = BASE_IDENTIFIER.METHOD_INVOCATION(PARAMETER).METHOD_INVOCATION(PARAMETER);`
* `BASE_IDENTIFIER.METHOD_INVOCATION(PARAMETER).METHOD_INVOCATION(PARAMETER);`

One set of rules is needed to transform these patterns.

```java
record BuilderReplacementSpec(
        MethodMatcher matcher,
        Set<String> methodNamesToExtractParameters,
        List<String> extractedParametersToApply,
        JavaTemplate template,
        J.Identifier baseIdentifier,
        String returnTypeFqn,
        ReturnTypeStrategy returnTypeStrategy,
        List<String> textComments,
        List<String> maybeRemoveImports,
        List<String> maybeAddImports) {
}
```

The method matcher is used to match with the last method invocation of a builder pattern method invocation. Again, this
allows the AbstractMigrationRecipe to find any variable declaration, assignment or standalone builder pattern method
invocation in the code and act on it.

The methodNamesToExtractParameters is used to match a specific builder pattern with a specific set of chained method
calls, regardless of their permutation. This set is used to extract the max 1 parameter from the method call with the
provided method name.

But not all extracted parameters need or can be used for the transformed code, e.g., a businessKey might be dropped. The
list extractedParametersToApply play a similar role to the argument indexes. They define which parameters, in which
order, need to be applied to the placeholders in the following java template code.

The other fields behave in the same manner as described above.

You can find examples for this builder replacement spec in the client migrate recipes which extend the
AbstractMigrationRecipe.

#### Method Invocations based on Returned Values

Pattern:

* `BASE_IDENTIFIER.METHOD_INVOCATION();`

This base identifier is a variable that was defined in an already transformed variable declaration. Imagine a
ProcessInstance that is returned from a method invocation and saved in a variable called `instance`. Later in the
code, `instance.getProcessInstanceKey()` might be called. Of course, the name of this base identifier is flexible. Under
the hood, the AbstractMigrationRecipe takes a note of every transformed variable declaration. So, if there is a method
invocation that matches the provided specification in the same block as the transformed variable declaration, the new
type of this identifier can be set.

The specification:

```java
record ReturnReplacementSpec(MethodMatcher matcher, JavaTemplate template) {
}
```

The matcher finds the method invocation to be transformed. The java template is used to provide the transformed code.
But in this case, the base identifier is not fixed, but needs to be constructed during runtime. The name of the base
identifier is kept the same, but the type is changed according to the previously transformed variable declaration.

#### Method Invocations without Base Identifier

Pattern:

* `...METHOD_INVOCATION(PARAMETERS)...`

This pattern can occur when dealing with chained method calls, where a simple renaming is sufficient.

```java
record RenameReplacementSpec(MethodMatcher matcher, String newSimpleName) {
}
```

No java template is provided, because the complete method invocation chain needs to stay intact and not be replaced.
Instead, a simple renaming of a specific matched method invocation takes place. Thus, in this case, no base identifier
is necessary. In fact, the base identifier of this chained method call is probably transformed via a different
transformation specification.

#### Under the Hood

A recipe that extends the AbstractMigrationRecipe can also provide a skipCondition. If this skipCondition, based on
cursor information is evaluated to true, all visitors are skipped, e.g., no visitor inside a method with a specific name
can be skipped.

For each transformed variable declaration, a note is made to later on change the type of the identifier in any other
context.

Comments are automatically made in the correct scope and duplication is avoided, also by taking a note when the comments
have already been added.

### Preconditions

All recipes in this project work with preconditions. These preconditions are made up of a logical composition of checks
for used types or method calls. They are mainly used to prevent recipes from running on classes they are not intended to
run on.

If a recipe appears to not run on a class it is supposed to run on, check the preconditions first.

The AbstractMethodRecipe also expects these preconditions to be overridden.

## Examples

### SimpleReplacementSpec

```java
    ReplacementUtils.SimpleReplacementSpec(
        new MethodMatcher(
        // "startProcessInstanceByKey(String processDefinitionKey)"
        "org.camunda.bpm.engine.RuntimeService startProcessInstanceByKey(java.lang.String)"),
        RecipeUtils.createSimpleJavaTemplate(
        """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .bpmnProcessId(#{processDefinitionId:any(String)})
                .latestVersion()
                .send()
                .join();
            """),
        RecipeUtils.createSimpleIdentifier("camundaClient","io.camunda.client.CamundaClient"),
        "io.camunda.client.api.response.ProcessInstanceEvent",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("processDefinitionKey",0)),
        Collections.emptyList());
```

This specification matches the `startProcessInstanceByKey(java.lang.String)` method call of the runtime service,
regardless of it being called as `engine.getRuntimeService()...` or `runtimeService...`. It is replaced by the builder
pattern code to start a process instance with the Camunda 8 Spring SDK. The `camundaClient` placeholder is provided by
the `createSimpleIdentifier` method with a fixed name, because the CamundaClient dependency was added with a fixed name
anyway. The returnTypeFqn is `io.camunda.client.api.response.ProcessInstanceEvent` with the return type strategy
indicating that this type can be applied directly and does not need to be infered from the context.
The `processDefinitionKey` from the original method call is mapped to the `processDefinitionId` placeholder in the java
templace code.

### BuilderReplacementSpec

```java
    ReplacementUtils.BuilderReplacementSpec(
        executeMethodMatcher,
        Set.of("createProcessInstanceByKey","processDefinitionTenantId","businessKey"),
        List.of("createProcessInstanceByKey","processDefinitionTenantId"),
        RecipeUtils.createSimpleJavaTemplate(
        """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .bpmnProcessId(#{processDefinitionId:any(String)})
                .latestVersion()
                .tenantId(#{tenantId:any(String)})
                .send()
                .join();
            """),
        RecipeUtils.createSimpleIdentifier("camundaClient","io.camunda.client.CamundaClient"),
        "io.camunda.client.api.response.ProcessInstanceEvent",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(" businessKey was removed"))
```

The executeMethodMatcher matches the last method call of a builder pattern that starts a process instance with a builder
pattern in Camunda 7. This specification matches with the Camunda 7 builder patterns that contain (exactly) the chain
method calls `"createProcessInstanceByKey", "processDefinitionTenantId", "businessKey"`. But the businessKey needs to be
discarded. Thus, comparing the list of extracted parameters to apply to the java template code, it is clear that only
the `"createProcessInstanceByKey"` and `"processDefinitionTenantId"` are applied to placeholders in the code.

### ReturnReplacementSpec

```java
    ReplacementUtils.ReturnReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.runtime.Execution getProcessInstanceId()"),
        RecipeUtils.createSimpleJavaTemplate(
        "String.valueOf(#{any()}.getProcessInstanceKey())")));
```

This specification finds the method `getProcessInstanceId()` that acts on an identifier with flexible name of
type `Execution` (extention of `ProcessInstance`). The entire method call is then replaced with the java template code
while the identifier is constructed with the original name, but a new type taked from a previously transformed variable
declaration.

### RenameReplacementSpec

```java
    ReplacementUtils.RenameReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert isWaitingAt(..)"),
        "hasActiveElements"); 
```

This specification matches the method `isWaitingAt` that is part of a method call chain. The method call is then
renamed, but the entire method call chain remains intact and is not replaced in its entirety.

### Preconditions

```java
    Preconditions.or(
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService signalEventReceived(..)",true),
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService createSignalEvent(java.lang.String)",true));
```

These preconditions evaluate to true if either one of the methods is used in a java file. In this case, if a signal is
broadcast in Camunda 7, the recipe to migrate the signal broadcast methods and types is applied.


## Learnings

The AbstractMigrationRecipe uses an `JavaIsoVisitor`. This means that a node must be replaced with the same type of
node. So a method invocation cannot be replaced with a throw command. If this is necessary, a `JavaVisitor` must be
used.

In any case, 'traversing the tree' is a complex topic. Please refer to
the [OpenRewrite documentation on LST examples](https://docs.openrewrite.org/concepts-and-explanations/lst-examples) for
a basic understanding of different node types. The documentation does not list all different types of nodes that exist.

It is recommended to always visit a node with the most specific visitor possible. Do not visit statements to dig into
them to replace something. There are exceptions, e.g., if you want to filter out leading annotations from a class
definitions, it is better to visit the class definition instead of the annotations. Because, you cannot simply visit an
annotation and replace it with nothing.

If you need to replace one statement with multiple, you can find an example in the `ReplaceTypedValueAPI` recipe. In
this recipe, the inline creation of a VariableMap with values directly put into it is replaced with a multi-line
solution. Practically, this is done with a `JavaVisitor` that replaces a Variable Declaration with a block. Later on the
block is removed by visiting blocks to find unneeded blocks inside them. Like this, one statement is still replaced with
one statement.

When completing a visitor, if you return a statement without specifying `super.visitXXX`, the child nodes will not be
visited by any other visitor. If you call `super.visitXXX`, all visitors can visit the child notes. This needs to be
considered when replacing nodes that contain identifiers (parameters,...) that need to be visited, e.g., to change their
type.

Sometimes, this `super.visitXXX` can be very confusing. Especially if you try to visit a node that is a statement and
something else, and you provide visitors for both, e.g., statement and variable declaration. In this scenario, you might
expect to first visit the statement visitor which might even enable you to stop child nodes, so the variable
declaration, to be visited. But the variable declaration is not a child node, it is both a statement and a variable
declaration. Indeed, the variable declaration visitor is visited first, in this scenario.

If imports are not behaving as expected, even though maybeAdd/RemoveImport is used correctly, it is likely that there is
an identifier somewhere that still carries the old type, even if the code looks correct on the surface. Sometimes when
running unit tests, OpenRewrite makes changes and completes, but as soon as the unit test would turn green, OpenRewrite
complains about missing types and method types. This is an odd behavior, but take this error seriously, even if you
thought you just succeeded in making the unit test green. The error hints to the fact that OpenRewrite was not able to
infer the type or method type of a java template code used to transform some code. Maybe an imports on the java template
is missing, maybe the placeholder type in the java template code is not specific enough because the method is
overloaded,...

When working with method matchers, make ensure that you got the correct package. E.g., `getProcessInstanceKey()` acted
on `Execution` not `ProcessInstance`. A good way to test a method matcher is also to work with just one preconditions
with the method in question. Now, if the recipe does not run at all, you know that something is wrong with how you
describe the method matcher.

When creating your own recipe, e.g., by copying and adjusting an existing one, try to replace everything with java
templates instead of mutation existing nodes. It is better to provide a java template, its imports, etc., and enable
OpenRewrite to infer every aspect of it, then try to mutate an existing node, e.g., changing the type. Just changing the
type of a variable declaration requires a lot of work and is very error-prone.