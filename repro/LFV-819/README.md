# ASCIIFoldingFilter folds the ligature ﬆ (U+FB06) to 'st' but has no entry for its sibling ﬅ (U+FB05 LATIN SMALL LIGATURE LONG S T, NFKD 'st'), so 'faﬅ' is indexed unchanged and the ASCII query 'fast' does not find it

Bug LFV-819 · Area: analysis-common / ASCIIFoldingFilter mapping table (foldToASCII switch) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-819.html](../../bugs/LFV-819.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You fold text with ASCIIFoldingFilter, whose javadoc says it converts characters 'into their ASCII equivalents, if one exists' and lists the Alphabetic Presentation Forms block, and you search in plain ASCII. You expect 'faﬅ' (with the long-s-t ligature U+FB05, whose NFKD is 'st') to be indexed as 'fast', just as 'faﬆ' (U+FB06) and 'faſt' (long s U+017F) are; instead the table has cases for U+FB00-FB04 and U+FB06 but none for U+FB05, so the character passes through unchanged, the term stays 'faﬅ' and the query 'fast' finds nothing. The fix is one case label (U+FB05 added to the U+FB06 'st' group) plus the matching row of the generated testAllFoldings array.

The test shows that `ASCIIFoldingFilter` has no table entry for U+FB05 LATIN SMALL LIGATURE LONG S T, although it folds its sibling U+FB06 to "st", so "fa\uFB05" is not folded.

## Expected vs actual

Expected the term "fast" (the proposed value "st" is the NFKD of U+FB05, not a promise of the current javadoc); actual: "fa\uFB05" unchanged (`term 0 expected:<fa[st]> but was:<fa[ﬅ]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestASCIIFoldingLongSTLigature.java](src/org/apache/lucene/analysis/miscellaneous/TestASCIIFoldingLongSTLigature.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestASCIIFoldingLongSTLigature
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
