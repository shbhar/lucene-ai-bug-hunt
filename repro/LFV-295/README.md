# Intervals.term(term, payloadFilter) sources compare equal whatever their payload filter, so Intervals.or/ordered/unordered, BooleanQuery rewrite and the query cache merge sources that match different positions

Bug LFV-295 · Area: queries / intervals (PayloadFilteredTermIntervalsSource equality; IntervalQuery dedup and cache keys) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field with a payload at each position and build two sources on the same term with different payload filters, for example a@0 = Intervals.term("a", payload is 'pos: 0') and a@1 = Intervals.term("a", payload is 'pos: 1'), which on their own match different documents. You expect Intervals.or(a@0, a@1) to match both sets, ordered(a@0, a@1) to match the document 'a a', FILTER a@0 + FILTER a@1 to match only that document, and a query cache to keep the two queries apart. Instead PayloadFilteredTermIntervalsSource.equals/hashCode compare only the term, so the two sources are equal: or keeps one of them, ordered and unordered become 'a@0 twice' and match nothing, the two FILTER clauses collapse into one (extra hits), MUST a@0 + MUST_NOT a@1 is rewritten to MatchNoDocsQuery, and a cached searcher returns the first filter's hits for the second, all with no error.

The test shows that `Intervals.ordered(Intervals.term("a", payload x), Intervals.term("a", payload y))` misses a document 'a|x a|y', because `PayloadFilteredTermIntervalsSource.equals/hashCode` ignore the payload filter and `ordered()` treats the two sources as one repeated source.

## Expected vs actual

Expected: 1 hit. Actual on main with -ea and with -da: 0 hits.

## Files

- [src/org/apache/lucene/queries/intervals/TestPayloadFilteredTermOrdered.java](src/org/apache/lucene/queries/intervals/TestPayloadFilteredTermOrdered.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestPayloadFilteredTermOrdered
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
