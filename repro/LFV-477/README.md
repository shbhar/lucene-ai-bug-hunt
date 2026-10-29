# DoubleMetaphoneFilter drops the alternate (secondary) code of a token whose text already equals its primary code, so 'JR' gets only JR while 'jr' gets JR and AR

Bug LFV-477 · Area: analysis / phonetic (DoubleMetaphoneFilter#incrementToken alternate-code branch; DoubleMetaphoneFilterFactory, SPI name doubleMetaphone) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-477.html](../../bugs/LFV-477.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run text through DoubleMetaphoneFilter, documented as 'supporting secondary codes', and expect the token 'JR' to get the same codes as 'jr', because the encoder is case-insensitive and gives both the primary code JR and the alternate code AR. Instead, when a token's text already equals its own primary code, the filter keeps the token as the primary but never emits the alternate: 'jr' gives JR and AR, 'JR' gives only JR, in both inject modes and at every maxCodeLength where the token equals its code. A document containing 'JR' is then missed by a query that matches only through AR (executed: TermQuery AR matches the document 'jr' but not the document 'JR'), while matches through the primary code still work.

The test shows that `DoubleMetaphoneFilter` drops the alternate code of a token whose text already equals its primary code.

## Expected vs actual

Expected `new DoubleMetaphoneFilter(whitespaceMockTokenizer("JR"), 4, true)` to emit `[JR, AR]` (the codes the encoder gives for both "JR" and "jr"); actual `[JR]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/phonetic/TestDoubleMetaphoneFilterTokenEqualsCode.java](src/org/apache/lucene/analysis/phonetic/TestDoubleMetaphoneFilterTokenEqualsCode.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/phonetic/build/libs/lucene-analysis-phonetic-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.phonetic.TestDoubleMetaphoneFilterTokenEqualsCode
```

The module(s) analysis-phonetic also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
