# Stempel's Row.store writes the count of all cells but skips the empty ones, so a stemmer table reduced with Gener (Compile method letter G) fails to load with EOFException or loads misaligned and gives wrong stems

Bug LFV-694 · Area: analysis/stempel / egothor stemmer table serialization (Row.store vs Row(DataInput); Gener, Compile 'G', StempelStemmer.load) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-694.html](../../bugs/LFV-694.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile a stemmer table with the stempel Compile tool using a method string that contains G (Gener, the reduction that drops infrequent commands), or reduce a trie with Gener and store it, and expect StempelStemmer.load to read the table back and stem like the trie that was stored. Instead Row.store writes the number of all cells of a row and then skips the empty cells (no command and no reference) that Gener leaves behind, so Row(DataInput) reads too many cell records and runs into the next row: usually load fails with java.io.EOFException (Compile -0ME2G, -0M2G and -0MEG on an 11-lemma Polish table, -EG on a 20-line table, the bundled table reduced with Gener, which then has 38,322 empty cells), and sometimes the table loads without error but answers wrongly (Compile G on a 4-line dictionary: for 'walked' the expected patch is Db and the loaded table gives null). Writing getCells, the existing count of the non-empty cells, fixes it in one line and changes no byte of any table without empty cells, such as the bundled table and every table compiled without G.

## Expected vs actual

- Expected: Both `G` tables load and stem `ab` to `a`, like their no-reduction controls; the stored row reads back with 1 cell in use.
- Actual: One `G` table throws `EOFException` at load, the other loads but stems `ab` to `null`; the row reads back with 2 cells.

## Files

- [src/org/apache/lucene/analysis/stempel/TestStoreAfterGener.java](src/org/apache/lucene/analysis/stempel/TestStoreAfterGener.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/stempel/build/libs/lucene-analysis-stempel-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.stempel.TestStoreAfterGener
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestStoreAfterGener.java](TestStoreAfterGener.java)
- [2-TestStoreAfterGener.java](2-TestStoreAfterGener.java)
- [TestGenerTableRoundTrip.java](TestGenerTableRoundTrip.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
