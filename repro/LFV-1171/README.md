# FuzzyQuery with maxEdits &gt;= 1 on a term whose bytes are not valid UTF-8 builds its automaton from the hex display string of Term.text(), so it misses the identical term and matches terms near "[ff 61 62 63]" instead

Finding LFV-1171 · Area: core / search: FuzzyQuery and FuzzyTermsEnum (Levenshtein pattern built from Term.text()) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1171.html](../../bugs/LFV-1171.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a binary term, for example the bytes ff 61 62 63 in a StringField, and search it with new FuzzyQuery(new Term(field, sameBytes), 1); you expect the document to match, because the identical term is at edit distance 0 and FuzzyQuery(term, 0) does match it. Instead the query matches nothing, or only a document holding the text term "[ff 61 62 63]": FuzzyTermsEnum builds its Levenshtein automaton from Term.text(), and for bytes that are not valid UTF-8 Term.text() returns the human-readable hex rendering of the bytes, not the term.

## Expected vs actual

- Expected: `count(new FuzzyQuery(new Term("id", bytes), 1))` is 1, the identical term (or the constructor rejects the non-UTF-8 pattern).
- Actual: `java.lang.AssertionError: expected:<1> but was:<0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/search/TestFuzzyQueryBinaryPattern.java](src/org/apache/lucene/search/TestFuzzyQueryBinaryPattern.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFuzzyQueryBinaryPattern
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFuzzyQueryBinaryPattern.java](TestFuzzyQueryBinaryPattern.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
