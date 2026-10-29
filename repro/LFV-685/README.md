# IrishLowerCaseFilter lower-cases one UTF-16 unit at a time, so IrishAnalyzer leaves upper-case letters outside the BMP (Adlam, Osage, Deseret and others) unchanged

Bug LFV-685 · Area: analysis-common / ga (IrishLowerCaseFilter and IrishAnalyzer case mapping) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-685.html](../../bugs/LFV-685.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index text with IrishAnalyzer (or put IrishLowerCaseFilter in a chain), whose filter is documented to 'normalise token text to lower case', and expect a capitalised word to match its lower-case form, as it does with LowerCaseFilter. For letters outside the BMP it does not: the Adlam capital U+1E900 stays U+1E900 instead of becoming U+1E922 (the Deseret capital U+10400 likewise stays, also after n-eclipsis), so a document holding the Adlam word U+1E900 U+1E923 is not found by U+1E922 U+1E923 (0 hits instead of 1). The loop at IrishLowerCaseFilter.java:55 passes one UTF-16 char to Character.toLowerCase, which returns a surrogate unit unchanged; BMP letters in the same token are lower-cased and nothing is thrown.

## Expected vs actual

- Expected: The filter maps U+1E900 to U+1E922 (and the other capitals to their small letters), and the `IrishAnalyzer` search for U+1E922 U+1E923 finds the document: 1 hit.
- Actual: 3 of 4 tests fail: all five filter cases keep their capital (U+1E900 U+1E923 stays U+1E900 U+1E923), `normalize` keeps U+1E900, and the lower-case search finds 0 hits; the capitalised spelling finds 1.

## Files

- [src/org/apache/lucene/analysis/ga/TestIrishLowerCaseSupplementary.java](src/org/apache/lucene/analysis/ga/TestIrishLowerCaseSupplementary.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ga.TestIrishLowerCaseSupplementary
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestIrishLowerCaseFilterSupplementary.java](TestIrishLowerCaseFilterSupplementary.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
