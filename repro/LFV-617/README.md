# UpgradeIndexMergePolicy rewrites the old segments its delegate declines with its own plain OneMerge, which a wrapping delegate never sees, so IndexUpgrader (or UpgradeIndexMergePolicy) with a SoftDeletesRetentionMergePolicy inside silently purges the soft-deleted documents the retention policy must keep

Finding LFV-617 · Area: core index / UpgradeIndexMergePolicy and IndexUpgrader with OneMergeWrappingMergePolicy delegates (SoftDeletesRetentionMergePolicy) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-617.html](../../bugs/LFV-617.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure soft deletes with SoftDeletesRetentionMergePolicy("soft", MatchAllDocsQuery::new, new TieredMergePolicy()), which must keep every soft-deleted document, and upgrade a one-segment index written by Lucene 10.5.2 (10 visible plus 3 soft-deleted documents) with new IndexUpgrader(dir, thatConfig, false).upgrade(). You expect the segment to be rewritten in the current format with all 13 documents, exactly as happens when the same index has two old segments. Instead TieredMergePolicy treats the lone segment as already merged, UpgradeIndexMergePolicy rewrites it with its own 'merge remaining ones into new segment' OneMerge that the retention policy never wraps, and the 3 retained documents are purged with no error (maxDoc 13 -&gt; 10; partial retention queries lose their documents the same way).

The test shows that `UpgradeIndexMergePolicy` around a `SoftDeletesRetentionMergePolicy` (the nesting `IndexUpgrader` builds) rewrites a lone old segment with its own unwrapped merge, so the soft-deleted documents the retention policy must keep are purged.

## Expected vs actual

Expected `maxDoc` 2 after `forceMerge(1)` (one live plus one retained soft-deleted doc); actual 1 (`expected:<2> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestUpgradeIndexMergePolicyRetainsSoftDeletes.java](src/org/apache/lucene/index/TestUpgradeIndexMergePolicyRetainsSoftDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- This finding was re-run on Lucene main f2b061406c4b on 2026-11-19: still reproduces (the repro adapted to an API change on this commit). The change: #16962 made the no-argument MatchAllDocsQuery constructor private on main (24d9e7d1a0d) and on the 11.0 branch (branch_11x, a6a26f9b251), so replace MatchAllDocsQuery::new with () -&gt; MatchAllDocsQuery.INSTANCE before you compile the test there; in the commands below, V=12.0.0-SNAPSHOT on a build of main f2b061406c4b and V=11.0.0-SNAPSHOT on a build of the 11.0 branch at daa54f2e0cd3.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.index.TestUpgradeIndexMergePolicyRetainsSoftDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
