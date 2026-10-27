# OpenNLP POS, chunker and lemmatizer filters emit no token for a whole field value when an earlier filter removes every token of its first sentence, because SentenceAttributeExtractor starts at sentence index 0 and reads a first token from sentence 1 as an empty sentence, which the filters take as the end of the stream

Bug LFV-621 · Area: analysis-opennlp / sentence-aware filters (SentenceAttributeExtractor.extractSentenceAttributes, shared by OpenNLPPOSFilter, OpenNLPChunkerFilter and OpenNLPLemmatizerFilter; factories opennlpPOS, opennlpChunker, opennlplemmatizer) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an OpenNLP chain that removes some tokens before a sentence-aware filter, for example tokenizer -&gt; POS tagger -&gt; TypeTokenFilter keeping only nouns -&gt; lemmatizer, and analyze 'He did not come back. They sent him running in the evening.', expecting the lemmatized nouns of the second sentence, [they, evening], as you get for that sentence alone. Instead the whole value yields no token: SentenceAttributeExtractor starts with currSentence = 0, so when the first token it receives already belongs to sentence 1 it returns an empty first sentence, and the POS, chunker and lemmatizer filters all treat an empty sentence as the end of the stream. A fully removed sentence in the middle of the text is handled correctly; only the first one is not.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The whole value yields no token: SentenceAttributeExtractor starts with currSentence = 0, so when the first token it receives already belongs to sentence 1 it returns an empty first sentence, and the POS, chunker and lemmatizer filters all treat an empty sentence as the end of the stream.

## Files

- [src/org/apache/lucene/analysis/opennlp/TestSentenceFiltersFirstSentenceRemoved.java](src/org/apache/lucene/analysis/opennlp/TestSentenceFiltersFirstSentenceRemoved.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/opennlp/build/libs/lucene-analysis-opennlp-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 1880C0DE
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=1880C0DE \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.opennlp.TestSentenceFiltersFirstSentenceRemoved
```

The module(s) analysis-opennlp also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
