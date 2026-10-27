# AnalyzingSuggester and FuzzySuggester do not escape the 0x00 byte that ends each analyzed form, so a U+0000 in a suggestion or key makes them return the same suggestion twice, lose the EXACT_FIRST exact match, or return entries that do not start with the key

Bug LFV-601 · Area: suggest / analyzing suggester (AnalyzingSuggester FST input encoding: analyzed form + END_BYTE 0x00 + dedup byte, inherited by FuzzySuggester; Util.TopNSearcher result path) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester (or a FuzzySuggester) with its default constructor and KeywordAnalyzer from two entries, 'cat' with weight 1 and 'cat\u0000\u0000s' with weight 5, and call lookup('cat', 5); you expect each entry once with the exact match 'cat' first, which is what you get when the second entry uses U+0001 instead of U+0000. Instead you get [cat/1, cat\u0000\u0000s/5, cat/1]: the suggester stores each entry as its analyzed bytes, then a 0x00 end marker, then a dedup byte, without escaping a 0x00 that belongs to the text, so the stored input of 'cat' is a prefix of the stored input of 'cat\u0000\u0000s', and the FST top-N search returns 'cat' a second time through a path that skips the suggester's duplicate filter. The same collision makes lookup('foo\u0000') return 'foo' and lets a longer U+0000-extended entry take the EXACT_FIRST exact-match slot.

## Expected vs actual

- Expected: You expect each entry once with the exact match 'cat' first, which is what you get when the second entry uses U+0001 instead of U+0000.
- Actual: You get [cat/1, cat\u0000\u0000s/5, cat/1]: the suggester stores each entry as its analyzed bytes, then a 0x00 end marker, then a dedup byte, without escaping a 0x00 that belongs to the text, so the stored input of 'cat' is a prefix of the stored input of 'cat\u0000\u0000s', and the FST top-N search returns 'cat' a second time through a path that skips the suggester's duplicate filter.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterZeroByte.java](src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterZeroByte.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterZeroByte
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
