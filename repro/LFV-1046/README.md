# A deleteDocuments or updateDocument(s) call with a term over 32,766 bytes is rejected but stays in IndexWriter's shared delete queue, so every later add and delete and the next commit, NRT reopen or close fail until rollback (regression in 9.9.0; up to 9.8.0 such a delete was a no-op)

Bug LFV-1046 · Area: index / IndexWriter buffered delete terms (DocumentsWriterDeleteQueue.DeleteSlice.apply, BufferedUpdates.DeletedTerms) · Priority: medium · Impact: index unusable · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document, call deleteDocuments(new Term("id", t)) with t longer than 32,766 bytes, and commit. No indexed term can be that long, so you expect the delete to match nothing (as it did up to 9.8.0) or to be rejected, and the commit to contain the document. Instead the delete throws BytesRefHash.MaxBytesLengthExceededException after it was already queued and is never removed from the queue, so every later addDocument and delete throws the same exception and commit fails (with assertions off an IllegalStateException 'Can't close queue unless all changes are applied', with -ea an AssertionError that closes the writer); the only clean way out is rollback.

## Expected vs actual

- Expected: You expect the delete to match nothing (as it did up to 9.8.0) or to be rejected, and the commit to contain the document.
- Actual: The delete throws BytesRefHash.MaxBytesLengthExceededException after it was already queued and is never removed from the queue, so every later addDocument and delete throws the same exception and commit fails (with assertions off an IllegalStateException 'Can't close queue unless all changes are applied', with -ea an AssertionError that closes the writer); the only clean way out is rollback.

## Files

- [src/org/apache/lucene/index/TestOverlongDeleteTermKeepsWriterUsable.java](src/org/apache/lucene/index/TestOverlongDeleteTermKeepsWriterUsable.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
     execute --select-class org.apache.lucene.index.TestOverlongDeleteTermKeepsWriterUsable
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
