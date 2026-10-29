# Grouping with a within-group sort that equals Sort.RELEVANCE but is a different object (new Sort, new Sort(SortField.FIELD_SCORE)) returns the wrong documents per group when results from slices or shards are merged, and since 10.5.0 a NaN GroupDocs.maxScore

Bug LFV-232 · Area: grouping / two-pass grouping (TopGroupsCollector second pass, TopGroups.merge, TopGroupsCollectorManager, GroupingSearch) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-232.html](../../bugs/LFV-232.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run two-pass grouping (GroupingSearch, or the grouping collectors and collector managers) with setSortWithinGroup(new Sort(SortField.FIELD_SCORE)) or new Sort, both of which equal Sort.RELEVANCE, and expect the same result as with the Sort.RELEVANCE constant: the top-scoring documents of each group, and GroupDocs.maxScore equal to the best score. Instead TopGroupsCollector tests 'withinGroupSort == Sort.RELEVANCE' (same object), so it collects each group with the field-sort collector, whose hits carry score NaN, while TopGroups.merge tests equals and merges those hits by their NaN scores. When results from 2 or more slices or shards are merged, each group's page then comes out slice by slice or shard by shard (the finder's test returns a score-1 document instead of the score-4 one), and since 10.5.0 the merged maxScore is NaN, on main even for a sequential GroupingSearch.

The test shows that with a within-group sort `new Sort(SortField.FIELD_SCORE)`, which equals Sort.RELEVANCE but is another instance, TopGroups.merge over two shards puts the wrong document at the top of the group, because TopGroupsCollector tests `== Sort.RELEVANCE` and collects score-NaN field hits.

## Expected vs actual

Expected: group g's top hit is shard 1's score-4 document; actual: `shard of group g's top hit expected:<1> but was:<0>` (shard 0's score-1 document).

## Files

- [src/org/apache/lucene/search/grouping/TestEqualRelevanceSortShardMerge.java](src/org/apache/lucene/search/grouping/TestEqualRelevanceSortShardMerge.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestEqualRelevanceSortShardMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
