# BlendedTermQuery.DisjunctionMaxRewrite, LatLonPoint.newDistanceFeatureQuery and XYDocValuesField.newDistanceSort compare float/double parameters with == in equals() but hash their bits, so objects built with 0.0 and -0.0 are equal yet have different hash codes (since 5.3.0)

Finding LFV-1337 · Area: core / search + document: equals and hashCode of BlendedTermQuery.DisjunctionMaxRewrite, LatLonPointDistanceFeatureQuery and XYPointSortField · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1337.html](../../bugs/LFV-1337.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build the same object twice, once with 0.0 and once with -0.0 for one parameter: a BlendedTermQuery with new DisjunctionMaxRewrite(0f) versus (-0f), LatLonPoint.newDistanceFeatureQuery with origin latitude (or longitude) 0.0 versus -0.0, or XYDocValuesField.newDistanceSort with x 0f versus -0f. equals() says the two are equal, because it compares the parameter with ==, under which 0.0 == -0.0, so by the Object#hashCode contract, which Query's javadoc asks subclasses to honour so that QueryCache works, their hash codes must be equal. Instead they differ, because hashCode() hashes the raw bits (Float.floatToIntBits or Double.hashCode), which differ in the sign bit, so a HashSet holding one object does not find the other, while hits and scores stay the same.

## Expected vs actual

- Expected: Every equal pair has equal hash codes, so the list of broken classes is empty.
- Actual: `AssertionError: expected:<[]> but was:<[BlendedTermQuery, LatLonPointDistanceFeatureQuery, XYPointSortField]>`

## Files

- [src/org/apache/lucene/search/TestSignedZeroEqualsHashCode.java](src/org/apache/lucene/search/TestSignedZeroEqualsHashCode.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSignedZeroEqualsHashCode
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSignedZeroEqualsHashCode.java](TestSignedZeroEqualsHashCode.java)

Formal models and scripts this finding's part of the hunt used: [../../models/signed-zero-equals-hashcode-remaining-query-valuesource-sort-sites/](../../models/signed-zero-equals-hashcode-remaining-query-valuesource-sort-sites/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
