# IndexWriter.deleteDocuments(Query...) and updateDocuments(Query,...) do not document that the query runs only when the buffered delete is applied later, or that an exception it throws then (for example a PhraseQuery or FieldExistsQuery on a StringField, or TooManyClauses from an interval prefix once more than 128 terms match) closes the IndexWriter and discards every change since the last commit (documentation defect; the close itself is an intended fail-safe)

Bug LFV-823 · Area: index / IndexWriter delete-by-query (deleteDocuments(Query...) and updateDocuments(Query,...) javadoc and class NOTE vs deferred application in FrozenBufferedUpdates and the applyUpdatesPacket tragic close) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 10 documents with a StringField id, commit 6 of them, and call deleteDocuments(new PhraseQuery("id", "d1", "d2")), which returns a sequence number; nothing in its javadoc says the query runs later or what happens if it fails then. The query actually runs only when the buffered delete is applied, and because a StringField has no positions the PhraseQuery throws IllegalStateException at that point: the exception comes out of the next commit (or flush, NRT open, merge, close, or an unrelated addDocument), the IndexWriter closes itself and the 4 uncommitted documents are gone. The close is a deliberate fail-safe (the same documents are lost without it); what is missing is a warning on deleteDocuments(Query...) and both updateDocuments(Query,...) overloads, and in the class NOTE on when the writer closes itself.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestDeleteQueryApplyFailureClosesWriter.java](src/org/apache/lucene/index/TestDeleteQueryApplyFailureClosesWriter.java) (JUnit test)
- [src/DeleteQueryApplyFailureMain.java](src/DeleteQueryApplyFailureMain.java) (main() class)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteQueryApplyFailureClosesWriter

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" DeleteQueryApplyFailureMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
