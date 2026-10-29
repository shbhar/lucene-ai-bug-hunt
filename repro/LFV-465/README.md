# IndexSearcher#count on a two-term disjunction over-counts, even above maxDoc, when the searcher timeout fires while the intersection is counted

Bug LFV-465 · Area: search / IndexSearcher#count (two-clause term-disjunction shortcut, GITHUB#13036) under IndexSearcher#setTimeout · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set a timeout with IndexSearcher#setTimeout and call IndexSearcher#count on a query like `a OR b`, where term b matches far fewer documents than term a, on an index without deletions. When the timeout fires you expect a partial count, at most the number of matching documents, which is what every other count and collect path returns under the same timeout (and what this path returned up to 9.9.2). Instead count returns count(a) + count(b) minus a cut-short count of `a AND b`, which is too high (1041 for a 1000-document index with 955 matches when the deadline has already passed) and gets smaller, not larger, when the timeout fires later; timedOut is true.

The test shows that `IndexSearcher#count` on `a OR b` (term counts more than 10x apart, no deletions) returns more than `maxDoc` when the searcher timeout has already expired: the shortcut count(a) + count(b) - count(a AND b) subtracts an intersection count the timeout cut short.

## Expected vs actual

Expected a timed-out count of at most `maxDoc` 22 (21 docs match); actual 23 (21 + 2 - 0), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestTimedOutCountTwoTermDisjunction.java](src/org/apache/lucene/search/TestTimedOutCountTwoTermDisjunction.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestTimedOutCountTwoTermDisjunction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/timeout-partial-results/](../../models/timeout-partial-results/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
