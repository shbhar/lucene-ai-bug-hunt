# OpenNLPLemmatizerFilter silently drops keyword-marked tokens that end a sentence (and every token but the first of a sentence made only of keyword tokens), because since 9.5.0 it ends a sentence once it has used all lemmas, and keyword tokens get no lemma

Bug LFV-620 · Area: analysis-opennlp / OpenNLPLemmatizerFilter (sentence buffering, KeywordAttribute handling) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-620.html](../../bugs/LFV-620.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze text with OpenNLPTokenizer, OpenNLPPOSFilter, a keyword marker that protects some words (for example KeywordMarkerFilter with the pattern 'evening') and OpenNLPLemmatizerFilter, and expect the protected words to pass through unlemmatized, which is what the filter's KeywordAttribute handling is for. Instead 'They sent him running in the evening' becomes [they, send, he, run, in, the]: the protected last word is gone, and when every word of a sentence is protected only its first word survives ([They]). The filter ends a sentence once it has emitted one lemma per non-keyword token (OpenNLPLemmatizerFilter.java:64, a 9.5.0 change from #11734), so the keyword tokens after the last non-keyword token are thrown away.

The test shows that OpenNLPLemmatizerFilter drops a keyword-marked token that ends a sentence (opennlp tokenizer -&gt; POS -&gt; keywordMarker(pattern=evening) -&gt; dictionary lemmatizer, module test models).

## Expected vs actual

Expected [they, send, he, run, in, the, evening] for "They sent him running in the evening"; actual [they, send, he, run, in, the] on main, with -ea and -da.

## Files

- [src/org/apache/lucene/analysis/opennlp/TestLemmatizerSentenceFinalKeyword.java](src/org/apache/lucene/analysis/opennlp/TestLemmatizerSentenceFinalKeyword.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/opennlp/build/libs/lucene-analysis-opennlp-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.opennlp.TestLemmatizerSentenceFinalKeyword
```

The module(s) analysis-opennlp also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
