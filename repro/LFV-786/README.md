# TSTLookup.build empties its tree before it reads the new input but resets its count only after the input is sorted, so a rebuild that throws while the input is read or sorted leaves a lookup that suggests nothing while getCount and store/load still report the previous build; lookups made while a rebuild reads its input also find nothing

Bug LFV-786 · Area: suggest / ternary search tree suggester (TSTLookup.build: rebuild failure atomicity and rebuild consistency) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TSTLookup from {foo/10, bar/5}, rebuild it from a dictionary whose stream throws IOException after its first line, catch the exception and keep using the lookup. You expect either the previous build (lookup('fo') returns foo and getCount 2), which FSTCompletionLookup, WFSTCompletionLookup and AnalyzingSuggester keep after the same failed rebuild, or an empty lookup with getCount 0. Instead lookup('fo') returns nothing while getCount still returns 2, and store+load carry that state into a new lookup, because build replaces the tree with an empty one before it reads and sorts the input and resets the count only after that; for the same reason a lookup made while a rebuild is still reading its input finds nothing.

## Expected vs actual

- Expected: You expect either the previous build (lookup('fo') returns foo and getCount 2), which FSTCompletionLookup, WFSTCompletionLookup and AnalyzingSuggester keep after the same failed rebuild, or an empty lookup with getCount 0.
- Actual: lookup('fo') returns nothing while getCount still returns 2, and store+load carry that state into a new lookup, because build replaces the tree with an empty one before it reads and sorts the input and resets the count only after that; for the same reason a lookup made while a rebuild is still reading its input finds nothing.

## Files

- [src/org/apache/lucene/search/suggest/tst/TestTSTLookupRebuild.java](src/org/apache/lucene/search/suggest/tst/TestTSTLookupRebuild.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.tst.TestTSTLookupRebuild
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
