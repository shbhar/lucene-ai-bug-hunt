# ParentsChildrenBlockJoinQuery throws IllegalStateException ('does not support intraSegment concurrency') when a scorer is requested twice for the same leaf, so IndexSearcher.explain of a BooleanQuery that contains it fails, even on a plain single-threaded searcher

Bug LFV-167 · Area: join / block join (ParentsChildrenBlockJoinQuery: ParentsChildrenBlockJoinWeight#scorerSupplier intra-segment guard) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a BooleanQuery that combines a ParentsChildrenBlockJoinQuery with another clause and call IndexSearcher.explain on a hit, and you expect an explanation, because the Weight#scorer and Weight#scorerSupplier javadoc say a scorer (supplier) for the same LeafReaderContext may be requested multiple times as part of a single search call. Instead explain throws IllegalStateException 'ParentsChildrenBlockJoinQuery does not support intraSegment concurrency. Context... was already seen.': the join query's Weight records every leaf it has built a scorer supplier for and refuses a second request, and BooleanWeight#explain makes exactly that second request (the join clause's own explain pulls a scorer, then BooleanWeight pulls its scorer for the same leaf to compute the score). The search itself returns the right hits; explain fails for every document that the BooleanQuery matches, including documents matched only by the other clause.

## Expected vs actual

- Expected: You expect an explanation, because the Weight#scorer and Weight#scorerSupplier javadoc say a scorer (supplier) for the same LeafReaderContext may be requested multiple times as part of a single search call.
- Actual: Explain throws IllegalStateException 'ParentsChildrenBlockJoinQuery does not support intraSegment concurrency.

## Files

- [src/org/apache/lucene/search/join/TestParentsChildrenRepeatedScorer.java](src/org/apache/lucene/search/join/TestParentsChildrenRepeatedScorer.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestParentsChildrenRepeatedScorer
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
