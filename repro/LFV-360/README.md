# JapaneseNumberFilter and KoreanNumberFilter honour KeywordAttribute only on the token that starts a number, so a keyword-protected numeral word directly after a numeral token is merged into the number and normalized (三 + protected 京一 gives 30000000000000001, the name 오경일 with 경일 protected gives 50000000000000001) (released since 6.0.0 / 8.2.0)

Bug LFV-360 · Area: analysis / kuromoji and nori number filters (JapaneseNumberFilter, KoreanNumberFilter: KeywordAttribute inside a number composition) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-360.html](../../bugs/LFV-360.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put SetKeywordMarkerFilter in front of JapaneseNumberFilter (or KoreanNumberFilter) to protect a word made of numeral characters, as the class javadoc suggests for the name 京一, and expect that word to come out unchanged wherever it occurs. That works when the word starts a number (田中京一 gives 田中, 京一, the upstream testName), but the filter checks the keyword flag only on the first token of a number and accepts every following numeral token without looking at it, so a protected word right after a numeral is appended to the number: 社員の三京一 gives the single token 30000000000000001 (offsets 3-6) instead of 3 and 京一, and the Korean name 오경일 (tokens 오 / 경일, 경일 protected) gives 50000000000000001. No error is raised; a TermQuery for the protected word then misses the document (executed).

## Expected vs actual

- Expected: `社員`, `の`, `3`, `京一` for `社員の三京一`, and `5`, `경일` for `오경일`: the protected word comes out unchanged.
- Actual: `社員`, `の`, `30000000000000001` (offsets 3-6) and `50000000000000001` (offsets 0-3): the protected word is merged into the number.

## Files

- [src/org/apache/lucene/analysis/ja/TestJapaneseNumberFilterKeywordInsideNumber.java](src/org/apache/lucene/analysis/ja/TestJapaneseNumberFilterKeywordInsideNumber.java) (JUnit test)
- [src/org/apache/lucene/analysis/ko/TestKoreanNumberFilterKeywordInsideNumber.java](src/org/apache/lucene/analysis/ko/TestKoreanNumberFilterKeywordInsideNumber.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestJapaneseNumberFilterKeywordInsideNumber \
       --select-class org.apache.lucene.analysis.ko.TestKoreanNumberFilterKeywordInsideNumber
```

The module(s) analysis-kuromoji, analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestJapaneseNumberFilterKeywordAfterNumeral.java](TestJapaneseNumberFilterKeywordAfterNumeral.java)
- [TestKoreanNumberFilterKeywordAfterNumeral.java](TestKoreanNumberFilterKeywordAfterNumeral.java)
- [fixAC.diff](fixAC.diff)
- [fixA-with-tests.diff](fixA-with-tests.diff)
- [TestJapaneseNumberFilterKeywordInsideNumber.java](TestJapaneseNumberFilterKeywordInsideNumber.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
