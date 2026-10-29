# ICUFoldingFilter removes the hook, stroke or descender from older Cyrillic letters such as ҧ (U+04A7) but from none of the 24 letters of that kind added in Unicode 4.1-7.0, such as its modern Abkhaz counterpart ԥ (U+0525), so the two spellings of a word give different terms

Bug LFV-877 · Area: analysis / icu (ICUFoldingFilter UTR#30 folding data: the hand-written 'Rule: verbatim' Cyrillic block of DiacriticFolding.txt, compiled into utr30.nrm) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-877.html](../../bugs/LFV-877.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Abkhaz text in the older spelling 'аҧсуа' with ICUFoldingFilter, whose javadoc lists 'Diacritic removal (including stroke, hook, descender)' and which folds ҧ (U+04A7) to п, and you expect the modern spelling 'аԥсуа', written with ԥ (U+0525, the modern-orthography counterpart of U+04A7), to find it. Instead the hand-copied Cyrillic table in DiacriticFolding.txt, taken from the 2004 UTR#30 draft data for Unicode 4.0 and never extended, has no line for ԥ or for any of the other 23 hook, stroke or descender letters added in Unicode 4.1-7.0 (ӷ ӻ ӽ ӿ ԓ ԡ ԣ ꚋ ԧ ԩ ԯ and the capitals), so they are only case-folded and keep the mark, and the query finds 0 documents. All 48 older letters of the same kind lose the mark (46 fold to their base letter; ҷ/Ҷ fold to a wrong letter, the separate bug LFV-855).

The test shows that ICUFoldingFilter leaves the descender on Cyrillic ԥ (U+0525 PE WITH DESCENDER, Unicode 5.2, modern Abkhaz), while it folds the older form ҧ (U+04A7 PE WITH MIDDLE HOOK) to п.

## Expected vs actual

Expected `ԥ` to fold to `п` (U+043F); actual: `ԥ` unchanged (`term 0 expected:<[п]> but was:<[ԥ]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/icu/TestICUFoldingPeWithDescender.java](src/org/apache/lucene/analysis/icu/TestICUFoldingPeWithDescender.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestICUFoldingPeWithDescender
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
