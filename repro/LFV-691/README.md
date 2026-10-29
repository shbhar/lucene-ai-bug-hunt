# RegexCompletionQuery lets '.' and negated character classes match the document suggester's internal end byte (0x00), so a suggestion one character shorter than the pattern is returned ('foo.' returns foo as well as food, 'foo..' returns food)

Bug LFV-691 · Area: suggest / document (NRT) suggester (RegexCompletionQuery automaton intersected with the NRTSuggester FST, including the internal END and dedup bytes; also inside ContextQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-691.html](../../bugs/LFV-691.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a RegexCompletionQuery, which 'takes a regular expression as the prefix of the query term', over the suggestions foo (weight 5), food (1) and fox (2), and expect 'foo.' and 'foo[^x]' to return only food (the only suggestion with a matching four-character prefix) and 'foo..' to return nothing (no suggestion has five characters). Instead 'foo.' and 'foo[^x]' return [foo, food] with foo ranked first, and 'foo..' returns [food]. The document suggester stores every suggestion in its FST as the analyzed text followed by an internal end byte 0x00 and dedup bytes, and RegexCompletionQuery hands the regex automaton, whose '.' and negated classes include U+0000, to the FST intersection unchanged, so the pattern runs past the end of the suggestion into these internal bytes.

The test shows that `RegexCompletionQuery("foo.")` over the suggestions foo and food also returns foo, because '.' matches the suggester's internal end byte 0x00.

## Expected vs actual

Expected `[food]`; actual `[foo, food]`: `expected:<[food]> but was:<[foo, food]>` (-ea and -da).

## Files

- [src/org/apache/lucene/search/suggest/document/TestRegexCompletionEndByte.java](src/org/apache/lucene/search/suggest/document/TestRegexCompletionEndByte.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.document.TestRegexCompletionEndByte
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
