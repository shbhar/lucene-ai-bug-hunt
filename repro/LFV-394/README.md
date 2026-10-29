# LogMergePolicy.findMerges wraps 'start + mergeFactor' when mergeFactor is Integer.MAX_VALUE (or within about the segment count of it), which setMergeFactor accepts, so commit and flush throw once two size levels exist (every release since 2.3.0)

Bug LFV-394 · Area: index / merge policy (LogMergePolicy.findMerges level window; LogDocMergePolicy, LogByteSizeMergePolicy) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-394.html](../../bugs/LFV-394.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure LogDocMergePolicy (or LogByteSizeMergePolicy) with setMergeFactor(Integer.MAX_VALUE), which the setter accepts (it rejects only values below 2, and its javadoc recommends large values for batch indexing), bulk-load one large segment, add one document and commit, and expect the commit to succeed with no merge, as it does with Integer.MAX_VALUE - 1. Instead findMerges computes the end of each merge window as 'int end = start + mergeFactor', which wraps negative for every size level that does not start at segment 0, and its minimum-size packing step then calls levels.get(-2147483648), so commit throws IndexOutOfBoundsException (OutOfMemoryError 'Requested array size exceeds VM limit' before 10.2.0). Every later commit and flush of new documents fails the same way until the factor is lowered; on main a 20,000,000-document segment followed by a 1-document flush is enough.

The test shows that LogDocMergePolicy.findMerges with mergeFactor = Integer.MAX_VALUE (accepted by setMergeFactor) throws on two segments of 20,000,000 and 1 docs, because 'start + mergeFactor' wraps negative for the second size level.

## Expected vs actual

Expected: null (no merge, as with Integer.MAX_VALUE - 1). Actual (-ea and -da): IndexOutOfBoundsException: Index -2147483648 out of bounds for length 2 at LogMergePolicy.findMerges:636.

## Files

- [src/org/apache/lucene/index/TestLogMergePolicyMaxMergeFactor.java](src/org/apache/lucene/index/TestLogMergePolicyMaxMergeFactor.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestLogMergePolicyMaxMergeFactor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/overflow-at-scale-2/](../../models/overflow-at-scale-2/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
