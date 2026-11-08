# WildcardQuery with the empty pattern matches nothing, even when the empty term is indexed, because Operations.concatenate of an empty list returns the empty language instead of the language of the empty string (since 4.10.0)

Finding LFV-1395 · Area: core / util automaton (Operations.concatenate) through search WildcardQuery.toAutomaton; also queries / intervals (Intervals.wildcard) · Priority: low · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document whose keyword field holds the empty string (StringField f = "") and search with new WildcardQuery(new Term("f", "")). The pattern has no wildcard characters, so you expect it to match exactly the term "", as TermQuery(f, "") and RegexpQuery(f, "") do. Instead it matches nothing: WildcardQuery.toAutomaton passes an empty list to Operations.concatenate, which returns the empty language instead of {""} (the identity of concatenation, what 4.9 returned), and Intervals.wildcard("") inherits the same automaton.

## Expected vs actual

- Expected: You expect it to match exactly the term "", as TermQuery(f, "") and RegexpQuery(f, "") do.
- Actual: It matches nothing: WildcardQuery.toAutomaton passes an empty list to Operations.concatenate, which returns the empty language instead of {""} (the identity of concatenation, what 4.9 returned), and Intervals.wildcard("") inherits the same automaton.

## Files

- [src/org/apache/lucene/search/TestWildcardEmptyPattern.java](src/org/apache/lucene/search/TestWildcardEmptyPattern.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestWildcardEmptyPattern
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
