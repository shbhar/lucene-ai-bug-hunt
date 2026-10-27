# OpenNLPOpsFactory.getLemmatizerDictionary never closes the lemma-dictionary InputStream it opens from the ResourceLoader, so every OpenNLPLemmatizerFilterFactory with a 'dictionary' leaves that file open until garbage collection (regression since 9.1.0: LUCENE-10171 undid the LUCENE-8188 fix)

Bug LFV-461 · Area: analysis-opennlp / lemma dictionary loading (OpenNLPOpsFactory.getLemmatizerDictionary) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure the OpenNLP lemmatizer with a dictionary file (for example CustomAnalyzer.builder(dir).addTokenFilter("openNlpLemmatizer", "dictionary", "lemmas.dict")) and expect Lucene to close the file once it has read it, as it does for every other OpenNLP model and as it did for this dictionary up to 9.0.0. Instead OpenNLPOpsFactory.getLemmatizerDictionary passes loader.openResource(dictionaryFile) straight to OpenNLP's DictionaryLemmatizer, which reads the stream to the end but does not close it, so the file stays open after the analyzer is built and even after Analyzer.close, until garbage collection reclaims the stream; Lucene's own LeakFS test filesystem reports 'file handle leaks: [InputStream(.../lemmas-...dict)]'. The lemmas themselves are correct; only the file handle leaks.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: OpenNLPOpsFactory.getLemmatizerDictionary passes loader.openResource(dictionaryFile) straight to OpenNLP's DictionaryLemmatizer, which reads the stream to the end but does not close it, so the file stays open after the analyzer is built and even after Analyzer.close, until garbage collection reclaims the stream; Lucene's own LeakFS test filesystem reports 'file handle leaks: [InputStream(.../lemmas-...dict)]'.

## Files

- [src/org/apache/lucene/analysis/opennlp/TestOpenNLPLemmaDictionaryStreamClosed.java](src/org/apache/lucene/analysis/opennlp/TestOpenNLPLemmaDictionaryStreamClosed.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/opennlp/build/libs/lucene-analysis-opennlp-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.opennlp.TestOpenNLPLemmaDictionaryStreamClosed
```

The module(s) analysis-opennlp also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
