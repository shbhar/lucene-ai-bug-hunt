# A multi-segment kNN query's optimistic second pass, when cut short by the IndexSearcher timeout, replaces the hits a complete first pass already found, so a later timeout can return fewer or farther hits than an earlier one, down to 0 hits

Bug LFV-117 · Area: search / kNN query rewrite (AbstractKnnVectorQuery optimistic re-entry pass) under the IndexSearcher timeout · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set a timeout with IndexSearcher#setTimeout and run a kNN query on an index with several segments; Lucene searches every segment once and, if that first pass completed, searches the segments that supplied the best hits a second time to improve the answer. You expect a timeout to give you the partial results found so far ('Return partial results only when timeout is met', AbstractKnnVectorQuery.java:296), so hits that the complete first pass already found are kept. Instead, when the timeout fires during the second pass, each re-entered segment's cut-short second-pass result (usually empty) replaces its first-pass hits, so the query keeps fewer or farther hits than a slightly earlier timeout would have given, down to 0 hits (IndexSearcher#count goes 6 -&gt; 0 when one more timeout check is allowed).

## Expected vs actual

- Expected: You expect a timeout to give you the partial results found so far ('Return partial results only when timeout is met', AbstractKnnVectorQuery.java:296), so hits that the complete first pass already found are kept.
- Actual: When the timeout fires during the second pass, each re-entered segment's cut-short second-pass result (usually empty) replaces its first-pass hits, so the query keeps fewer or farther hits than a slightly earlier timeout would have given, down to 0 hits (IndexSearcher#count goes 6 -&gt; 0 when one more timeout check is allowed).

## Files

- [src/org/apache/lucene/search/TestKnnReentryTimeout.java](src/org/apache/lucene/search/TestKnnReentryTimeout.java) (JUnit test)
- [src/org/apache/lucene/search/TestProbeReentryTimeout.java](src/org/apache/lucene/search/TestProbeReentryTimeout.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestKnnReentryTimeout

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestProbeReentryTimeout
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/knn-query-layer/](../../models/knn-query-layer/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
