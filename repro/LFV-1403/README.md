# IndexWriter.hasDeletions() returns false for an acknowledged buffered delete while another thread's commit(), flush() or NRT reader open is writing a segment, because DocumentsWriter.anyDeletions() reads only the current delete queue and not the deletes frozen into flush tickets that are not yet published (since 4.0.0)

Finding LFV-1403 · Area: index / IndexWriter full flush (hasDeletions vs. the DocumentsWriter delete queue and the flush ticket queue) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1403.html](../../bugs/LFV-1403.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add documents a and b, delete a with deleteDocuments(new Term("id", "a")), and hasDeletions() returns true; then another thread calls commit(). You expect hasDeletions() to stay true while that commit runs, because its javadoc counts buffered deletions and the delete is either still buffered or already applied at every moment (the method is true before the commit and true after it). Instead, while the commit writes the new segment, hasDeletions() returns false: the flush has frozen the delete into a flush ticket that is not yet published, and DocumentsWriter.anyDeletions() reads only the new, empty delete queue.

## Expected vs actual

- Expected: `hasDeletions()` is `true` before, during and after the commit.
- Actual: `java.lang.AssertionError: hasDeletions() while the commit writes the segment` (the read during the commit is `false`), assertions on and off.

## Files

- [src/org/apache/lucene/index/TestHasDeletionsDuringFlush.java](src/org/apache/lucene/index/TestHasDeletionsDuringFlush.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHasDeletionsDuringFlush
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHasDeletionsDuringFlush.java](TestHasDeletionsDuringFlush.java)

Formal models and scripts this finding's part of the hunt used: [../../models/hasdeletions-false-for-acknowledged-delete-in-swapped-out-queue-or-unpublished-flush-ticket-low/](../../models/hasdeletions-false-for-acknowledged-delete-in-swapped-out-queue-or-unpublished-flush-ticket-low/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
