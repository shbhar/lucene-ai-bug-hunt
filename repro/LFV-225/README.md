# ThaiTokenizer and ThaiAnalyzer silently drop a number written directly after a period, '#' or currency sign (2567 in 'พ.ศ.2567', 1,500 in 'ราคา ฿1,500', 42 in 'page.42'), because a word segment is skipped whole when its first character is not a letter or digit

Bug LFV-225 · Area: analysis / Thai (ThaiTokenizer#incrementWord skip loop; ThaiAnalyzer and the 'thai' tokenizer SPI) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-225.html](../../bugs/LFV-225.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Thai text such as 'ประชุมวันที่ 1 ม.ค.2567' or 'ราคา ฿1,500' with the stock ThaiAnalyzer and expect the year 2567 or the amount 1,500 to be searchable, as they are when written 'พ.ศ. 2567' or 'ราคา 1,500 บาท'. Instead the number never reaches the index: the JDK word BreakIterator returns it together with its leading period or currency sign as one segment ('.2567', '฿1,500'), and ThaiTokenizer skips any segment whose first character is not a letter or digit, so the whole number is thrown away. A TermQuery for 2567 then finds 0 documents instead of 1, with no error.

The test shows that the stock `ThaiAnalyzer` drops the year in 'พ.ศ.2567' (Buddhist Era 2567), because `ThaiTokenizer` skips the whole word segment '.2567' when its first character is not a letter or digit.

## Expected vs actual

Expected tokens: [พ, ศ, 2567]. Actual: [พ, ศ] (`AssertionError: token 2 does not exist`).

## Files

- [src/org/apache/lucene/analysis/th/TestThaiAnalyzerNumberAfterPeriod.java](src/org/apache/lucene/analysis/th/TestThaiAnalyzerNumberAfterPeriod.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.th.TestThaiAnalyzerNumberAfterPeriod
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
