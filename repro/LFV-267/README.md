# TopDocs.merge with a custom tie breaker fails an assertion (only with -ea) when the tie breaker returns 0 for tied hits from two different shards at the same rank

Bug LFV-267 · Area: search / TopDocs.merge tie breaking (score and Sort merges with a custom tie breaker) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge per-shard results with TopDocs.merge(start, topN, shardHits, tieBreaker), or the Sort overload, and pass a valid Comparator&lt;ScoreDoc&gt; tie breaker that ignores the shard, for example one that compares doc ids only; two shards each have a hit with the same score (or sort value) at the same rank and the same local doc id. You expect both hits back, since the method accepts any custom tie breaker and its helper documents that a 0 from the tie breaker falls back to hit indices. Instead, with assertions enabled, the merge throws AssertionError at TopDocs.java:88 ('assert first.hitIndex != second.hitIndex'), because that check assumes a tie can only come from the same shard while the merge queue only ever compares hits of different shards; with -da both hits are returned.

## Expected vs actual

- Expected: You expect both hits back, since the method accepts any custom tie breaker and its helper documents that a 0 from the tie breaker falls back to hit indices.
- Actual: With assertions enabled, the merge throws AssertionError at TopDocs.java:88 ('assert first.hitIndex != second.hitIndex'), because that check assumes a tie can only come from the same shard while the merge queue only ever compares hits of different shards; with -da both hits are returned.

## Files

- [src/org/apache/lucene/search/TestTopDocsMergeTieBreakerAcrossShards.java](src/org/apache/lucene/search/TestTopDocsMergeTieBreakerAcrossShards.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestTopDocsMergeTieBreakerAcrossShards
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/priorityqueue-topdocs-merge/](../../models/priorityqueue-topdocs-merge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
