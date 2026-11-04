# Flexible StandardQueryParser ignores setFuzzyMinSim for a fuzzy term without a number: 'term~' always gets FuzzyQuery.defaultMaxEdits (2 edits) whatever the configured value, while the classic QueryParser honours the same setting

Finding LFV-1363 · Area: queryparser / flexible standard parser fuzzy configuration (StandardSyntaxParser FuzzyOp, FuzzyQueryNodeProcessor, StandardQueryParser#setFuzzyMinSim / FuzzyConfig) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1363.html](../../bugs/LFV-1363.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure the flexible StandardQueryParser with setFuzzyMinSim(1f), which the shared CommonQueryParserConfiguration interface documents as 'Set the minimum similarity for fuzzy queries' and which the classic QueryParser applies to a fuzzy term without a number, then parse 'lucene~' and expect FuzzyQuery lucene~1 (at most one edit). Instead you get lucene~2, which also matches 'lucxna' (two edits), although getFuzzyMinSim() returns 1.0: the grammar (StandardSyntaxParser.jj FuzzyOp) fills in FuzzyQuery.defaultMaxEdits for a bare '~', and FuzzyQueryNodeProcessor copies the configured value onto the node only when its similarity is unset (below 0), which the grammar never produces. So the setter only changes what its getter returns; the sibling setFuzzyPrefixLength, stored in the same FuzzyConfig, is applied.

## Expected vs actual

- Expected: `getMaxEdits()` is 1: `lucene~` allows at most one edit, as the classic `QueryParser` gives with the same setting.
- Actual: `AssertionError: expected:<1> but was:<2>`: the parsed query is `f:lucene~2`.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestFuzzyMinSimBareTilde.java](src/org/apache/lucene/queryparser/flexible/standard/TestFuzzyMinSimBareTilde.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestFuzzyMinSimBareTilde
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFuzzyMinSimBareTilde.java](TestFuzzyMinSimBareTilde.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
