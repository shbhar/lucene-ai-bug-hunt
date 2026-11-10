# MultiTermQuery.getTermsEnum's javadoc says the returned TermsEnum is already positioned on the first matching term, but it is unpositioned and every Lucene consumer calls next() first, so a MultiTermQuery subclass that positions its enum as documented silently loses its first matching term in each segment, and a caller who reads term() first gets the empty term (javadoc only; the code is correct); since 4.0.0

Finding LFV-1448 · Area: search / MultiTermQuery term expansion (getTermsEnum javadoc; TermCollectingRewrite and AbstractMultiTermQueryConstantScoreWrapper call next() before the first term) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1448.html](../../bugs/LFV-1448.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a MultiTermQuery subclass whose getTermsEnum(Terms, AttributeSource) returns a prefix FilteredTermsEnum that you have already advanced with next(), because that method's javadoc says 'The TermsEnum must already be positioned to the first matching term', and you index apple, banana and blueberry. You expect your query for prefix b to count 2 documents, like PrefixQuery f:b*, but it counts 1: Lucene's rewrites call next() before they read the first term, so the term you positioned on (banana) is skipped; in the same way, reading term() first on the public getTermsEnum(Terms), whose javadoc says the enum 'is positioned to the first matching term', gives the empty term instead of banana. The unpositioned enum is the intended design (TermsEnum's class contract, LUCENE-2110), so only the two sentences, left over from the pre-4.0 FilteredTermEnum API, are wrong.

## Expected vs actual

- Expected: `IndexSearcher.count` is 2, the same as for the stock `PrefixQuery f:b*`.
- Actual: `count` is 1 (`expected:<2> but was:<1>`), with no error, assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/search/TestMultiTermQueryPositionedEnum.java](src/org/apache/lucene/search/TestMultiTermQueryPositionedEnum.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestMultiTermQueryPositionedEnum
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiTermQueryPositionedEnum.java](TestMultiTermQueryPositionedEnum.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
