# TieredMergePolicy.setMaxMergedSegmentMB(0), a value the setter accepts, makes commit, close and forceMergeDeletes throw once a segment has enough deletes: the doFindMerges candidate loop never admits a segment, so the merge candidate is empty (AssertionError with -ea, IndexOutOfBoundsException with -da); a regression since 7.5.0

Bug LFV-442 · Area: index / TieredMergePolicy merge selection (doFindMerges candidate loop; setMaxMergedSegmentMB validation) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-442.html](../../bugs/LFV-442.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure the default TieredMergePolicy with setMaxMergedSegmentMB(0), which the setter accepts (it rejects only negative values, with the message 'must be &gt;=0'), delete some documents, and expect commit, close and forceMergeDeletes to succeed, either with no merge or with the singleton merge the class javadoc promises for segments over the maximum ('down to 1 at once, if that one has deletions'), as they do with a 1-byte maximum and as they did on 7.4.0. Instead the candidate loop in doFindMerges requires 'bytesThisMerge &lt; maxMergedSegmentBytes', which is 0 &lt; 0 before the first segment is added, so no segment is ever admitted and the candidate list stays empty. 'assert candidate.size &gt; 0' then fails with -ea, and candidate.get(0) throws IndexOutOfBoundsException ('Index 0 out of bounds for length 0') with -da, out of the writer call.

The test shows that `TieredMergePolicy.setMaxMergedSegmentMB(0)`, which the setter accepts, makes `forceMergeDeletes()` throw on a 5-doc segment with 1 deleted doc: the `doFindMerges` candidate loop never admits a segment, so the merge candidate is empty.

## Expected vs actual

Expected `forceMergeDeletes()` to succeed; actual: `AssertionError` at `TieredMergePolicy.java:578` (`-ea`) or `IndexOutOfBoundsException: Index 0 out of bounds for length 0` at `:580` (`-da`), from `findForcedDeletesMerges`.

## Files

- [src/org/apache/lucene/index/TestTieredMergePolicyZeroMaxMergedSegment.java](src/org/apache/lucene/index/TestTieredMergePolicyZeroMaxMergedSegment.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestTieredMergePolicyZeroMaxMergedSegment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
