# JapaneseNumberFilter and KoreanNumberFilter honour KeywordAttribute only on the token that starts a number, so a keyword-protected numeral word directly after a numeral token is merged into the number and normalized (三 + protected 京一 gives 30000000000000001, the name 오경일 with 경일 protected gives 50000000000000001) (released since 6.0.0 / 8.2.0)

Bug LFV-360 · Area: analysis / kuromoji and nori number filters (JapaneseNumberFilter, KoreanNumberFilter: KeywordAttribute inside a number composition) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-360.html](../../bugs/LFV-360.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put SetKeywordMarkerFilter in front of JapaneseNumberFilter (or KoreanNumberFilter) to protect a word made of numeral characters, as the class javadoc suggests for the name 京一, and expect that word to come out unchanged wherever it occurs. That works when the word starts a number (田中京一 gives 田中, 京一, the upstream testName), but the filter checks the keyword flag only on the first token of a number and accepts every following numeral token without looking at it, so a protected word right after a numeral is appended to the number: 社員の三京一 gives the single token 30000000000000001 (offsets 3-6) instead of 3 and 京一, and the Korean name 오경일 (tokens 오 / 경일, 경일 protected) gives 50000000000000001. No error is raised; a TermQuery for the protected word then misses the document (executed).

The test shows that JapaneseNumberFilter folds a keyword-protected numeral word into the number when it directly follows a numeral token (`三京一` with `京一` marked by SetKeywordMarkerFilter).

## Expected vs actual

Expected the tokens `[3, 京一]`; actual the single token `30000000000000001` (`term 0 expected:<3[]> but was:<3[0000000000000001]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ja/TestJapaneseNumberFilterKeywordAfterNumeral.java](src/org/apache/lucene/analysis/ja/TestJapaneseNumberFilterKeywordAfterNumeral.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestJapaneseNumberFilterKeywordAfterNumeral
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
