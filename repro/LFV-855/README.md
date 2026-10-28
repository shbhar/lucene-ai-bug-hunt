# ICUFoldingFilter folds the Tajik/Abkhaz letter CHE WITH DESCENDER (ҷ U+04B7, Ҷ U+04B6) to the different letter ABKHASIAN CHE (ҽ U+04BD) instead of to CHE (ч U+0447), so Tajik text is not found by the same words typed with plain ч

Bug LFV-855 · Area: analysis / icu (ICUFoldingFilter UTR#30 folding data: the hand-written 'Rule: verbatim' Cyrillic block of DiacriticFolding.txt, compiled into utr30.nrm) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-855.html](../../bugs/LFV-855.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Tajik text such as 'Ҷумҳурии Тоҷикистон' with ICUFoldingFilter, whose javadoc lists 'Diacritic removal (including stroke, hook, descender)', and expect the same words typed without the Tajik letters, 'Чумхурии Точикистон', to find it, as they do for ҳ, қ and the other WITH DESCENDER letters. Instead the hand-copied line '04B7&gt;04BC' in DiacriticFolding.txt maps CHE WITH DESCENDER to ABKHASIAN CHE, so ҷ and Ҷ are indexed as ҽ while the query's ч stays ч, and the query gets 0 hits. In Abkhaz, which uses ҷ, ҽ and ҿ as three separate letters, all three fold to ҽ and match each other.

## Expected vs actual

- Expected: "Ҷумҳурии Тоҷикистон" is indexed as `чумхурии точикистон` and the query "Чумхурии Точикистон" finds it (1 hit); ҷ and Ҷ fold to `ч`.
- Actual: It is indexed as `ҽумхурии тоҽикистон`, so the query finds 0 documents; ҷ and Ҷ fold to `ҽ`.

## Files

- [src/org/apache/lucene/analysis/icu/TestICUFoldingCheWithDescender.java](src/org/apache/lucene/analysis/icu/TestICUFoldingCheWithDescender.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestICUFoldingCheWithDescender
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestICUFoldingCheWithDescender.java](TestICUFoldingCheWithDescender.java)
- [TestCheWithDescenderFolding.java](TestCheWithDescenderFolding.java)
- [CheFoldHistoryMain.java](CheFoldHistoryMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
