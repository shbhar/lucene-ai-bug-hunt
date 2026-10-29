# Kuromoji and Nori UserDictionary.open keep a leading UTF-8 byte order mark, so Nori silently drops the first user-dictionary entry and Kuromoji refuses to load the file

Bug LFV-950 · Area: analysis-kuromoji + analysis-nori / user dictionary loading (UserDictionary.open, reached through the JapaneseTokenizerFactory / KoreanTokenizerFactory userDictionary argument) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-950.html](../../bugs/LFV-950.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give KoreanTokenizerFactory or JapaneseTokenizerFactory a user dictionary file that an editor saved as UTF-8 with a byte order mark (bytes EF BB BF), and expect the same dictionary as from the file without the mark, because Lucene's shared resource-line loader skips a leading BOM for the other file-based analysis factories (WordlistLoader.getLines, 'skip initial bom marker', SOLR-1865). Instead both factories decode the file themselves and pass it to UserDictionary.open, which keeps the invisible U+FEFF on line 1: in Nori the first entry becomes '\uFEFF세종시', which no text contains, so 세종시 is segmented [세, 종시] instead of the user's [세종, 시] with no error. Kuromoji (9.0.0 and later) rejects the file with 'Illegal user dictionary entry \uFEFF関西国際空港 - the concatenated segmentation (関西国際空港) does not match the surface form (\uFEFF関西国際空港)', and when line 1 is a '#' comment or blank it throws ArrayIndexOutOfBoundsException 'Index 1 out of bounds for length 1', because the BOM hides the comment from its 'skip empty lines or comment lines' check.

The test shows that a Nori user dictionary that starts with a UTF-8 byte order mark (`UserDictionary.open("\uFEFF세종시 세종 시\n")`) silently loses its first entry.

## Expected vs actual

Expected `KoreanTokenizer` to segment 세종시 as [세종, 시], as without the mark; actual: [세, 종시] (`term 0 expected:<세[종]> but was:<세[]>`), with -ea and -da.

## Files

- [src/org/apache/lucene/analysis/ko/TestNoriUserDictionaryUtf8Bom.java](src/org/apache/lucene/analysis/ko/TestNoriUserDictionaryUtf8Bom.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.TestNoriUserDictionaryUtf8Bom
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
