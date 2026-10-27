# Since 10.3.0, a field-sorted top-n search whose query fully matches a segment without deletions (MatchAllDocsQuery, a range covering the segment) no longer skips non-competitive documents: the comparator's initial all-docs competitive iterator reports a run to maxDoc through UpdateableDocIdSetIterator.docIDRunEnd, so DenseConjunctionBulkScorer hands the whole segment to the collector in one collectRange call (performance regression; results correct)

Bug LFV-150 · Area: search / sorting (TopFieldCollector competitive iterator x DenseConjunctionBulkScorer collectRange shortcut) · Priority: medium · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a top-10 search sorted on a LONG field with points (or a STRING field with a terms index, or a LONG field with a skip index) with MatchAllDocsQuery over one 100,000-doc segment that has no deletions. You expect Lucene to skip non-competitive documents, as 10.2.2 does for this exact search (about 1,000-1,750 docs collected) and as a TermQuery matching the same docs still does on main (about 8,200). Instead main and every release since 10.3.0 collect all 100,000 docs, because the comparator's competitive iterator starts as all docs and since #14532 reports a run up to maxDoc, so DenseConjunctionBulkScorer collects the whole segment in one collectRange call before the comparator narrows the iterator; the top hits are still correct, only the pruning is lost.

## Expected vs actual

- Expected: You expect Lucene to skip non-competitive documents, as 10.2.2 does for this exact search (about 1,000-1,750 docs collected) and as a TermQuery matching the same docs still does on main (about 8,200).
- Actual: Main and every release since 10.3.0 collect all 100,000 docs, because the comparator's competitive iterator starts as all docs and since #14532 reports a run up to maxDoc, so DenseConjunctionBulkScorer collects the whole segment in one collectRange call before the comparator narrows the iterator; the top hits are still correct, only the pruning is lost.

## Files

- [src/org/apache/lucene/search/TestSortPruningMatchAll.java](src/org/apache/lucene/search/TestSortPruningMatchAll.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestSortPruningMatchAll
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
