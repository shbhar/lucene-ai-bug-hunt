# Weight#matches of an interval query can throw ArrayIndexOutOfBoundsException for a matching document when one sub-source of an ordered, unordered or atLeast source has 13 or more sub-matches (for example a 13-term phrase followed by a term; run at seven sizes from 13 to 300), so a default UnifiedHighlighter call can fail when one of its hits is such a document; search, count and explain are unaffected

Finding LFV-1577 · Area: queries / intervals (Matches of ordered, unordered and atLeast sources: CachingMatchesIterator); highlighting · Priority: medium · Impact: crash · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1577.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1577.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the 14-word text 'a b c d e f g h i j k l m n' and ask Weight#matches for the interval query ordered(phrase(a ... m), term(n)), a 13-term phrase followed by a term; the default UnifiedHighlighter asks the same question when it highlights a hit of such a query. The document is a hit (count is 1), so you expect the one match at positions 0 to 13, as ordered(phrase(a ... l), term(m)), a 12-term phrase followed by a term, gives positions 0 to 12 on the same text. Instead the call throws java.lang.ArrayIndexOutOfBoundsException (Index 50 out of bounds for length 50) from CachingMatchesIterator.cache(), with and without assertions: the buffer that copies the sub-matches of each sub-source is grown only when 'count * 4 &gt;= length', a test that is right only while the length is a multiple of 4, and after four growths the length is 50, so the 13th sub-match of one sub-source cannot be stored; the search itself is unaffected.

## Expected vs actual

- Expected: One match at positions 0 to 13: the document is a hit of the query (its count is 1).
- Actual: `weight.matches` throws `java.lang.ArrayIndexOutOfBoundsException: Index 50 out of bounds for length 50` at `CachingMatchesIterator.cache(CachingMatchesIterator.java:54)`, assertions on and off.

## Files

- [src/org/apache/lucene/queries/intervals/TestIntervalMatchesThirteenSubMatches.java](src/org/apache/lucene/queries/intervals/TestIntervalMatchesThirteenSubMatches.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): reproduced, with assertions on and off.
- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (3221dd4cf3a1) and on Lucene main, with and without assertions. What the test does: it indexes one document with the 14-word text 'a b c d e f g h i j k l m n', builds the interval query ordered(phrase(a ... m), term(n)), a 13-term phrase followed by a term, and asks Weight#matches for the document; it expects one match at positions 0 to 13. The matches call throws java.lang.ArrayIndexOutOfBoundsException: Index 50 out of bounds for length 50 at CachingMatchesIterator.cache(CachingMatchesIterator.java:54), so the test's own assertions are not reached. The test's input has no randomness; the test framework draws its environment (the default codec among other things) from the seed, and the outcome was the same under each seed that was run. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for the release, set V=10.6.0 and use the four 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-queries) in place of the four $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/queries: with the test copied to lucene/queries/src/test/org/apache/lucene/queries/intervals/ it reports 1 test completed, 1 failed, with the same exception, on branch_11x 3221dd4cf3a1 and on main 1a59f96fc6de, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.ArrayIndexOutOfBoundsException: Index 50 out of bounds for length 50, thrown in CachingMatchesIterator.cache under the test's Weight#matches call.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.queries.intervals.TestIntervalMatchesThirteenSubMatches
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestIntervalMatchesThirteenSubMatches.java](TestIntervalMatchesThirteenSubMatches.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
