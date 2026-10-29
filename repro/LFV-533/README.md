# CapitalizationFilter changes case one UTF-16 char at a time, so cased letters outside the BMP (Adlam, Osage, Deseret and five other scripts) are neither capitalized nor lower-cased, while BMP letters in the same token are

Bug LFV-533 · Area: analysis-common / miscellaneous (CapitalizationFilter case mapping) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-533.html](../../bugs/LFV-533.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add CapitalizationFilter, documented to 'make the first letter capital and the rest lower case', and give it a word in a cased script outside the BMP, for example the Adlam word U+1E922 U+1E923 (two small letters), and expect U+1E900 U+1E923 (capital first letter). Instead every supplementary letter keeps its case: U+1E922 U+1E923 comes out unchanged, U+1E900 U+1E901 is not lower-cased to U+1E900 U+1E923, and 'aB' followed by the Osage capital U+104B0 gives 'Ab' + U+104B0, because processWord applies Character.toUpperCase(char) and toLowerCase(char) to single UTF-16 units and a surrogate unit maps to itself. BMP letters in the same token are mapped, nothing is thrown, and the onlyFirstWord and keep-word forceFirstLetter paths behave the same way.

## Expected vs actual

- Expected: `PARIS`/`paris` and the two Adlam spellings give 2 terms, `Paris` and U+1E900 U+1E923, and the query U+1E922 U+1E923 finds 2 documents; every token gets a capital first letter and a lower-case rest.
- Actual: 3 of 4 tests fail: 3 terms (both Adlam spellings kept, neither capitalized), the Adlam query finds 1 document, and every supplementary letter in the token cases keeps its case.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestCapitalizationFilterSupplementaryLetters.java](src/org/apache/lucene/analysis/miscellaneous/TestCapitalizationFilterSupplementaryLetters.java) (JUnit test)
- [src/repro/CapitalizationSupplementaryMain.java](src/repro/CapitalizationSupplementaryMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestCapitalizationFilterSupplementaryLetters

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.CapitalizationSupplementaryMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestCapitalizationFilterSupplementary.java](TestCapitalizationFilterSupplementary.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
