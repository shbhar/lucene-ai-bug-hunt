# SingleTermsEnum's constructor javadoc says the new enum already points to the term, but it stays unpositioned until the first next(), so a caller who follows it reads an empty term, docFreq 0 and empty postings (javadoc only; the code is correct); since 4.0.0-alpha

Finding LFV-1433 · Area: index / terms enumeration (SingleTermsEnum constructor javadoc; FilteredTermsEnum seeks the delegate lazily in the first next()) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1433.html](../../bugs/LFV-1433.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the term b in field f, open the reader and call new SingleTermsEnum(terms.iterator(), new BytesRef("b")). You expect what the constructor javadoc says, 'After calling the constructor the enumeration is already pointing to the term, if it exists', so term() should return b and docFreq() and postings() should describe it. Instead the enum is unpositioned until the first next(), like every TermsEnum: the constructor only records the seek term (setInitialSeekTerm) and FilteredTermsEnum seeks lazily inside next(), so term() returns an empty BytesRef and, with -da, docFreq() is 0 and postings() are empty; the lazy code is the intended design (LUCENE-2110) and the sentence is a leftover from the pre-4.0 SingleTermEnum, which did position itself in its constructor.

## Expected vs actual

- Expected: `term()` returns `b` right after the constructor, as its javadoc says.
- Actual: `term()` returns an empty `BytesRef`: `AssertionError: expected:<[62]> but was:<[]>`, with assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/index/TestSingleTermsEnumCtorPositioned.java](src/org/apache/lucene/index/TestSingleTermsEnumCtorPositioned.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.index.TestSingleTermsEnumCtorPositioned
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSingleTermsEnumCtorPositioned.java](TestSingleTermsEnumCtorPositioned.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
