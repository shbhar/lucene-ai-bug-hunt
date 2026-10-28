# SpellChecker.indexDictionary checks for already-indexed words only against the spell index as it was before the call, so a word that one dictionary repeats is indexed once per occurrence and suggestSimilar returns it several times, pushing distinct suggestions out

Bug LFV-518 · Area: suggest / spell: SpellChecker.indexDictionary (n-gram spell index build) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-518.html](../../bugs/LFV-518.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a word list into SpellChecker with one indexDictionary call, and the list contains a word twice, for example a PlainTextDictionary with the lines relieve, relieve, receive. You expect each word to be indexed once, as indexDictionary already ensures for words that were in the spell index before the call, so suggestSimilar("recieve", 2) returns [relieve, receive]. Instead the duplicate check looks only at the spell index as it was before the call, so the repeated word is indexed twice and suggestSimilar returns [relieve, relieve], pushing receive out; the same words split over two calls are de-duplicated correctly.

## Expected vs actual

- Expected: `[relieve, receive]` and one spell-index document for `relieve` in all four tests.
- Actual: `[relieve, relieve]` and 2 documents for `relieve` in the three one-call tests; the split-over-two-calls control passes.

## Files

- [src/org/apache/lucene/search/spell/TestSpellCheckerDuplicateDictionaryWords.java](src/org/apache/lucene/search/spell/TestSpellCheckerDuplicateDictionaryWords.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.search.spell.TestSpellCheckerDuplicateDictionaryWords
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSpellCheckerRepeatedDictionaryEntries.java](TestSpellCheckerRepeatedDictionaryEntries.java)
- [TestSpellCheckerDuplicateDictionaryWords.java](TestSpellCheckerDuplicateDictionaryWords.java)
- [TestSpellCheckerRepeatedEntries.java](TestSpellCheckerRepeatedEntries.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
