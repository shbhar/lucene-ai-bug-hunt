# MathUtil.asinh, acosh and atanh use the textbook log formulas without range reduction: asinh and acosh return Infinity for every finite |x| &gt;= 2^512, asinh and atanh return 0 for tiny nonzero x, and acosh of a large negative x returns an infinity instead of the documented NaN, also through the expression functions asinh, acosh and atanh

Bug LFV-327 · Area: util / MathUtil inverse hyperbolic functions (asinh, acosh, atanh), reached through the expressions module's default functions asinh, acosh and atanh · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call MathUtil.asinh(1e200), or evaluate the expression asinh(x) for a document whose x is 1e200, and expect about 461.21, because the inverse hyperbolic sine of a finite number is finite (at most about 710.5). Instead you get Infinity, because the code computes log(sqrt(x*x + 1) + x) and x*x overflows for every |x| &gt;= 2^512; acosh does the same, asinh(1e-17) and atanh(1e-17) return 0 instead of 1e-17 because 1 + x*x (or (1+x)/(1-x)) rounds to exactly 1, and acosh(-1e8) returns -Infinity (acosh(-1e200) +Infinity) although its javadoc says that any argument below 1 gives NaN.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get Infinity, because the code computes log(sqrt(x*x + 1) + x) and x*x overflows for every |x| &gt;= 2^512; acosh does the same, asinh(1e-17) and atanh(1e-17) return 0 instead of 1e-17 because 1 + x*x (or (1+x)/(1-x)) rounds to exactly 1, and acosh(-1e8) returns -Infinity (acosh(-1e200) +Infinity) although its javadoc says that any argument below 1 gives NaN.

## Files

- [src/org/apache/lucene/util/TestMathUtilInverseHyperbolicRange.java](src/org/apache/lucene/util/TestMathUtilInverseHyperbolicRange.java) (JUnit test)
- [src/org/apache/lucene/expressions/js/TestExpressionInverseHyperbolicRange.java](src/org/apache/lucene/expressions/js/TestExpressionInverseHyperbolicRange.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestMathUtilInverseHyperbolicRange \
       --select-class org.apache.lucene.expressions.js.TestExpressionInverseHyperbolicRange
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/numeric-string-version-utils/](../../models/numeric-string-version-utils/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
