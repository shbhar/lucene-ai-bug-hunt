# Kuromoji's romaji reading (ToStringUtil.getRomanization, used by JapaneseReadingFormFilter with useRomaji=true) writes the small tsu ッ as a doubled consonant only before the k, s, t and p rows and silently drops it before any other kana, so loanwords with ッ before g, z/j, d, b or h/f lose it: バッグ 'bag' and バグ 'bug' both become 'bagu', ベッド becomes 'bedo'

Bug LFV-605 · Area: analysis / kuromoji romanization (ToStringUtil.getRomanization; JapaneseReadingFormFilter romaji mode) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-605.html](../../bugs/LFV-605.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Japanese text with JapaneseTokenizer and JapaneseReadingFormFilter(useRomaji=true), whose romanizer is documented as 'Romanize katakana with modified hepburn', and expect the small tsu ッ to double the next consonant, as the method already does for キット -&gt; 'kitto' and マッチ -&gt; 'matchi'. Instead the 'ッ' case appends a consonant only for the カ, サ, タ and パ rows and appends nothing otherwise, so バッグ 'bag' becomes 'bagu', the same term as バグ 'bug', ベッド becomes 'bedo' and スタッフ becomes 'sutafu'. Distinct words then match each other, and a romaji query 'baggu' finds no document that contains バッグ.

The test shows that Kuromoji's `ToStringUtil.getRomanization` drops the small tsu ッ before the ガ row instead of doubling the consonant, as it does before k/s/t/p (キット -&gt; kitto).

## Expected vs actual

Expected `getRomanization("バッグ")` to be "baggu"; actual "bagu" (the romaji of バグ), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ja/dict/TestToStringUtilSokuon.java](src/org/apache/lucene/analysis/ja/dict/TestToStringUtilSokuon.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.dict.TestToStringUtilSokuon
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
