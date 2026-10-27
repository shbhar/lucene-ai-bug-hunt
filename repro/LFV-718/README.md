# With format="snowball", StopFilterFactory, KeepWordFilterFactory, CommonGramsFilterFactory and SuggestStopFilterFactory keep a leading UTF-8 byte order mark glued to the first word of the words file, so that word silently stops matching, while the default "wordset" format of the same factories strips the mark

Bug LFV-718 · Area: core + analysis-common + suggest / words-file factories (format="snowball" loader: AbstractAnalysisFactory.getSnowballWordSet, WordlistLoader.getSnowballWordSet) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure StopFilterFactory (or KeepWordFilterFactory, CommonGramsFilterFactory, CommonGramsQueryFilterFactory or SuggestStopFilterFactory) with format="snowball" and a UTF-8 words file that begins with a byte order mark, and expect the same words that the default "wordset" format reads from such a file, because that format skips a leading BOM (WordlistLoader.getLines, 'skip initial bom marker', a rule recorded in SOLR-1865). Instead the snowball loader keeps the invisible U+FEFF glued to the first word, so the set holds "\uFEFFthe" instead of "the" (getStopWords = [a, \uFEFFthe, an]): StopFilter no longer removes 'the', KeepWordFilter drops the tokens of the first keep word, CommonGrams does not treat it as common, and a BOM before a comment or blank first line adds a stray "\uFEFF" entry. No error is raised.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The snowball loader keeps the invisible U+FEFF glued to the first word, so the set holds "\uFEFFthe" instead of "the" (getStopWords = [a, \uFEFFthe, an]): StopFilter no longer removes 'the', KeepWordFilter drops the tokens of the first keep word, CommonGrams does not treat it as common, and a BOM before a comment or blank first line adds a stray "\uFEFF" entry.

## Files

- [src/org/apache/lucene/analysis/core/TestSnowballFormatUtf8Bom.java](src/org/apache/lucene/analysis/core/TestSnowballFormatUtf8Bom.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.core.TestSnowballFormatUtf8Bom
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
