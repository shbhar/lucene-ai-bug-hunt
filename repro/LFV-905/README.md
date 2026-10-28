# SimpleBindings.validate copies the whole binding path into a new set at every step and re-checks every binding once per origin, so validating a plain chain of 2000 expression bindings takes about 45 s instead of about 100 ms (cubic since 8.6.0, a regression from LUCENE-9338)

Bug LFV-905 · Area: expressions / SimpleBindings.validate (cycle and missing-reference check, CycleDetectionBindings) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a chain of named expressions with SimpleBindings.add(String, Expression), each using the previous one (x1 = x0 + 1, x2 = x1 + 1,..., x2000 = x1999 + 1), and call bindings.validate, whose javadoc says it 'Traverses the graph of bindings, checking there are no cycles or missing references'. You expect a graph check that costs about as much as resolving every binding once, as on 8.5.2, where validate of these 2000 bindings took about 100 ms. Instead, since 8.6.0 validate starts a fresh walk from every binding, re-walks everything below it, and copies the whole current path into a new LinkedHashSet at every step, so its cost grows with the cube of the chain length: about 45 s for 2000 bindings and about 575 s for 4000 (at 1000 bindings it allocates 389.9 times the bytes of resolving every binding once), although the answer is still correct.

## Expected vs actual

- Expected: You expect a graph check that costs about as much as resolving every binding once, as on 8.5.2, where validate of these 2000 bindings took about 100 ms.
- Actual: Since 8.6.0 validate starts a fresh walk from every binding, re-walks everything below it, and copies the whole current path into a new LinkedHashSet at every step, so its cost grows with the cube of the chain length: about 45 s for 2000 bindings and about 575 s for 4000 (at 1000 bindings it allocates 389.9 times the bytes of resolving every binding once), although the answer is still correct.

## Files

- [src/org/apache/lucene/expressions/TestSimpleBindingsValidateCost.java](src/org/apache/lucene/expressions/TestSimpleBindingsValidateCost.java) (JUnit test)

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
     execute --select-class org.apache.lucene.expressions.TestSimpleBindingsValidateCost
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
