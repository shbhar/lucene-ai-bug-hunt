# MemoryIndex drops the final position increment of each value of a multi-valued field (and the gaps after leading values with no tokens), so phrase and other position queries disagree with IndexWriter, including Lucene Monitor's single-document matching

Bug LFV-340 · Area: memory / MemoryIndex term inversion (multi-valued analyzed fields: positions and offsets carried from one value to the next); reached in-tree through monitor single-document batches · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-340.html](../../bugs/LFV-340.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document whose text field has two values, 'quick the' and 'fox', to a MemoryIndex with an analyzer that removes the stop word 'the', and expect the positions of a one-document IndexWriter index: IndexWriter keeps the hole the removed trailing 'the' leaves (TokenStream.end reports it as a final position increment), so 'fox' is at position 2 and the phrase "quick fox" does not match. MemoryIndex.storeTerms never adds that final increment, puts 'fox' at position 1 and matches the phrase; the reverse also happens (the input of upstream's TestIndexWriter#testStopwordsPosIncHole matches a phrase on IndexWriter but not on MemoryIndex), and values that produce no tokens before the first token skip the position and offset gaps ([the | quick fox] with gap 100: IndexWriter quick at position 101, offsets 4-9; MemoryIndex position 0, offsets 0-5). Lucene Monitor inherits it: a stored phrase query matches the document when it is matched alone (MemoryIndex path) but not when the same document is in a two-document batch (IndexWriter path).

## Expected vs actual

- Expected: As with `IndexWriter`: `fox` at 2 and 0 hits for `"quick fox"`; 1 hit for `"just ? test"`; `quick` at 101 (offsets 4-9); Monitor gives the same answer alone and in a batch.
- Actual: `fox` at 1 and 1 hit for `"quick fox"`; 0 hits for `"just ? test"`; `quick` at 0 (offsets 0-5); Monitor: 1 match alone, 0 in a batch of two.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexMultiValuePositions.java](src/org/apache/lucene/index/memory/TestMemoryIndexMultiValuePositions.java) (JUnit test)
- [src/org/apache/lucene/monitor/TestMonitorSingleVsBatchPositions.java](src/org/apache/lucene/monitor/TestMonitorSingleVsBatchPositions.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexMultiValuePositions \
       --select-class org.apache.lucene.monitor.TestMonitorSingleVsBatchPositions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMemoryIndexMultiValuedPositions.java](TestMemoryIndexMultiValuedPositions.java)
- [TestMonitorSingleVsBatchPositions.java](TestMonitorSingleVsBatchPositions.java)
- [TestMemoryIndexMultiValuePositions.java](TestMemoryIndexMultiValuePositions.java)
- [TestMultiValueInversionEquivalence.java](TestMultiValueInversionEquivalence.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
