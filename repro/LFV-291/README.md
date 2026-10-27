# Intervals.notContainedBy(small, or(B1, B2)) returns small intervals that lie inside B1 or B2, because the default disjunction rewrite turns it into an OR of per-clause filters

Bug LFV-291 · Area: queries / intervals (Intervals.notContainedBy: NotContainedByIntervalsSource disjunction rewrite, LUCENE-8477) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-291.html](../../bugs/LFV-291.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'i love new york', 'york city is big' and 'york is here' and search notContainedBy(york, or(phrase(new, york), phrase(york, city))), meaning 'york that is not part of new york or york city'; you expect only the third document, which is what or(false,...) and the nested form notContainedBy(notContainedBy(york, 'new york'), 'york city') return. Instead all three documents match: the default disjunction rewrite (LUCENE-8477) pulls the OR out of the excluded operand and builds or(NOT_CONTAINED_BY(york, 'new york'), NOT_CONTAINED_BY(york, 'york city')), which computes 'not inside B1 or not inside B2' where the javadoc promises 'not inside (B1 or B2)'. So adding a clause to the exclusion adds hits: notContainedBy(a, or(phrase(c, a), b)) matches 'c a' although notContainedBy(a, phrase(c, a)) does not.

## Expected vs actual

- Expected: `testOrOfPhrasesAsBigSource`: 1 hit (doc 2, "york is here"), as the chained and `or(false,...)` controls give; `testAddingClauseToBigSourceAddsNoHits`: 0 hits.
- Actual: 3 hits, then 1 hit: the query is built as `or(NOT_CONTAINED_BY(york,BLOCK(new,york)),NOT_CONTAINED_BY(york,BLOCK(york,city)))`, one filter per phrase.

## Files

- [src/org/apache/lucene/queries/intervals/TestNotContainedByDisjunction.java](src/org/apache/lucene/queries/intervals/TestNotContainedByDisjunction.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestNotContainedByDisjunction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestNotContainedByDisjunction.java](TestNotContainedByDisjunction.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestNotContainedByNestedClauses.java](TestNotContainedByNestedClauses.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
