# LogOddsFusionQuery returns the raw clause score in segments where only one clause has a scorer, so identical documents score differently depending on their segment and explain disagrees with the score

Bug LFV-173 · Area: search / score fusion (LogOddsFusionQuery: LogOddsFusionWeight#scorerSupplier) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You combine two or more probability clauses (for example a BayesianScoreQuery over BM25 and a kNN query) with LogOddsFusionQuery and search an index that has more than one segment. You expect every document to get the documented fused score, sigmoid(n^alpha * mean of the gated log-odds over all n clauses), which is also what explain reports, whichever segment holds the document. Instead, in a segment where only one clause has a scorer, the query hands back that clause's own scorer unchanged, so documents there get the raw clause score: in the repro two documents with identical content score 0.6386912 and 0.5532073, while explain reports 0.6386912 for both.

## Expected vs actual

- Expected: You expect every document to get the documented fused score, sigmoid(n^alpha * mean of the gated log-odds over all n clauses), which is also what explain reports, whichever segment holds the document.
- Actual: In a segment where only one clause has a scorer, the query hands back that clause's own scorer unchanged, so documents there get the raw clause score: in the repro two documents with identical content score 0.6386912 and 0.5532073, while explain reports 0.6386912 for both.

## Files

- [src/org/apache/lucene/search/TestLogOddsFusionSegmentShortcut.java](src/org/apache/lucene/search/TestLogOddsFusionSegmentShortcut.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestLogOddsFusionSegmentShortcut
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
