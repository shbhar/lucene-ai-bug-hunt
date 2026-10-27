# LongRangeGroupSelector and DoubleRangeGroupSelector load their per-segment values only in setScorer, so grouping with score-less caching returns wrong groups (8.6.0-10.5.2) or throws NullPointerException (main), and DistinctValuesCollector with a range value selector always throws NullPointerException

Bug LFV-414 · Area: grouping / range group selectors (LongRangeGroupSelector, DoubleRangeGroupSelector) on scorer-less collection paths: the GroupingSearch score-less cache replay and the DistinctValuesCollector value selector · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group a two-segment index (prices 5, 15, 25 | 35, 45, 55) into price ranges with GroupingSearch and new LongRangeGroupSelector(price, new LongRangeFactory(0, 20, 60)), sort by a field, and turn on the documented second-pass cache without scores, setCaching(100, false); you expect the same groups as without the cache: LongRange(0, 20) -&gt; docs 0, 1, LongRange(20, 40) -&gt; 2, 3, LongRange(40, 60) -&gt; 4, 5. Instead every release from 8.6.0 to 10.5.2 returns LongRange(0, 20) -&gt; none, LongRange(20, 40) -&gt; 0, 3 and LongRange(40, 60) -&gt; 1, 2, 4, 5 with no error (the first segment's documents are grouped by the second segment's prices), main throws NullPointerException, and DistinctValuesCollector with a range selector as its value selector throws NullPointerException in every version; DoubleRangeGroupSelector behaves the same. The cause is that both range selectors fetch their per-segment values only in setScorer, while the score-less cache replay and DistinctValuesCollector's per-group collector call setNextReader and advanceTo on the selector but never setScorer.

## Expected vs actual

- Expected: You expect the same groups as without the cache: LongRange(0, 20) -&gt; docs 0, 1, LongRange(20, 40) -&gt; 2, 3, LongRange(40, 60) -&gt; 4, 5.
- Actual: Every release from 8.6.0 to 10.5.2 returns LongRange(0, 20) -&gt; none, LongRange(20, 40) -&gt; 0, 3 and LongRange(40, 60) -&gt; 1, 2, 4, 5 with no error (the first segment's documents are grouped by the second segment's prices), main throws NullPointerException, and DistinctValuesCollector with a range selector as its value selector throws NullPointerException in every version; DoubleRangeGroupSelector behaves the same.

## Files

- [src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorer.java](src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorer.java) (JUnit test)
- [src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorerReleases.java](src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorerReleases.java) (JUnit test)
- [src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorer9x.java](src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorer9x.java) (JUnit test)
- [src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorer8x.java](src/org/apache/lucene/search/grouping/TestRangeGroupSelectorWithoutScorer8x.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestRangeGroupSelectorWithoutScorer

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestRangeGroupSelectorWithoutScorerReleases \
       --select-class org.apache.lucene.search.grouping.TestRangeGroupSelectorWithoutScorer9x \
       --select-class org.apache.lucene.search.grouping.TestRangeGroupSelectorWithoutScorer8x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
