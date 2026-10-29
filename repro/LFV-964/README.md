# GroupingSearch with groupLimit or groupDocsLimit = Integer.MAX_VALUE ('all remaining groups/documents') throws NegativeArraySizeException, OutOfMemoryError or an IllegalArgumentException naming -2147483648, because it does not cap the limits at maxDoc as IndexSearcher.search does and its collectors add offset + limit in int (every release since 4.0.0)

Bug LFV-964 · Area: grouping / GroupingSearch (term, function, cached two-pass and doc-block grouping) x the first-pass, TopGroups and block collector managers · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call GroupingSearch.search(searcher, query, 0, Integer.MAX_VALUE) or setGroupDocsLimit(Integer.MAX_VALUE) to get every group, or every document of each group, and expect the same TopGroups as with a limit of maxDoc, the way IndexSearcher.search(query, Integer.MAX_VALUE) returns every hit. Instead the search throws: with offset 0 a NegativeArraySizeException -2147483648, an OutOfMemoryError 'Requested array size exceeds VM limit' or a queue-size IllegalArgumentException, and with offset 1 an IllegalArgumentException such as 'topNGroups must be &gt;= 1 (got -2147483648)' or 'numHits must be &gt; 0'. GroupingSearch hands the limits on uncapped to collectors that add offset + limit in int and pre-size their queues by the limit; nothing is ever returned wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The search throws: with offset 0 a NegativeArraySizeException -2147483648, an OutOfMemoryError 'Requested array size exceeds VM limit' or a queue-size IllegalArgumentException, and with offset 1 an IllegalArgumentException such as 'topNGroups must be &gt;= 1 (got -2147483648)' or 'numHits must be &gt; 0'.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingSearchNoLimit.java](src/org/apache/lucene/search/grouping/TestGroupingSearchNoLimit.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupingSearchNoLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/grouping-offset-plus-limit-overflow/](../../models/grouping-offset-plus-limit-overflow/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
