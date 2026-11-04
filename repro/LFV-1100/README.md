# TopGroups.merge adds each group's per-shard long hit counts into an int, so a group with more than Integer.MAX_VALUE hits over all shards makes the merge throw IllegalArgumentException (two shards) or return a wrong positive hit count and average score (three or more shards) (known: #10342 / LUCENE-9302) (released)

Finding LFV-1100 · Area: grouping / TopGroups.merge (sharded grouping) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge the second-pass TopGroups of several shards with TopGroups.merge, as its javadoc suggests, and expect each group's hit count to be the sum of the per-shard counts, which are long TotalHits values. Instead the merge sums them in an int: two shards of 1.5e9 hits wrap negative and new TotalHits(...) throws 'value must be &gt;= 0', while three shards of 1.5e9 silently give 205,032,704 and the average score is divided by that number.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The merge sums them in an int: two shards of 1.5e9 hits wrap negative and new TotalHits(...) throws 'value must be &gt;= 0', while three shards of 1.5e9 silently give 205,032,704 and the average score is divided by that number.

## Files

- [src/org/apache/lucene/search/grouping/TestTopGroupsMergeGroupHitCountOverflow.java](src/org/apache/lucene/search/grouping/TestTopGroupsMergeGroupHitCountOverflow.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.search.grouping.TestTopGroupsMergeGroupHitCountOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/overflow-at-scale-2/](../../models/overflow-at-scale-2/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
