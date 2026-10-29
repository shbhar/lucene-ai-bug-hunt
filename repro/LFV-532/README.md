# NGramTokenFilter and EdgeNGramTokenFilter end overwrite the input's final position increment instead of adding to it, so a trailing hole (such as a removed trailing stop word) is lost and the next value of a multi-valued field starts too early (released since 7.4.0)

Bug LFV-532 · Area: analysis / common n-gram token filters (NGramTokenFilter, EdgeNGramTokenFilter: final position increment in end) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-532.html](../../bugs/LFV-532.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyse 'abc the' with a whitespace tokenizer, StopFilter and then NGramTokenFilter(3,3) or EdgeNGramTokenFilter(1,3); StopFilter reports the removed trailing 'the' as a final position increment of 1 in end, and because the n-gram filter drops nothing here you expect end to still report 1. Instead both filters call super.end and then replace that value with only the increments of tokens they dropped themselves, so end reports 0 ('abc x the' gives 1 instead of 2) and the trailing hole is lost. In a multi-valued field with Lucene's default gap 0, the second value 'def' of ['abc the', 'def'] is then indexed at position 1 instead of 2, and PhraseQuery('abc','def') matches the document (1 hit) where the same chain without the n-gram filter gives 0 hits (executed).

The test shows that `NGramTokenFilter.end()` overwrites the final position increment of its input, so the trailing hole `StopFilter` leaves for a removed last word is lost.

## Expected vs actual

For whitespace -&gt; `StopFilter("the")` -&gt; `NGramTokenFilter(3, 3)` on `abc the`, expected a final position increment of 1 after `end()`; actual 0, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ngram/TestNGramTokenFilterFinalPositionIncrement.java](src/org/apache/lucene/analysis/ngram/TestNGramTokenFilterFinalPositionIncrement.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ngram.TestNGramTokenFilterFinalPositionIncrement
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
