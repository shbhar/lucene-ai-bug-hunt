# addIndexes(CodecReader...) silently adds nothing when OneMerge#wrapForMerge brings the merged document count above Integer.MAX_VALUE, because MergeState sums the wrapped readers' numDocs() in an int with no MAX_DOCS check (index-sorted writers, since 9.9.0)

Finding LFV-1340 · Area: index / IndexWriter.addIndexes(CodecReader...) and MergeState (OneMerge#wrapForMerge) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1340.html](../../bugs/LFV-1340.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index-sorted IndexWriter whose merge policy's findMerges(CodecReader...) returns a OneMerge whose wrapForMerge revives deleted documents (its javadoc allows a wrapper to 'add/remove fields or documents'), and call addIndexes(CodecReader...) with inputs whose live documents fit under MAX_DOCS but whose revived documents total more than Integer.MAX_VALUE; you expect the IllegalArgumentException that addIndexes documents for exceeding MAX_DOCS. Instead addIndexes returns normally and adds nothing: IndexWriter checked the limit only on the unwrapped readers, MergeState sums the wrapped readers' numDocs() in an int that wraps negative (-2,094,967,296 for 2 x 1.1 billion), SegmentMerger.shouldMerge() is then false, and addIndexesReaderMerge returns before writing a segment. Without an index sort, the merged view that IndexWriter builds first rejects the same total with IllegalArgumentException, so only index-sorted writers (and writers given block inputs without a parent field) get the silent no-op.

## Expected vs actual

- Expected: `IllegalArgumentException`: the merge would hold 2,200,000,000 documents, more than `MAX_DOCS` (2,147,483,519).
- Actual: `addIndexes` returns normally and the writer holds 0 documents (the `int` sum wrapped to -2,094,967,296), assertions on or off.

## Files

- [src/org/apache/lucene/index/TestAddIndexesWrappedDocCountOverflow.java](src/org/apache/lucene/index/TestAddIndexesWrappedDocCountOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestAddIndexesWrappedDocCountOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAddIndexesWrappedDocCountOverflow.java](TestAddIndexesWrappedDocCountOverflow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
