# WordDelimiterGraphFilterFactory (and the deprecated WordDelimiterFilterFactory) throw a java.util.NoSuchElementException with no message from inform when the 'types' file holds no rule (empty, only comment or blank lines, or a sole '# =&gt;...' rule that the loader drops as a comment), because parseTypes calls TreeMap.lastKey on the empty rule map

Bug LFV-929 · Area: analysis / WordDelimiterGraphFilterFactory and WordDelimiterFilterFactory types option (parseTypes) versus the getLines resource-file format · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give WordDelimiterGraphFilterFactory a types file that holds no rule yet, for example only a '# custom character types' comment, an empty file, or the single rule '# =&gt; ALPHA' (which the loader drops, because a line that starts with '#' is a comment), and expect the analyzer to build and tokenize exactly as without the option: getLines documents that comment and blank lines are skipped, and the other resource files (the same factory's protected words, stop words, mapping rules) load such a file as empty. Instead inform throws java.util.NoSuchElementException with a null message from TreeMap.lastKey, because parseTypes sizes its table from the largest mapped character without checking that any rule was read. A file with one real rule works, and a two-line guard that returns no custom table when the rule map is empty fixes both factories.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: inform throws java.util.NoSuchElementException with a null message from TreeMap.lastKey, because parseTypes sizes its table from the largest mapped character without checking that any rule was read.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestWordDelimiterTypesFileWithoutRules.java](src/org/apache/lucene/analysis/miscellaneous/TestWordDelimiterTypesFileWithoutRules.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestWordDelimiterTypesFileWithoutRules
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
