# IntsRefFSTEnum.seekCeil returns a term below the target (and seekFloor and seekExact answer wrongly) when a negative target int follows a prefix that is itself a term, because the seek compares it with the virtual end-of-term arc whose label is FST.END_LABEL = -1

Finding LFV-1163 · Area: util / FST (IntsRefFSTEnum seek targets vs. FSTEnum's virtual END_LABEL arc) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1163.html](../../bugs/LFV-1163.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a BYTE4 FST with [5] -&gt; 1 and [5, 3] -&gt; 2 and call seekCeil([5, Integer.MIN_VALUE]) on a fresh IntsRefFSTEnum; you expect [5, 3], the smallest term &gt;= the target in IntsRef's signed order ([5] &lt; [5, MIN_VALUE] &lt; [5, 3]). Instead it returns [5], a term below the target, with or without assertions. IntsRefFSTEnum hands the raw target int to FSTEnum's arc scan, and the node after the final arc [5] starts with a virtual end-of-term arc labelled END_LABEL = -1, so a negative int either equals that label (-1) or sorts below it and the scan stops at [5]; seekFloor and seekExact go wrong the same way.

## Expected vs actual

- Expected: `[5, 3]`, the smallest key &gt;= `[5, Integer.MIN_VALUE]`.
- Actual: `[5]`, a key below the target: `java.lang.AssertionError: expected:<[5 3]> but was:<[5]>`.

## Files

- [src/org/apache/lucene/util/fst/TestIntsRefFSTEnumNegativeTarget.java](src/org/apache/lucene/util/fst/TestIntsRefFSTEnumNegativeTarget.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestIntsRefFSTEnumNegativeTarget
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIntsRefFSTEnumNegativeTarget.java](TestIntsRefFSTEnumNegativeTarget.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
