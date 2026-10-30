# Since #16669 (10.6.0 RC1 and main), ReqExclBulkScorer no longer skips long runs of excluded doc IDs: for +R -X it scores the required clause in every 4096-doc window, even windows X fully excludes, so R's iterator steps through all of its matches inside X's runs (10,001 moves for 1,000 hits in a 10,000-doc segment, where 10.0.0-10.5.2 need 1,001; performance regression, results correct)

Bug LFV-1044 · Area: search / BooleanQuery MUST_NOT bulk scoring (ReqExclBulkScorer) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search +R -grp:old, where R matches every document and grp:old matches one run of consecutive doc IDs covering 9,000 of the 10,000 documents of a segment. You expect Lucene to jump over the excluded run, as 10.3.0 to 10.5.2 do in one docIDRunEnd step (on every release from 10.0.0 to 10.5.2 the required clause's iterator moves 1,001 times for the 1,000 hits). Instead, since #16669 ReqExclBulkScorer loads the exclusion into a bit mask per 4096-doc window and always runs the required clause over every window, even windows the exclusion covers completely, so the iterator moves 10,001 times; the 1,000 hits are still right, only the work now grows with the length of the excluded run.

## Expected vs actual

- Expected: You expect Lucene to jump over the excluded run, as 10.3.0 to 10.5.2 do in one docIDRunEnd step (on every release from 10.0.0 to 10.5.2 the required clause's iterator moves 1,001 times for the 1,000 hits).
- Actual: Since #16669 ReqExclBulkScorer loads the exclusion into a bit mask per 4096-doc window and always runs the required clause over every window, even windows the exclusion covers completely, so the iterator moves 10,001 times; the 1,000 hits are still right, only the work now grows with the length of the excluded run.

## Files

- [src/org/apache/lucene/search/TestReqExclSkipsExcludedRun.java](src/org/apache/lucene/search/TestReqExclSkipsExcludedRun.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestReqExclSkipsExcludedRun
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/reqexcl-bulkscorer-excluded-run-skip-16669/](../../models/reqexcl-bulkscorer-excluded-run-skip-16669/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
