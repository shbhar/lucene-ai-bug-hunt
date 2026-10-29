# ASCIIFoldingFilter folds six letters to the ASCII letter of the opposite case (ǥ U+01E5 and ǧ U+01E7 to 'G', insular ꞅ U+A785 to 'S' and Ꞅ U+A784 to 's', Ɐ U+2C6F to 'a', Ꜿ U+A73E to 'c'; stretched ʗ U+0297 to 'C' is contested), so a LowerCaseFilter-then-ASCIIFoldingFilter chain indexes 'naǧib' as 'naGib' and the ASCII query 'nagib' finds nothing

Bug LFV-818 · Area: analysis-common / ASCIIFoldingFilter mapping table (foldToASCII switch) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-818.html](../../bugs/LFV-818.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index and search through a chain that lower-cases and then applies ASCIIFoldingFilter, documented to convert characters 'into their ASCII equivalents', and you type queries in plain ASCII. You expect 'naǧib' and 'NAǦIB' to be indexed as 'nagib', just as 'ağa' becomes 'aga'; instead six entries of the 1,242-entry folding table sit in the output group of the opposite case (ǥ and ǧ in 'G', ꞅ in 'S', Ꞅ in 's', Ɐ in 'a', Ꜿ in 'c', plus the contested ʗ in 'C'), so the term is 'naGib' and the query 'nagib' finds nothing. Each of the six lines carries a source comment that names the other case (for example [LATIN SMALL LETTER G WITH CARON] inside the 'G' group), and the fix is to move these case labels into the group of their own case.

The test shows that `ASCIIFoldingFilter` folds ǧ (U+01E7 LATIN SMALL LETTER G WITH CARON) to capital `G`: its table entry is in the 'G' group, not the 'g' group.

## Expected vs actual

Expected the token `g`; actual `G` (-ea and -da).

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestASCIIFoldingOppositeCase.java](src/org/apache/lucene/analysis/miscellaneous/TestASCIIFoldingOppositeCase.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestASCIIFoldingOppositeCase
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/ascii-folding-table-vs-unicode-character-database/](../../models/ascii-folding-table-vs-unicode-character-database/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
