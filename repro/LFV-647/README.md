# SpellChecker.indexDictionary de-duplicates against its cached searcher instead of the index its new IndexWriter writes, so a rebuild with OpenMode.CREATE silently drops every word of the new dictionary that the old spell index already had (and after a failed searcher reopen, later calls drop words or index them twice)

Bug LFV-647 · Area: suggest / spell: SpellChecker.indexDictionary de-duplication (cached searcher vs. the new IndexWriter's index; clearIndex, swapSearcher) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a spell index with SpellChecker.indexDictionary from the words {apple, banana}, then rebuild it from {apple, cherry}, passing an IndexWriterConfig with OpenMode.CREATE, which is documented as 'Creates a new index or overwrites an existing one'. You expect the spell index to hold apple and cherry. Instead it holds only cherry: the new writer starts from an empty index, but indexDictionary skips every word that its cached searcher of the old index still contains, so apple is silently dropped; the same stale check makes a call after a failed searcher reopen drop words (after clearIndex) or index them twice (on a retry).

## Expected vs actual

- Expected: You expect the spell index to hold apple and cherry.
- Actual: It holds only cherry: the new writer starts from an empty index, but indexDictionary skips every word that its cached searcher of the old index still contains, so apple is silently dropped; the same stale check makes a call after a failed searcher reopen drop words (after clearIndex) or index them twice (on a retry).

## Files

- [src/org/apache/lucene/search/spell/TestSpellCheckerStaleDedup.java](src/org/apache/lucene/search/spell/TestSpellCheckerStaleDedup.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestSpellCheckerStaleDedup
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
