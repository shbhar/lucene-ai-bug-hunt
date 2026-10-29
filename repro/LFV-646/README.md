# AnalyzingInfixSuggester.build can commit and then throw, and its catch block leaves writer == null, so refresh becomes a no-op: the old suggestions stay served while the directory holds the build that threw, and the next add/update or a reopen silently switches to it (default settings, since 9.2.0)

Bug LFV-646 · Area: suggest / analyzing infix suggester (AnalyzingInfixSuggester.build failure path; inherited unchanged by BlendedInfixSuggester) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-646.html](../../bugs/LFV-646.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You rebuild an AnalyzingInfixSuggester with default settings, build throws an IOException, you call refresh and keep using the instance, and you expect it to serve what its index holds, so that a later add adds exactly one suggestion. Instead build has already committed the new index (closeIndexWriterOnBuild defaults to true) before it opens the SearcherManager on it; when that open fails, the catch block rolls back (which cannot undo the commit), sets writer = null and keeps the old SearcherManager, so refresh is a no-op, lookups serve the old suggestions ([old apple]) while the directory holds the failed build ([new avocado]), and after the next add lookups return [another apricot, new avocado], with the old suggestion gone. A second trigger needs no I/O fault: build first closes, and so commits, a writer that holds an add or update not yet refreshed, and if the rebuild's input then throws, refresh never shows that committed change.

The test shows that AnalyzingInfixSuggester.build() (default settings) commits, then throws while it opens the new searcher. refresh() becomes a no-op, lookups keep serving the old suggestions, and the next add() silently switches to the failed build.

## Expected vs actual

Expected: after the failed build and refresh(), add() + refresh() serves [another apricot, old apple], which is what was served plus the one added suggestion. Actual (-ea and -da): [another apricot, new avocado].

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestInfixFailedBuildAfterCommit.java](src/org/apache/lucene/search/suggest/analyzing/TestInfixFailedBuildAfterCommit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestInfixFailedBuildAfterCommit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
