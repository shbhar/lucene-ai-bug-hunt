# FSTCompiler throws ArrayIndexOutOfBoundsException on BYTE4 nodes whose labels span 2^31-8 or more

Bug LFV-067 · Area: util / FST · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a BYTE4 FST with arbitrary int labels so that one node's labels span 2^31-8 or more. You expect the FST to build, and direct addressing to be off with a factor of -1. Instead the presence-byte count wraps and the build throws ArrayIndexOutOfBoundsException, even with factor -1.

The test shows that `FSTCompiler.compile()` on a BYTE4 FST whose root node has the labels {0, 1, 2, 3, Integer.MAX_VALUE - 1} (default builder) chooses direct addressing because the presence-byte count `(labelRange + 7) >> 3` wraps negative, and then crashes.

## Expected vs actual

Expected: the FST builds. Actual: `ArrayIndexOutOfBoundsException: arraycopy: destination index -268435436 out of bounds for byte[11]` from `writeNodeForDirectAddressingOrContinuous`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/util/fst/TestFSTByte4WideLabelSpan.java](src/org/apache/lucene/util/fst/TestFSTByte4WideLabelSpan.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestFSTByte4WideLabelSpan
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-fst-verification/](../../models/study-fst-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
