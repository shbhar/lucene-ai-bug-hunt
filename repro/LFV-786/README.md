# TSTLookup.build empties its tree before it reads the new input but resets its count only after the input is sorted, so a rebuild that throws while the input is read or sorted leaves a lookup that suggests nothing while getCount and store/load still report the previous build; lookups made while a rebuild reads its input also find nothing

Bug LFV-786 · Area: suggest / ternary search tree suggester (TSTLookup.build: rebuild failure atomicity and rebuild consistency) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TSTLookup from {foo/10, bar/5}, rebuild it from a dictionary whose stream throws IOException after its first line, catch the exception and keep using the lookup. You expect either the previous build (lookup('fo') returns foo and getCount 2), which FSTCompletionLookup, WFSTCompletionLookup and AnalyzingSuggester keep after the same failed rebuild, or an empty lookup with getCount 0. Instead lookup('fo') returns nothing while getCount still returns 2, and store+load carry that state into a new lookup, because build replaces the tree with an empty one before it reads and sorts the input and resets the count only after that; for the same reason a lookup made while a rebuild is still reading its input finds nothing.

The test shows that `TSTLookup` returns no suggestions while `build()` is still reading its new input, using upstream's own `SuggestRebuildTestUtil.testLookupsDuringReBuild` (LUCENE-10292), which the FST/WFST/Analyzing suggesters pass.

## Expected vs actual

Expected `lookup("ba")` to keep returning `bar` during the rebuild; actual: no suggestions (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/suggest/tst/TestTSTLookupDuringRebuild.java](src/org/apache/lucene/search/suggest/tst/TestTSTLookupDuringRebuild.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.tst.TestTSTLookupDuringRebuild
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
