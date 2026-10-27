# ExpressionRescorer.explain evaluates the expression with _score = 0 (the value of SortRescorer's placeholder explanation) instead of the first-pass score, so the explanation shows the wrong _score and expression values; the rescored ranking is correct

Bug LFV-174 · Area: expressions / ExpressionRescorer explain (Expression.getRescorer, ExpressionValueSource.explain over SortRescorer) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You rescore first-pass hits with an expression that reads _score, for example '_score * 100 + popularity' through Expression.getRescorer, and ask the rescorer to explain a hit. You expect what the ExpressionRescorer javadoc promises, an explanation 'showing the value of each variable': _score equal to the first-pass score the rescore used, and the expression value computed from it (107.0 for a first-pass score of 1.0 and popularity 7). Instead, since 7.0.0, ExpressionRescorer hands SortRescorer's explanation, whose value is always 0, to the expression as the score explanation, so the explanation reads '7.0 = _score * 100 + popularity' with a _score detail of 0.0, while the rescore itself (order, sort values, scores) is correct.

## Expected vs actual

- Expected: You expect what the ExpressionRescorer javadoc promises, an explanation 'showing the value of each variable': _score equal to the first-pass score the rescore used, and the expression value computed from it (107.0 for a first-pass score of 1.0 and popularity 7).
- Actual: Since 7.0.0, ExpressionRescorer hands SortRescorer's explanation, whose value is always 0, to the expression as the score explanation, so the explanation reads '7.0 = _score * 100 + popularity' with a _score detail of 0.0, while the rescore itself (order, sort values, scores) is correct.

## Files

- [src/org/apache/lucene/expressions/TestExpressionRescorerExplainScore.java](src/org/apache/lucene/expressions/TestExpressionRescorerExplainScore.java) (JUnit test)
- [src/ExpressionRescorerExplainScoreMain.java](src/ExpressionRescorerExplainScoreMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.expressions.TestExpressionRescorerExplainScore

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ExpressionRescorerExplainScoreMain
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
