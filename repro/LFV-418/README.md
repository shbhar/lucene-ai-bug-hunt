# The flush-time doc-values iterators that IndexWriter hands to a DocValuesConsumer move only their doc-ID cursor in intoBitSet, so values read afterwards belong to earlier documents (NUMERIC, BINARY, SORTED, SORTED_NUMERIC, SORTED_SET; latent: Lucene's own consumers never read values after intoBitSet on the same iterator)

Bug LFV-418 · Area: index / doc values flush (DocValuesWriter Buffered*DocValues iterators handed to DocValuesConsumer) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A custom doc-values codec receives, at flush, the iterator over a field's buffered values; it calls nextDoc, bulk-loads the next documents into a bit set with intoBitSet, and then reads the value of the current document, expecting what the documented nextDoc loop gives. Instead the five flush-time iterators (one per doc-values type) move only their doc-ID cursor in intoBitSet, so the value read belongs to an earlier document, and every later value of the field in that segment stays shifted by the same number of documents (doc 2 reports doc 0's value, doc 3 reports doc 1's). Lucene's own doc-values consumer never reads values after intoBitSet on the same iterator, so indexes written by stock Lucene are correct.

The test shows that at flush, a pass-through doc-values consumer calls `nextDoc()` and then `intoBitSet(2, bits, 0)` on the buffered NUMERIC iterator (docs 0-2 hold 10, 11, 12). The doc cursor moves to doc 2, but the value cursor stays on doc 0. The Binary, Sorted, SortedNumeric and SortedSet flush iterators have the same override.

## Expected vs actual

Expected, as after the documented `nextDoc()` loop: `doc=2 value=12`. Actual: `doc=2 value=10`.

## Files

- [src/org/apache/lucene/index/TestFlushNumericDocValuesIntoBitSet.java](src/org/apache/lucene/index/TestFlushNumericDocValuesIntoBitSet.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestFlushNumericDocValuesIntoBitSet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/dv-norms-cursor-coherence/](../../models/dv-norms-cursor-coherence/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
