# Kuromoji's DictionaryBuilder silently accepts a reading or pronunciation of 128 or more chars and stores its length in 7 bits, so the built system dictionary returns an empty or truncated reading and a garbage pronunciation (a too-long base form, by contrast, is rejected)

Bug LFV-712 · Area: analysis / kuromoji system dictionary builder (ja.dict.TokenInfoDictionaryEntryWriter reading and pronunciation length bytes; TokenInfoMorphData decoding; custom dictionaries built with DictionaryBuilder) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-712.html](../../bugs/LFV-712.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a custom kuromoji system dictionary with DictionaryBuilder from an IPADIC-format CSV that has a row whose reading is 128 katakana chars long (or, more naturally, a 64-char proper noun with a 136-char reading), and you expect either an IllegalArgumentException at build time, as the builder throws for a stored base form of 16 or more chars that its compact format cannot hold, or the reading back unchanged. Instead the build succeeds, because putEntry writes each length as (byte) (length &lt;&lt; 1 | kanaFlag) with no check, so the stored length is the real length mod 128: getReading and JapaneseTokenizer's ReadingAttribute return "" for the 128-char reading and only the first 8 chars of the 136-char one, a 128-char pronunciation comes back empty, a pronunciation stored after a wrapped reading is decoded from the reading's own bytes (a 4-char pronunciation came back as the single char U+0304), and JapaneseReadingFormFilter indexes the empty or truncated reading as the term.

## Expected vs actual

- Expected: Each long row is rejected at build time with an `IllegalArgumentException`, or reads back as written: 136 or 128 chars of reading, pronunciation and term.
- Actual: 2 of 4 tests fail: the 136-char row gives reading コクリツケンキュ (8 chars), pronunciation U+0B04 U+2F24 U+3B06 and term コクリツケンキュ; the 128-char row gives "" for all three.

## Files

- [src/org/apache/lucene/analysis/ja/dict/TestKuromojiBuilderReadingLength.java](src/org/apache/lucene/analysis/ja/dict/TestKuromojiBuilderReadingLength.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.dict.TestKuromojiBuilderReadingLength
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDictionaryBuilderLongReading.java](TestDictionaryBuilderLongReading.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
