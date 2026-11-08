# SoftDeletesRetentionMergePolicy.keepFullyDeletedSegment ignores the wrapped policy when its retention query has a non-null scorer with no hits, so a fully soft-deleted segment that the wrapped policy keeps (for example the soft-deleted documents an inner SoftDeletesRetentionMergePolicy retains) is dropped at commit

Finding LFV-1354 · Area: index / merge policies (SoftDeletesRetentionMergePolicy: keeping fully deleted segments) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1354.html](../../bugs/LFV-1354.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap one SoftDeletesRetentionMergePolicy (retention query 'keep everything') in a second one whose retention query is LongPoint seq:[3 TO 4], index two documents with seq 1 and 10, soft-delete both through updateDocValues and commit; you expect both soft-deleted documents to stay in the index (maxDoc 2), as they do with the inner policy alone, because the outer policy asks the wrapped policy whenever its own query matches nothing. Instead the commit drops the segment (maxDoc 0): keepFullyDeletedSegment asks the wrapped policy only when the query's scorer is null, and a range that lies between the segment's values gives a non-null scorer with no hits, for which it answers 'do not keep' by itself. A range outside the segment's values (null scorer) keeps the documents, so two queries that both match nothing give opposite answers.

## Expected vs actual

- Expected: `maxDoc` 2: the inner policy keeps both soft-deleted documents, as it does alone or under an outer query with a `null` scorer.
- Actual: `maxDoc` 0: the commit drops the segment (`AssertionError: expected:<2> but was:<0>`), with assertions on and off, and no error from Lucene.

## Files

- [src/org/apache/lucene/index/TestSoftDeletesRetentionKeepsWrappedSegment.java](src/org/apache/lucene/index/TestSoftDeletesRetentionKeepsWrappedSegment.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces (the repro adapted to an API change on this commit).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSoftDeletesRetentionKeepsWrappedSegment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSoftDeletesRetentionKeepsWrappedSegment.java](TestSoftDeletesRetentionKeepsWrappedSegment.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
