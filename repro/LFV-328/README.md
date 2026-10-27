# ArrayUtil.hashCode(char, int, int) javadoc says the end index is inclusive, but the code and its only caller treat it as exclusive (documentation defect; the code is correct and must not change)

Bug LFV-328 · Area: util / ArrayUtil (hashCode(char, int, int) javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The javadoc of ArrayUtil.hashCode(char array, int start, int end) says it hashes the chars from start (inclusive) to end (inclusive), so hashCode({'x','y'}, 0, 0) should hash the one char 'x' and hashCode(a, 0, 1) should depend on a[1]. The loop 'for (int i = end - 1; i &gt;= start; i--)' hashes the half-open range [start, end) instead: hashCode({'x','y'}, 0, 0) is 0 (an empty range) and the ranges [0, 1] of "xy" and "xz" both hash to 120. The code is the intended side (its only caller passes an exclusive end, and every other range method in ArrayUtil documents the end as exclusive), so the fix is one word in the javadoc, 'end (exclusive)'.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/util/TestArrayUtilHashCodeRangeJavadoc.java](src/org/apache/lucene/util/TestArrayUtilHashCodeRangeJavadoc.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestArrayUtilHashCodeRangeJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
