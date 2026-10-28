# SimpleBindings rebuilds an Expression binding on every reference, so building the source of bindings that share sub-expressions creates one ExpressionValueSource per path (exponential in the depth of sharing)

Bug LFV-899 · Area: expressions / SimpleBindings binding resolution (add(String, Expression), getDoubleValuesSource) and the ExpressionValueSource constructor · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You bind named expressions that use each other with SimpleBindings.add(String, Expression), which the class javadoc documents ('This can be used to reference expressions from other expressions'), and some of them share a sub-expression, for example X1 = L0 + R0 with L0 = X0 + 1 and R0 = X0 * 2, repeated in a chain. You expect getDoubleValuesSource to build each binding a bounded number of times, as building the same graph eagerly with add(String, DoubleValuesSource) does. Instead every reference re-applies expression::getDoubleValuesSource, so the shared graph is expanded into a tree with one ExpressionValueSource per path: 3*(2^d - 1) objects for a chain of d diamonds (196,605 for 49 bindings), and building the source of 70 or more such bindings exhausts a 1 GB heap before any document is read, while the computed values stay correct.

## Expected vs actual

- Expected: You expect getDoubleValuesSource to build each binding a bounded number of times, as building the same graph eagerly with add(String, DoubleValuesSource) does.
- Actual: Every reference re-applies expression::getDoubleValuesSource, so the shared graph is expanded into a tree with one ExpressionValueSource per path: 3*(2^d - 1) objects for a chain of d diamonds (196,605 for 49 bindings), and building the source of 70 or more such bindings exhausts a 1 GB heap before any document is read, while the computed values stay correct.

## Files

- [src/org/apache/lucene/expressions/TestSimpleBindingsSharedSubExpressionCost.java](src/org/apache/lucene/expressions/TestSimpleBindingsSharedSubExpressionCost.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/expressions/build/libs/lucene-expressions-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.expressions.TestSimpleBindingsSharedSubExpressionCost
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
