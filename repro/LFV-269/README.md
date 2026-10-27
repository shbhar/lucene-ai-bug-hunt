# The merged vector values of SlowCompositeCodecReaderWrapper throw NullPointerException from copy when one merged segment has no vectors for the field, so merges through BPReorderingMergePolicy with BpVectorReorderer fail and close the IndexWriter, again after every reopen

Bug LFV-269 · Area: index / merged CodecReader view (SlowCompositeCodecReaderWrapper vector values); misc / BP merge-time reordering · Priority: medium · Impact: index unusable · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use BPReorderingMergePolicy with a BpVectorReorderer on a float vector field, and one segment holds only documents without that optional field, for example a batch of documents without embeddings that was flushed on its own. You expect merges to combine it with the other segments, as they do when every segment has vectors. Instead the merged view's FloatVectorValues.copy throws NullPointerException ('Cannot invoke KnnVectorValues.copy because this.sub is null'), IndexWriter treats the failed merge as a tragic event, closes itself and drops every change since the last commit, and the same merge fails again after each reopen.

## Expected vs actual

- Expected: You expect merges to combine it with the other segments, as they do when every segment has vectors.
- Actual: The merged view's FloatVectorValues.copy throws NullPointerException ('Cannot invoke KnnVectorValues.copy because this.sub is null'), IndexWriter treats the failed merge as a tragic event, closes itself and drops every change since the last commit, and the same merge fails again after each reopen.

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorReorderSegmentWithoutVectors.java](src/org/apache/lucene/misc/index/TestBpVectorReorderSegmentWithoutVectors.java) (JUnit test)
- [src/org/apache/lucene/index/TestSlowCompositeVectorValuesCopy.java](src/org/apache/lucene/index/TestSlowCompositeVectorValuesCopy.java) (JUnit test)
- [src2/org/apache/lucene/index/TestSlowCompositeVectorValuesCopy.java](src2/org/apache/lucene/index/TestSlowCompositeVectorValuesCopy.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorReorderSegmentWithoutVectors \
       --select-class org.apache.lucene.index.TestSlowCompositeVectorValuesCopy
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSlowCompositeVectorValuesCopy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
