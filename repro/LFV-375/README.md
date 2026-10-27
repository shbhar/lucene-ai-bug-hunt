# Hunspell COMPLEXPREFIXES: after removing the second prefix the Stemmer keeps removing prefixes, so words with three prefixes are accepted and a zero-length prefix recurses until StackOverflowError (also in HunspellStemFilter, where it closes the IndexWriter)

Bug LFV-375 · Area: analysis / hunspell (Stemmer affix removal) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a hunspell dictionary that sets COMPLEXPREFIXES (twofold prefix stripping, meant for morphologically complex right-to-left languages) and chains prefixes through continuation classes, and expect at most two prefixes per word, as hunspell(5), C Hunspell and Lucene up to 9.4.2 do. Instead, after removing the second prefix the Stemmer goes on removing prefixes: with prefixes a, b, c chained on root x, the three-prefix word cbax is accepted by spell and stemmed to x, and with a zero-length prefix that only passes on a flag (PFX A 0 0/B), the same prefix is removed again and again without shortening the word, so spell("bbx"), getRoots("bx") and HunspellStemFilter throw StackOverflowError. Inside IndexWriter.addDocument that Error closes the writer, and every later addDocument throws AlreadyClosedException.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: After removing the second prefix the Stemmer goes on removing prefixes: with prefixes a, b, c chained on root x, the three-prefix word cbax is accepted by spell and stemmed to x, and with a zero-length prefix that only passes on a flag (PFX A 0 0/B), the same prefix is removed again and again without shortening the word, so spell("bbx"), getRoots("bx") and HunspellStemFilter throw StackOverflowError.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestComplexPrefixesThirdPrefix.java](src/org/apache/lucene/analysis/hunspell/TestComplexPrefixesThirdPrefix.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestComplexPrefixesThirdPrefix
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
