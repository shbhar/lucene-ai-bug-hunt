# For a sloppy PhraseQuery with a repeated term (for example "very very"~1), Weight#matches reports an end position and end offset past the match, at a later occurrence of the repeated term that repeat-collision resolution has already advanced to, so Matches-based highlighting (the default UnifiedHighlighter) marks too much text

Finding LFV-200 · Area: search / phrase queries (SloppyPhraseMatcher Matches support: endPosition/endOffset read after advanceRpts) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by Aron Nopanen (aronnopanen-veeva; open PR #16913; an independent fix, earlier than our listing) on 2026-10-26 (https://github.com/apache/lucene/pull/16913). Open PR #16913 (aronnopanen-veeva, opened 2026-10-26, no milestone; open when read on 2026-11-20: a review of 2026-11-19 requested changes, and a second commit of that day changes code comments only) makes SloppyPhraseMatcher report the saved position and offsets of a repeated term that collision resolution has moved on. On a build of the pull request as of 2026-11-19 our repro passes 2 of 2; it fails 2 of 2 on main f2b061406c4b, with and without assertions. Hits, scores and explain values were unchanged against main in a comparison of 2026-11-14 on the first commit; the second commit changes no code. It describes its own highlighting report, links no issue and does not cite this list; it was opened before the list's first snapshot (2026-10-27). This site files nothing upstream.

## What it shows

You run a sloppy phrase query that repeats a word, such as "very very"~1, on the text 'very very good very' and ask Weight#matches (or the default UnifiedHighlighter) where it matched; you expect positions 0-1, 'very very'. Lucene reports positions 0-3, 'very very good very', because after finding the match the sloppy matcher moves the second 'very' on to the later occurrence to resolve a collision between the two copies of the term, and endPosition()/endOffset() are read only afterwards, from that moved state. The reported span is not the window of any valid match (positions 0 and 3 are two moves apart, the slop is 1); the hit itself and its score are correct.

## Expected vs actual

- Expected: You expect positions 0-1, 'very very'.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestSloppyPhraseMatchesRepeatInterval.java](src/org/apache/lucene/search/TestSloppyPhraseMatchesRepeatInterval.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSloppyPhraseMatchesRepeatInterval
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
