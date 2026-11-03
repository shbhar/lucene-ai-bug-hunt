# QueryBuilder counts a removed leading stop word as a position, so a quoted phrase such as "the dogs", whose one remaining word carries a same-position synonym or variant, becomes a one-position MultiPhraseQuery instead of the SynonymQuery that "dogs" gives, and is scored differently (since 6.0.0)

Finding LFV-1148 · Area: core / util QueryBuilder (createFieldQuery position count choosing the phrase branch over the single-position synonym branch; reached through the classic, multi-field and simple query parsers) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1148.html](../../bugs/LFV-1148.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze query text with stop-word removal and a same-position synonym (dogs -&gt; dog) and call createPhraseQuery(field, "the dogs"), or type the quoted phrase "the dogs" into the classic QueryParser. You expect the SynonymQuery(dogs, dog) that "dogs" and "dogs the" give, because the remaining terms sit at one position and QueryBuilder generates phrases only 'when terms occur at more than one position'; you get the one-position MultiPhraseQuery field:"(dogs dog)", which rewrites to a plain disjunction of the two terms. The same documents match, but BM25 scores them differently (doc 'dogs' rises from 0.2774 to 0.7135 and no longer ties with doc 'dog'), because createFieldQuery adds up position increments, so the hole left by 'the' counts as a second position.

## Expected vs actual

- Expected: `Synonym(field:dog field:dogs)`, the SynonymQuery that quoted `"dogs"` gives.
- Actual: `AssertionError: expected:<Synonym(field:dog field:dogs)> but was:<field:"(dogs dog)">`: a one-position MultiPhraseQuery.

## Files

- [src/org/apache/lucene/util/TestQueryBuilderLeadingStopWordSynonym.java](src/org/apache/lucene/util/TestQueryBuilderLeadingStopWordSynonym.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestQueryBuilderLeadingStopWordSynonym
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestQueryBuilderLeadingStopWordSynonym.java](TestQueryBuilderLeadingStopWordSynonym.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
