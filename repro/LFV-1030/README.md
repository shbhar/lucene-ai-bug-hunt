# MIGRATE.md's 11.0 'Upgrade Example' says forceMerge(1) 'Rewrites all segments to latest format', but with the default merge policy it leaves an index that already has one segment and no deletions unchanged in its 9.x or 10.x format (documentation defect, main only; the code is correct)

Bug LFV-1030 · Area: index / forceMerge and index upgrade (lucene/MIGRATE.md 11.0 'Relaxed Index Upgrade Policy': Scenario 1 and 'Upgrade Example') · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Lucene main's MIGRATE.md tells users who open a Lucene 9.x or 10.x index to upgrade its segments by opening an IndexWriter and calling forceMerge(1), with the comment 'Rewrites all segments to latest format' (line 179), and line 153 offers forceMerge as a way 'to upgrade segment formats immediately'. With the default merge policy, forceMerge(1) does nothing on an index that already has one segment and no deletions: TieredMergePolicy reports it as already merged, so on Lucene's own single-segment 9.0.0 test index the segment is still version 9.0.0 with codec Lucene90 afterwards. The code behaves as designed (forceMerge only promises a segment count, and UpgradeIndexMergePolicy and IndexUpgrader exist for exactly this case); the guide should wrap the merge policy in UpgradeIndexMergePolicy or point to IndexUpgrader.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/backward_index/TestMigrateUpgradeExample.java](src/org/apache/lucene/backward_index/TestMigrateUpgradeExample.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Fixture: the test reads moreterms.9.0.0.zip, a test resource of Lucene's backward-codecs module (lucene/backward-codecs/src/test/org/apache/lucene/backward_index/ in the checkout), so the run puts $LUCENE/lucene/backward-codecs/src/test on the class path; without it the test fails for another reason.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$LUCENE/lucene/backward-codecs/src/test:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_index.TestMigrateUpgradeExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/migrate-upgrade-example-forcemerge-keeps-old-format/](../../models/migrate-upgrade-example-forcemerge-keeps-old-format/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
