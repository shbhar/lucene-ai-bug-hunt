# SpanTermQuery equals/hashCode ignore the TermStates passed to the expert constructor, so BooleanQuery merges two same-term span clauses with different statistics into one clause and scores only one clause's statistics (the span counterpart of #10309, which #12354 fixed for TermQuery only)

Finding LFV-1368 · Area: queries / spans (SpanTermQuery equals/hashCode, consumed by the core BooleanQuery duplicate-clause rewrite) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1368.html](../../bugs/LFV-1368.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two SpanTermQuery objects for the same term with the expert constructor SpanTermQuery(Term, TermStates), one with the reader's real statistics and one with adjusted statistics (as BlendedTermQuery does for TermQuery), and add both as SHOULD clauses of a BooleanQuery. You expect a matching document to score the sum of the two clauses' scores (0.398 in the repro), as TermQuery does on main since #12354. Instead SpanTermQuery.equals/hashCode compare only the term, so BooleanQuery's duplicate-clause rewrite merges the two into one clause weighted for two occurrences and the document scores 0.630, twice the first clause, with no error.

## Expected vs actual

- Expected: Document `"a"` scores `score(q1) + score(q2)` = 0.31506687 + 0.082873434 = 0.3979403.
- Actual: It scores 0.63013375, twice `score(q1)`: `AssertionError: expected:<0.3979403> but was:<0.63013375>`, assertions on and off.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanTermQueryTermStatesDedup.java](src/org/apache/lucene/queries/spans/TestSpanTermQueryTermStatesDedup.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanTermQueryTermStatesDedup
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSpanTermQueryTermStatesDedup.java](TestSpanTermQueryTermStatesDedup.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
