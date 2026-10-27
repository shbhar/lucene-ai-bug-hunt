# IndexWriter.deleteDocuments(Term...) and deleteDocuments(Query...) keep the caller's array without copying it, so documents still buffered in RAM are deleted by whatever the array holds when the per-thread writer later applies the delete (at its next addDocument or flush): a caller that refills or clears its array after the call returned keeps documents it deleted and deletes documents no call named, silently and also after commit (since 4.0.0)

Bug LFV-644 · Area: index / IndexWriter deletes (deleteDocuments, DocumentsWriterDeleteQueue array nodes, per-thread delete slices) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-644.html](../../bugs/LFV-644.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You pass a Term or Query to IndexWriter.deleteDocuments, the call returns its sequence number, and you then reuse the array, for example refill it with the next batch of ids; the delete should mean the terms or queries the array held at the call, as it already does for documents in flushed segments. IndexWriter hands the array itself to its delete queue, and each per-thread writer reads it only later, at its next addDocument or flush, so documents still buffered in RAM are deleted by the array's new contents. In the repro, deleting {flushed1, buffered1} and then refilling the same array with {flushed2, buffered2} and deleting again leaves buffered1 alive after commit, and reassigning the slot of a one-element Query after the call keeps 'delete-me' and deletes 'keep', which no delete call named; no exception is thrown.

## Expected vs actual

- Expected: `[keep]` is the only live document after the commit in all 3 tests.
- Actual: `[d0, d1, d2, d3, keep]`, `[buffered1, keep]` and `[delete-me]`: 3 of 3 tests fail, with no exception from Lucene.

## Files

- [src/org/apache/lucene/index/TestDeleteDocumentsRetainsCallerArray.java](src/org/apache/lucene/index/TestDeleteDocumentsRetainsCallerArray.java) (JUnit test)
- [src/legacy/DeleteArrayReuseMain.java](src/legacy/DeleteArrayReuseMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.index.TestDeleteDocumentsRetainsCallerArray

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" legacy.DeleteArrayReuseMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDeleteDocumentsArrayReuse.java](TestDeleteDocumentsArrayReuse.java)
- [DeleteArrayReuse.java](DeleteArrayReuse.java)
- [TestDeleteDocumentsRetainsCallerArray.java](TestDeleteDocumentsRetainsCallerArray.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
