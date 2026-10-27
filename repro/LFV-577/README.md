# MoreLikeThis.retrieveInterestingTerms(int) throws NullPointerException after the documented setFieldNames(null) ('determined at runtime from the IndexReader'), unless like(int) or like(Map) ran first on the same instance

Bug LFV-577 · Area: queries / MoreLikeThis field-name resolution (setFieldNames(null) and the convenience method retrieveInterestingTerms(int)) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a MoreLikeThis, call setFieldNames(null), which its javadoc documents as 'the field names to be determined at runtime from the IndexReader provided in the constructor', and ask retrieveInterestingTerms(docNum) for the interesting words of an indexed document. You expect the words that like(docNum) would put into its query, taken from all indexed fields. Instead the call throws 'NullPointerException: Cannot read the array length because "&lt;local4&gt;" is null' at MoreLikeThis.retrieveTerms (MoreLikeThis.java:715), because only like(int) and like(Map) replace null with the reader's indexed fields, so it works only if one of them ran first on the same instance.

## Expected vs actual

- Expected: You expect the words that like(docNum) would put into its query, taken from all indexed fields.
- Actual: The call throws 'NullPointerException: Cannot read the array length because "&lt;local4&gt;" is null' at MoreLikeThis.retrieveTerms (MoreLikeThis.java:715), because only like(int) and like(Map) replace null with the reader's indexed fields, so it works only if one of them ran first on the same instance.

## Files

- [src/org/apache/lucene/queries/mlt/TestMoreLikeThisNullFieldNames.java](src/org/apache/lucene/queries/mlt/TestMoreLikeThisNullFieldNames.java) (JUnit test)
- [src2/org/apache/lucene/queries/mlt/TestMoreLikeThisNullFieldNames.java](src2/org/apache/lucene/queries/mlt/TestMoreLikeThisNullFieldNames.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisNullFieldNames
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisNullFieldNames
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
