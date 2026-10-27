# JapaneseIterationMarkCharFilter expands the unvoiced kana iteration mark ゝ/ヽ after the na-row, the small tsu and the handakuten kana (な に ぬ ね の, っ, ぱ ぴ ぷ ぺ and their katakana forms) to a different kana, so なゝめ becomes などめ instead of ななめ (のゝしる becomes のねしる, ぱゝ becomes ぱば), because kana that have no voiced form are stored as self-mappings in the voicing table and the 'is voiced' check reads every self-mapping as voiced

Bug LFV-607 · Area: analysis-kuromoji / Japanese char filters (JapaneseIterationMarkCharFilter kana iteration marks ゝ/ヽ: isHiraganaDakuten / isKatakanaDakuten over the h2d / k2d voicing tables, used by normalizedHiragana / normalizedKatakana; JapaneseIterationMarkCharFilterFactory 'japaneseIterationMark') · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put JapaneseIterationMarkCharFilter in front of an analyzer and index なゝめ, the older spelling of ななめ ('diagonal'), and expect the filter to replace the iteration mark ゝ with the repeated kana, as its javadoc promises ('normalizes... iteration marks to their expanded form'). Instead it emits などめ, so the index holds a different word and a search for ななめ finds nothing; the same happens for 19 more kana (のゝ becomes のね, ぱゝ becomes ぱば, っゝ becomes っぢ, and the katakana forms with ヽ). The filter decides whether the preceding kana is voiced by checking whether it maps to itself in its voicing table, but the kana that have no voiced form (っ, な..の, ぱ ぴ ぷ ぺ) are stored there as self-mappings too, so it strips a dakuten they never had and emits the preceding code point.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It emits などめ, so the index holds a different word and a search for ななめ finds nothing; the same happens for 19 more kana (のゝ becomes のね, ぱゝ becomes ぱば, っゝ becomes っぢ, and the katakana forms with ヽ).

## Files

- [src/org/apache/lucene/analysis/ja/TestIterationMarkUnvoicedKana.java](src/org/apache/lucene/analysis/ja/TestIterationMarkUnvoicedKana.java) (JUnit test)
- [src/IterationMarkUnvoicedKanaMain.java](src/IterationMarkUnvoicedKanaMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.analysis.ja.TestIterationMarkUnvoicedKana

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" IterationMarkUnvoicedKanaMain
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
