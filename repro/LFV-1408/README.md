# JoinUtil term joins (TermsQuery, TermsIncludingScoreQuery) compare equal on the from searcher's reader-context id instead of the collected terms, so after a join whose from-side search hit IndexSearcher#setTimeout, a shared query cache serves the partial join's hits to later requests that did not time out

Finding LFV-1408 · Area: join / JoinUtil term joins (query identity) and the query cache under IndexSearcher#setTimeout · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1408.html](../../bugs/LFV-1408.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a JoinUtil term join on an IndexSearcher with setTimeout, the deadline passes while the join collects its from-side terms, and later requests build the same join on the same reader without any timeout, all sharing one query cache (the IndexSearcher default in 9.3.0-10.6.0). You expect every later request to get the complete join's hits, as it does without the cache. Instead the partial join compares equal to the complete one, because TermsQuery and TermsIncludingScoreQuery compare the from searcher's reader-context id rather than the collected terms, so the cache hands the later requests the partial join's hits (0 instead of 1 in the repro, 50 of 12,000 with the 10.6.0 defaults) while their timedOut() is false.

## Expected vs actual

- Expected: The second `count(join)`, on the searcher without a timeout, returns 1.
- Actual: It returns 0 (`expected:<1> but was:<0>`), and that searcher's `timedOut()` is false.

## Files

- [src/org/apache/lucene/search/join/TestJoinAfterTimedOutJoin.java](src/org/apache/lucene/search/join/TestJoinAfterTimedOutJoin.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestJoinAfterTimedOutJoin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestJoinAfterTimedOutJoin.java](TestJoinAfterTimedOutJoin.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
