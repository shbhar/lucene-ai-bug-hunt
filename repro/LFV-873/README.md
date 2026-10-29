# ThaiNormalizer looks for a decomposed Sara Am before it converts Lakkhangyao or removes an extra tone mark, so its output can still be the decomposed form (Nikhahit + tone + Sara Aa), and the default ThaiAnalyzer indexes น้ำ ('water') typed with two tone marks inside a decomposed Sara Am as a different term

Bug LFV-873 · Area: analysis-common / Thai (ThaiNormalizer.normalize rule order; ThaiNormalizationFilter, the 'thaiNormalization' factory and the default ThaiAnalyzer as consumers; new in 10.6.0) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-873.html](../../bugs/LFV-873.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Thai text with the default ThaiAnalyzer (or ThaiNormalizationFilter) in which the word น้ำ ('water') is typed with a decomposed Sara Am and an extra tone mark inside it, น + Nikhahit + Mai Ek + Mai Tho + Sara Aa, and you expect the term น้ำ, because ThaiNormalizer documents that it recomposes Nikhahit + tone + Sara Aa into Sara Am and (per its code comment and upstream test) keeps the last of two different tone marks. ThaiNormalizer tests for the decomposed Sara Am in one forward pass over the raw characters, before it converts a following Lakkhangyao and before its last step removes the extra or repeated tone mark, and it never checks again, so the output is นํ้า (U+0E19 U+0E4D U+0E49 U+0E32), still the decomposed form; a query for น้ำ misses the document, while a second normalize call, or the same two tone marks typed in the other order, gives น้ำ. When the token filter runs without ThaiCharFilter, ท + Nikhahit + Lakkhangyao likewise gives ทํา instead of ทำ, and a doubled tone mark inside the decomposed form gives นํ้า.

The test shows that the default `ThaiAnalyzer` (ThaiNormalizer) leaves a decomposed Sara Am in place when two different tone marks sit inside it: น + Nikhahit + Mai Ek + Mai Tho + Sara Aa is not recomposed to น้ำ ("water").

## Expected vs actual

`ThaiAnalyzer.normalize` should give U+0E19 U+0E49 U+0E33; actual U+0E19 U+0E4D U+0E49 U+0E32 (still Nikhahit + Sara Aa), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/th/TestThaiSaraAmTwoTones.java](src/org/apache/lucene/analysis/th/TestThaiSaraAmTwoTones.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.th.TestThaiSaraAmTwoTones
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
