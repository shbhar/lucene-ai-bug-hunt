# FreeTextSuggester.build resets its unigram token total before it reads the input, so a rebuild that throws (an empty dictionary, an IOException from the input, or build's own separator-byte rejection) leaves the previous model scored against a zero or partial total: unigram suggestions get wrong scores (every one Long.MAX_VALUE, i.e. 1.0, when the total is 0), a backed-off unigram outranks the real bigram predictions, and store persists it

Bug LFV-982 · Area: suggest / FreeTextSuggester (n-gram suggester; build failure atomicity of the unigram token total) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-982.html](../../bugs/LFV-982.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FreeTextSuggester (bigram model) from {foo bar baz, foo bar, foo bee, fun}, so lookup('f') scores foo 3/8 and fun 1/8 and lookup('foo b') ranks 'foo bar' (2/3) and 'foo bee' (1/3) above the backed-off unigram baz (0.4 x 1/8); then you rebuild the same instance from an empty dictionary, or one whose next throws IOException, catch the exception and keep using it. You expect the previous model to be untouched, as its FST and getCount are, because build publishes those only after success. Instead build has already set the unigram token total to 0, so foo and fun both score Long.MAX_VALUE (1.0), baz jumps to first place at 1.0 ahead of both real bigram predictions, and store writes the zero total so a reloaded copy is damaged the same way.

The test shows that FreeTextSuggester.build() resets its unigram token total before it reads the input, so a rebuild that throws (here build(InputIterator.EMPTY), rejected with IllegalArgumentException) leaves the previous model scored against a total of 0.

## Expected vs actual

Expected: lookup("f") unchanged, [foo/3458764513820540928, fun/1152921504606846976] (3/8 and 1/8 of Long.MAX_VALUE). Actual (-ea and -da): [foo/9223372036854775807, fun/9223372036854775807].

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterFailedRebuild.java](src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterFailedRebuild.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestFreeTextSuggesterFailedRebuild
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
