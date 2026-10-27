# Nori's DictionaryBuilder checks that rows sharing a left context id have the same part of speech only with a Java assert, so with assertions off a custom system dictionary silently gives those words the part of speech of another row and KoreanPartOfSpeechStopFilter drops or keeps them wrongly

Bug LFV-717 · Area: analysis / nori system dictionary builder (DictionaryBuilder, TokenInfoDictionaryEntryWriter; custom system dictionaries) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile a custom nori system dictionary with DictionaryBuilder from MeCab-format rows in which the noun 가 (NNG) and the particle 는 (JX) share left context id 1, and expect the builder either to reject the source (the compiled format keeps only one part of speech per left id, and kuromoji's builder throws 'Multiple entries found for leftID' for the same case) or each word to keep its own part of speech. Instead, with assertions off, the builder overwrites the single slot for left id 1 with the part of speech of the row it writes last, because the only check is an assert (TokenInfoDictionaryEntryWriter.java:100): 가 then reports left POS JX, and KoreanTokenizer followed by KoreanPartOfSpeechStopFilter with DEFAULT_STOP_TAGS returns no token instead of [가]. With -ea the build fails with a bare AssertionError rather than an IllegalArgumentException that names the problem.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: With assertions off, the builder overwrites the single slot for left id 1 with the part of speech of the row it writes last, because the only check is an assert (TokenInfoDictionaryEntryWriter.java:100): 가 then reports left POS JX, and KoreanTokenizer followed by KoreanPartOfSpeechStopFilter with DEFAULT_STOP_TAGS returns no token instead of [가].

## Files

- [src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderLeftIdPosCollision.java](src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderLeftIdPosCollision.java) (JUnit test)
- [src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderLeftIdPosCollision9x.java](src/org/apache/lucene/analysis/ko/dict/TestNoriBuilderLeftIdPosCollision9x.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.dict.TestNoriBuilderLeftIdPosCollision

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.dict.TestNoriBuilderLeftIdPosCollision9x
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/cjk-compiled-dictionary-source-round-trip/](../../models/cjk-compiled-dictionary-source-round-trip/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
