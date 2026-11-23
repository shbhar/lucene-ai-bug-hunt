# Sandbox LargeNumHitsTopDocsCollector#topDocs(int howMany), on a collector that collected more hits than its requestedHitCount, returns the lowest-ranked of the kept hits instead of the best for a positive howMany below requestedHitCount (20 hits, a collector for 10: topDocs(3) returns documents [12 11 10], the best three are [19 18 17]) and a scoreDocs array that starts with null entries for a howMany above it (topDocs(15): five); topDocs() and the stock manager were right in the runs

Finding LFV-1580 · Area: sandbox / search collectors (LargeNumHitsTopDocsCollector#topDocs(int howMany): the queue path of populateResults) · Priority: low · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search 20 matching documents into a LargeNumHitsTopDocsCollector built for 10 hits (through your own CollectorManager) and then call topDocs(3) or topDocs(15) on it. You expect the three best hits, and for 15 the ten hits that the collector kept, as the method's javadoc says ('Returns the top docs that were collected by this collector.') and as the same calls give while the collector has collected no more hits than it was built for. Instead topDocs(3) returns documents [12 11 10], the three lowest-ranked of the ten kept hits, with no error, and topDocs(15) returns an array of 15 entries whose first five are null: once the collector has built its priority queue, the method calls pop() howMany times on it, least element first, without looking at the queue's size. topDocs(), topDocs(10) and the stock LargeNumHitsTopDocsCollectorManager returned the ten best hits in the same runs.

## Expected vs actual

- Expected: You expect the three best hits, and for 15 the ten hits that the collector kept, as the method's javadoc says ('Returns the top docs that were collected by this collector.') and as the same calls give while the collector has collected no more hits than it was built for.
- Actual: topDocs(3) returns documents [12 11 10], the three lowest-ranked of the ten kept hits, with no error, and topDocs(15) returns an array of 15 entries whose first five are null: once the collector has built its priority queue, the method calls pop() howMany times on it, least element first, without looking at the queue's size. topDocs(), topDocs(10) and the stock LargeNumHitsTopDocsCollectorManager returned the ten best hits in the same runs.

## Files

- [src/TestLargeNumHitsTopDocsHowMany.java](src/TestLargeNumHitsTopDocsHowMany.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 262e019500ec (a commit of 2026-11-22): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main, with and without assertions. topDocs() and the stock LargeNumHitsTopDocsCollectorManager were right in the runs: a probe program with 20 hits got the best hits from topDocs(), from topDocs(10) on a collector for 10 and from the stock manager on the three builds. Which test this block runs: the small test of this folder, TestLargeNumHitsTopDocsHowMany, a plain JUnit class with two methods. It indexes three documents that hold the term 'a' once, twice and three times (three hits with three different scores, the best last), searches them into one LargeNumHitsTopDocsCollector built for 2 hits through a CollectorManager of its own, and calls topDocs(1) or topDocs(3) on that collector. testHowManyBelowRequestedHitCount expects document 2, the best hit, and gets document 1, the second best: java.lang.AssertionError: expected:&lt;2&gt; but was:&lt;1&gt;. testHowManyAboveRequestedHitCount expects the first entry of the array of topDocs(3) not to be null and fails with java.lang.AssertionError: scoreDocs[0] of topDocs(3). The numbers in the text above (20 hits, a collector for 10, documents [12 11 10], five null entries) are those of a larger case with the same two kinds of call, run by a test that is not shipped here and by a probe program; that test was run on the same three builds, on the releases 10.5.0, 10.4.0 and 9.12.3 and, in a form with three changed import lines, on 8.11.4 and 8.2.0; a small program, not a test, was run on each of the 58 releases from 8.2.0 to 10.6.0. The shipped test draws nothing from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for the release, set V=10.6.0 and use the six 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox: with the test copied to lucene/sandbox/src/test/ it reports 2 tests completed, 2 failed, with the same two messages, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false.
- How to read the outcome: a failing test is the finding reproducing: the run reports 2 tests found and 2 tests failed, testHowManyBelowRequestedHitCount with java.lang.AssertionError: expected:&lt;2&gt; but was:&lt;1&gt; and testHowManyAboveRequestedHitCount with java.lang.AssertionError: scoreDocs[0] of topDocs(3).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestLargeNumHitsTopDocsHowMany
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
