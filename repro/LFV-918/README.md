# RussianAnalyzer does not remove its default stop words when they are written with ё (её, ещё, всё, неё, чём): its StopFilter runs before the stemmer folds ё to е, although the bundled stop list says ё is translated to е

Bug LFV-918 · Area: analysis-common / Russian analyzer default stop words (RussianAnalyzer, snowball/russian_stop.txt; interaction with the Snowball RussianStemmer's ё-to-е fold) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Russian text with the stock RussianAnalyzer. Its default stop list, the bundled Snowball russian_stop.txt, says in its header that 'letter ё is translated to е', so it lists её, ещё, всё, неё and чём only as ее, еще, все, нее and чем, and since 9.0 the same analyzer without stop words maps the ё and е spellings of every word that reaches its stemmer to one term. But the StopFilter runs before the only step that folds ё to е (the Snowball RussianStemmer), so the е spellings are removed while the ё spellings pass the StopFilter and are indexed as е, ещ, все, не and чем: 'Он все еще читает книгу' gives [чита, книг] but 'Он всё ещё читает книгу' gives [все, ещ, чита, книг], with no error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/ru/TestRussianAnalyzerYoStopWords.java](src/org/apache/lucene/analysis/ru/TestRussianAnalyzerYoStopWords.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ru.TestRussianAnalyzerYoStopWords
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
