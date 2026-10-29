# Suggest Lookup.load assigns its count and model fields while it is still reading the stream, so a load that throws (a stored copy cut short, a read failure, or FreeTextSuggester rejecting a model saved with other settings) leaves a mix of the previous and the new model: TSTLookup serves part of the new tree and some lookups throw NullPointerException, AnalyzingSuggester/FuzzySuggester can decode the new FST with the old payload flag, FreeTextSuggester scores the old FST with the new token total, and FST/WFSTCompletionLookup report the new count over the old FST

Bug LFV-979 · Area: suggest / Lookup persistence (load failure atomicity: TSTLookup, AnalyzingSuggester, FuzzySuggester, FreeTextSuggester, FSTCompletionLookup, WFSTCompletionLookup) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TSTLookup from {aa, ab, ba, bb, cア}, then call load with a stored copy of another model that is cut short or whose stream throws IOException part-way, and catch the exception. You expect the lookup to hold either its previous data or no data, but it reports the new model's getCount and serves part of the new tree, and some lookups throw NullPointerException; the same happens when you load into a newly constructed TSTLookup. The other Lookups do the same in their own way, because each load assigns count and model fields one after another as it reads: AnalyzingSuggester/FuzzySuggester can decode the new FST with the old payload flag (payload glued to the key), FreeTextSuggester scores the old FST with the new token total and changes getCount even when it rejects a complete model saved with other grams/separator settings, and FST/WFSTCompletionLookup report the new count over the old FST.

The test shows that TSTLookup.load() sets its count before it reads the tree, so a load of a stored copy cut short by one byte throws but leaves a new TSTLookup reporting the stored copy's count.

## Expected vs actual

Expected: after the failed `load()`, `getCount()` is 0 (the new lookup's previous, empty state). Actual: `getCount() after a failed load() expected:<0> but was:<2>`.

## Files

- [src/org/apache/lucene/search/suggest/TestTSTLookupFailedLoad.java](src/org/apache/lucene/search/suggest/TestTSTLookupFailedLoad.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.TestTSTLookupFailedLoad
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
