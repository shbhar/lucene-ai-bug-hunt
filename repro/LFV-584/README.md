# QueryBuilder drops every token boost (BoostAttribute) from a quoted phrase as soon as one of its words has a single-word synonym, so typed word|weight boosts and weighted-synonym weights are silently ignored there, while plain phrases, graph phrases and unquoted queries keep them

Bug LFV-584 · Area: core / util QueryBuilder phrase building with token boosts (analyzeMultiPhrase; BoostAttribute from DelimitedBoostTokenFilter and weighted synonyms; reached through the classic, multi-field and simple query parsers) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-584.html](../../bugs/LFV-584.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give a phrase a token boost, for example "fast|0.5 car" through DelimitedBoostTokenFilter, or use weighted synonyms such as 'cat, kitty|0.5', and expect the quoted phrase to keep the boost the way QueryBuilder's other paths do: "fast|0.5 tiger" becomes (body:"fast tiger")^0.5, and a multi-word weighted synonym gives one boosted phrase per path. But as soon as one word of the phrase has a single-word synonym ('car, automobile'), QueryBuilder builds a MultiPhraseQuery in analyzeMultiPhrase, which never reads BoostAttribute: "fast|0.5 car" becomes the unboosted body:"fast (automobile car)" (score 0.2615 instead of 0.1308), and the quoted "cat food" scores 'kitty food' the same as 'cat food' (0.7130 both), although the unquoted query ranks it lower. The matching documents are unchanged; only scores and ranking are wrong, and there is no error.

The test shows that `QueryBuilder.createPhraseQuery` drops the token boost of a phrase once one of its words has a single-word synonym: `"fast|0.5 car"` with the synonym car -&gt; automobile builds an unboosted `MultiPhraseQuery`, while `"fast|0.5 tiger"` gives `(body:"fast tiger")^0.5`.

## Expected vs actual

Expected boost 0.5; actual 1.0 (`body:"fast (automobile car)" expected:<0.5> but was:<1.0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/util/TestQueryBuilderMultiPhraseBoost.java](src/org/apache/lucene/util/TestQueryBuilderMultiPhraseBoost.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestQueryBuilderMultiPhraseBoost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
