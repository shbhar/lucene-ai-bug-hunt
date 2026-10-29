# Since 9.0 the utr30.nrm build passes gennorm2 its inputs sorted by file name, so the standard NFKC data overrides Lucene's own UTR#30 folding tables and ICUFoldingFilter turns spacing accents such as ´ ¨ ¯ ¸ into a space inside the term instead of removing them, while ` and ^ are still removed

Bug LFV-857 · Area: analysis / icu (ICUFoldingFilter UTR#30 folding data: the gennorm2 input order of the utr30.nrm regeneration step in lucene/analysis/icu/build.gradle, and the shipped utr30.nrm) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-857.html](../../bugs/LFV-857.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You normalize a keyword field with ICUFoldingFilter and index 'O´Brien' (an acute accent used as an apostrophe); you expect the term 'obrien', because Lucene's own folding table DiacriticFolding.txt deletes U+00B4 along with the other spacing diacritics, as every release up to 8.11.x did and as the filter still does for ` and ^. Since 9.0.0 the term is 'o brien': a 2021 build change (LUCENE-9900) started passing the data files to the gennorm2 compiler sorted by file name, which puts the standard NFKC files last, and in gennorm2 a later file overrides an earlier one, so ´ ¨ ¯ ¸ ˘ ˜ and other spacing accents now become a space and 236 modifier letters fold to their base letter instead of being removed. The change was meant only to make regeneration incremental and its review assumed the order did not matter; 292 entries of Lucene's tables are now dead, and 271 code points fold differently.

`ICUFoldingFilter` turns the spacing ACUTE ACCENT U+00B4 inside a term into a space, although Lucene's own UTR#30 table DiacriticFolding.txt removes it (since 9.0 the build lets the NFKC data override the table).

## Expected vs actual

Expected: `a´b` -&gt; `ab`; actual: `a b` (`term 0 expected:<a[]b> but was:<ab>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/icu/TestICUFoldingSpacingAcute.java](src/org/apache/lucene/analysis/icu/TestICUFoldingSpacingAcute.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestICUFoldingSpacingAcute
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
