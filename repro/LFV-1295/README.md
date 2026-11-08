# With assertions enabled, calling Scorable.getChildren() from LeafCollector.setScorer on a TOP_SCORES BooleanQuery with minimumNumberShouldMatch &gt;= 2 throws AssertionError from WANDScorer.ensureConsistent, which calls score() on sub-scorers that are not positioned yet (clauses such as ConstantScoreQuery, PrefixQuery or a TermQuery on a docs-only field; 10.1.0 through 10.6.0 and main)

Finding LFV-1295 · Area: core / search: WANDScorer (Scorable.getChildren before the first document, TOP_SCORES minimum-should-match disjunctions) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1295.html](../../bugs/LFV-1295.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search a BooleanQuery with three SHOULD clauses and minimumNumberShouldMatch 2 with a collector whose scoreMode is TOP_SCORES and whose setScorer walks the scorer tree with Scorable.getChildren(), a pattern Lucene's own TestBooleanQueryVisitSubscorers uses. You expect getChildren() to return the three clause scorers, as it does with assertions disabled. Instead, with -ea, WANDScorer.getChildren() runs its internal consistency check before any document has been read: the check calls score() on the not-yet-positioned clause scorers and asserts that their sum equals the lead score, which is still 0, so the search fails with AssertionError at WANDScorer.java:238 whenever a clause scores non-zero while unpositioned (for example ConstantScoreQuery clauses).

## Expected vs actual

- Expected: `getChildren()` returns the 3 clause scorers (as with assertions off, and on 10.0.0).
- Actual: `java.lang.AssertionError` at `WANDScorer.ensureConsistent(WANDScorer.java:238)` &lt;- `advanceAllTail(:549)` &lt;- `getChildren(:272)`.

## Files

- [src/org/apache/lucene/search/TestWANDScorerGetChildren.java](src/org/apache/lucene/search/TestWANDScorerGetChildren.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestWANDScorerGetChildren
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestWANDScorerGetChildren.java](TestWANDScorerGetChildren.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
