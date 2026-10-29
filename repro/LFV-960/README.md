# TopGroups.merge throws NegativeArraySizeException when docOffset + docTopN overflows int, for example docOffset = 1 with docTopN = Integer.MAX_VALUE ('all remaining documents'), although docOffset 0 with the same docTopN works

Bug LFV-960 · Area: grouping / TopGroups.merge (merging second-pass grouped results across shards or slices) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge two shards' second-pass grouping results with TopGroups.merge(shardTopGroups, groupSort, docSort, 1, Integer.MAX_VALUE,...) to get every document after the first in each group, and expect the same groups as with docTopN = 10 on the same small data, just as docOffset 0 with docTopN = Integer.MAX_VALUE already gives. Instead merge throws NegativeArraySizeException: -2147483648: it passes the int sum docOffset + docTopN to TopDocs.merge, the sum wraps negative, and TopDocs.mergeAux sizes its result array with it. Every pair with docOffset + docTopN &gt;= 2^31 fails, for relevance and field order within groups; a sum of 2^31 - 1 still works.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Merge throws NegativeArraySizeException: -2147483648: it passes the int sum docOffset + docTopN to TopDocs.merge, the sum wraps negative, and TopDocs.mergeAux sizes its result array with it.

## Files

- [src/org/apache/lucene/search/grouping/TestTopGroupsMergeNoLimit.java](src/org/apache/lucene/search/grouping/TestTopGroupsMergeNoLimit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.grouping.TestTopGroupsMergeNoLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/grouping-offset-plus-limit-overflow/](../../models/grouping-offset-plus-limit-overflow/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
