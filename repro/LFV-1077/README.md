# While a document sits buffered in an idle in-memory segment, IndexWriter keeps every later delete and doc-values update reachable, uncounted by ramBytesUsed() and never released by the RAM buffer, so a delete-only or update-only stream grows the heap until OutOfMemoryError (default config, since 4.0.0)

Finding LFV-1077 · Area: index / IndexWriter delete queue (DeleteSlice of an idle DocumentsWriterPerThread, flush-by-RAM on pure deletes) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document to an IndexWriter with the default config and then, without a commit, NRT reopen or flush(), send a long stream of deleteDocuments(Term) or doc-values update calls, and expect the heap held by those buffered deletes to stay around the RAM buffer, because the writer applies and drops them each time they reach it, as it does when nothing is buffered. Instead the in-memory segment that holds the one document keeps a pointer into the shared delete queue that moves only when that segment indexes again or is flushed, so every later delete stays reachable after it was applied. IndexWriter.ramBytesUsed() does not count it, nothing flushes that segment, and the heap grows with every call until OutOfMemoryError.

## Expected vs actual

- Expected: Deletes pushed past the 1 MB buffer are applied and dropped, so the first delete term becomes garbage and the weak reference is cleared.
- Actual: `AssertionError: first delete term still reachable after 100,000 deletes expected null, but was:<id:x0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestIdleSegmentPinsAppliedDeletes.java](src/org/apache/lucene/index/TestIdleSegmentPinsAppliedDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIdleSegmentPinsAppliedDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
