# A term over 32,766 bytes passed to deleteDocuments(Term...), or to an updateDocument(s) call whose documents are indexed without error, is rejected but stays in IndexWriter's shared delete queue, so later deletes, some later adds and the next commit, NRT reopen or close() fail, and with assertions off a later commit can return without the documents that were buffered when the term was rejected (regression in 9.9.0; up to 9.8.0 such a delete was a no-op)

Finding LFV-1046 · Area: index / IndexWriter buffered delete terms (DocumentsWriterDeleteQueue.DeleteSlice.apply, BufferedUpdates.DeletedTerms) · Priority: medium · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1046.html](../../bugs/LFV-1046.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document, call deleteDocuments(new Term("id", t)) with t longer than 32,766 bytes, and commit. No indexed term can be that long, so you expect the delete to match nothing (as it did up to 9.8.0) or to be rejected, and the commit to contain the document. Instead the delete throws BytesRefHash.MaxBytesLengthExceededException after it was already queued and is never removed from the queue, so later deletes throw the same exception, as does an addDocument called right after it (not every later add does), and commit() fails (with assertions off an IllegalStateException 'Can't close queue unless all changes are applied', with -ea an AssertionError that closes the writer); with assertions off a second commit() then returns without the document.

## Expected vs actual

- Expected: `commit()` succeeds and the index holds 1 document, as on 9.8.0.
- Actual: `commit()` throws `IllegalStateException: Can't close queue unless all changes are applied` (assertions off) or `AssertionError: pendingNumDocs 1 != 0 totalMaxDoc` (assertions on).

## Files

- [src/org/apache/lucene/index/TestOverlongDeleteTermKeepsWriterUsable.java](src/org/apache/lucene/index/TestOverlongDeleteTermKeepsWriterUsable.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- What this folder covers: the test adds one document, lets deleteDocuments with a 32,767-byte term throw, and calls commit(): the failed commit is the finding reproducing. The other clauses of the title (an updateDocument call with such a term, the later deletes and adds, an NRT reopen or close(), and the later commit that returns without the buffered document) have no test in this folder; the finding page gives their runs. Run on 2026-11-18 on Lucene main 1cfa86310f76, with and without assertions: 1 test found, 1 test failed (AssertionError 'pendingNumDocs 1 != 0 totalMaxDoc' with assertions on; IllegalStateException 'Can't close queue unless all changes are applied' with assertions off).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestOverlongDeleteTermKeepsWriterUsable
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestOverlongDeleteTermKeepsWriterUsable.java](TestOverlongDeleteTermKeepsWriterUsable.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
