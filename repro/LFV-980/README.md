# TermInSetQuery's class javadoc example does not compile: it calls the varargs constructor TermInSetQuery(String, BytesRef...), removed in 10.0 (#12837), and new BooleanQuery / bq.add(...), removed in 6.0 (LUCENE-6570); the equal-scores claim it illustrates still holds (documentation defect)

Bug LFV-980 · Area: core / search (TermInSetQuery class javadoc example) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the TermInSetQuery javadoc, which says that in its example q1 and q2 'would yield the same scores', and copy the example into your code. It does not compile on any release: q1 calls new TermInSetQuery("field", new BytesRef("foo"), new BytesRef("bar")), a varargs constructor removed in 10.0, and q2 is built with new BooleanQuery and bq.add(...), which have not existed since BooleanQuery became immutable, with a Builder, in 6.0. Written with the current API (a List of terms and BooleanQuery.Builder) the example compiles and the two queries do return the same hits and scores, so only the code in the javadoc is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestTermInSetQueryJavadocExample.java](src/org/apache/lucene/search/TestTermInSetQueryJavadocExample.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestTermInSetQueryJavadocExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/](../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
