# Flexible query parser: the BoostQueryNode javadoc says three times (class, constructor @param, getValue) that the boost value 'may vary from 0.0 to 1.0', but boosts above 1.0 are standard syntax and are stored unchanged: StandardQueryParser's own documented example jones^2 gives a node value of 2.0 (documentation defect; every release since 2.9.0)

Bug LFV-973 · Area: queryparser / flexible core nodes (BoostQueryNode javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the javadoc of the flexible query parser's BoostQueryNode, which says in the class doc, the constructor @param and getValue that the boost value 'may vary from 0.0 to 1.0', and expect the parser to put only such values into the node. Instead boosts above 1.0 are ordinary syntax and pass through unchanged: StandardQueryParser's own javadoc examples jones^2 and (..)^2.5 give nodes with values 2.0 and 2.5, field boosts set with setFieldsBoost (5, 10) do the same, and BoostQuery accepts any finite boost &gt;= 0. The code is right and the javadoc is wrong; nothing clamps or checks the range, and nothing should.

The test shows that the flexible query parser's `BoostQueryNode` javadoc range ("The boost value may vary from 0.0 to 1.0", also on the constructor and `getValue`) is wrong: `StandardQueryParser`'s own documented example `jones^2` gives a node value of 2.0 (a documentation defect; the code is right).

## Expected vs actual

Expected (javadoc): a value in [0.0, 1.0]. Actual: `expected a boost value in [0.0, 1.0], but getValue() = 2.0` (-ea and -da).

## Files

- [src/org/apache/lucene/queryparser/flexible/core/nodes/TestBoostQueryNodeDocumentedRange.java](src/org/apache/lucene/queryparser/flexible/core/nodes/TestBoostQueryNodeDocumentedRange.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.core.nodes.TestBoostQueryNodeDocumentedRange
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
