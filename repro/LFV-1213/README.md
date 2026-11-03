# TemporalMergePolicy with setMaxThreshold(Integer.MAX_VALUE), a value the setter accepts, makes IndexWriter.forceMergeDeletes() throw OutOfMemoryError ('Requested array size exceeds VM limit') and merge nothing, because buildSequentialMerges pre-sizes its batch list to maxThreshold

Finding LFV-1213 · Area: index / merge policy (TemporalMergePolicy forced-deletes merges, IndexWriter.forceMergeDeletes) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1213.html](../../bugs/LFV-1213.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure TemporalMergePolicy with setMaxThreshold(Integer.MAX_VALUE), which the setter accepts (it checks only maxThreshold &gt;= minThreshold and says larger values merge more aggressively), and call IndexWriter.forceMergeDeletes() while one time window holds two segments whose share of deleted documents is above forceMergeDeletesPctAllowed, for example two 4-document segments with one deleted document each. You expect the two segments to be merged into one with no deleted documents left, as happens with the default maxThreshold of 8, and as natural merges and forceMerge do with the same setting. Instead buildSequentialMerges creates its batch list with new ArrayList&lt;&gt;(maxThreshold) before adding anything, so forceMergeDeletes() throws java.lang.OutOfMemoryError: Requested array size exceeds VM limit at any heap size, no merge is registered and the deleted documents stay.

## Expected vs actual

- Expected: The two segments are merged into one and `numDeletedDocs()` is 0, as with the default `maxThreshold` of 8.
- Actual: `forceMergeDeletes()` throws `java.lang.OutOfMemoryError: Requested array size exceeds VM limit` at `TemporalMergePolicy.java:957`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestTemporalMergePolicyMaxThreshold.java](src/org/apache/lucene/index/TestTemporalMergePolicyMaxThreshold.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestTemporalMergePolicyMaxThreshold
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTemporalMergePolicyMaxThreshold.java](TestTemporalMergePolicyMaxThreshold.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
