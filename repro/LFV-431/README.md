# AnalyzingSuggester and FuzzySuggester getCount counts analyzed paths instead of entries when the analyzer produces a token graph (synonyms, word-delimiter graphs), and store/load persist the inflated count

Bug LFV-431 · Area: suggest / analyzing suggester (AnalyzingSuggester.build and getCount, inherited by FuzzySuggester) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester or FuzzySuggester from the two entries 'fast car' and 'red car' with an analyzer whose synonym rule maps fast to {fast, quick}, and you expect getCount to return 2, because Lookup#getCount is documented as 'the number of entries the lookup was built with'. It returns 3: build increments its counter once for every analyzed path of an entry ('fast car' and 'quick car' here), not once per entry, so each entry counts as often as its token graph has paths (8 for the single entry 'wifi wifi wifi' with one stacked synonym), and store and load keep the inflated number. Lookups are correct, and analyzers that produce no token graph give the right count.

The test shows that AnalyzingSuggester.getCount() counts analyzed paths, not entries: one entry 'fast' with the synonym fast -&gt; {fast, quick} is counted twice.

## Expected vs actual

Expected getCount() 1 (Lookup#getCount: "the number of entries the lookup was built with"); actual 2.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterGetCount.java](src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterGetCount.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterGetCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
