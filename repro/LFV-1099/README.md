# SynonymGraphFilter drops the position hole (for example a stop word removed before it) in front of a token that starts a synonym match, so the replacement moves one position back (known: #10028 / LUCENE-8985) (released)

Finding LFV-1099 · Area: analysis / synonyms (SynonymGraphFilter position increments) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run 'run the fast' through StopFilter('the') and SynonymGraphFilter with the rule fast -&gt; quick and expect run +1, quick +2, as a non-matching token keeps its hole. Instead you get run +1, quick +1: the match is placed at the end node of the previous output token and the matched token's own position increment is ignored.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get run +1, quick +1: the match is placed at the end node of the previous output token and the matched token's own position increment is ignored.

## Files

- [src/org/apache/lucene/analysis/synonym/TestSynonymGraphFilterHoleBeforeMatch.java](src/org/apache/lucene/analysis/synonym/TestSynonymGraphFilterHoleBeforeMatch.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.analysis.synonym.TestSynonymGraphFilterHoleBeforeMatch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
