# With a soft-deletes field configured, IndexWriter.hasUncommittedChanges() returns false after IndexWriter applies a deleteDocuments(Term|Query) that hard-deletes only documents that are already soft-deleted (for example at an NRT reader open), although the next commit() writes that delete (since 7.4.0)

Finding LFV-1393 · Area: index / IndexWriter delete-apply change accounting with soft deletes (IndexWriter.closeSegmentStates / finishApply, PendingSoftDeletes.delete, PendingDeletes.getDelCount) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1393.html](../../bugs/LFV-1393.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a soft-deletes field, soft-delete document a (updateDocValues on the soft-deletes field), commit, then hard-delete a with deleteDocuments(new Term("id", "a")) and open a near-real-time reader with DirectoryReader.open(writer), which applies the delete. You expect hasUncommittedChanges() to return true, because the next commit() does write that delete (a new segments_N in which a is hard-deleted; a plain reader's numDocs goes from 2 to 1) and the javadoc allows only false positives. Instead it returns false: when IndexWriter applies the delete it only checks whether the segment's total delete count (hard plus soft) grew, and turning a soft delete into a hard delete leaves that total unchanged, so the change is never counted.

## Expected vs actual

- Expected: `hasUncommittedChanges()` returns true after the NRT open, since the next `commit()` writes the purge.
- Actual: It returns false (`assertTrue` at line 34 fails), although the next `commit()` hard-deletes `a`: a plain reader sees 1 document, not 2.

## Files

- [src/org/apache/lucene/index/TestNrtAppliedPurgeOfSoftDeletedDoc.java](src/org/apache/lucene/index/TestNrtAppliedPurgeOfSoftDeletedDoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestNrtAppliedPurgeOfSoftDeletedDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNrtAppliedPurgeOfSoftDeletedDoc.java](TestNrtAppliedPurgeOfSoftDeletedDoc.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
