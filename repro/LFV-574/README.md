# MoreLikeThis.retrieveInterestingTerms pre-sizes its result list to maxQueryTerms, so a large maxQueryTerms that like accepts (for example Integer.MAX_VALUE as 'no limit') throws OutOfMemoryError even for a one-word document

Bug LFV-574 · Area: queries / MoreLikeThis interesting-terms convenience API (MoreLikeThis.retrieveInterestingTerms(int) and retrieveInterestingTerms(Reader, String)) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set MoreLikeThis.setMaxQueryTerms(Integer.MAX_VALUE), meaning 'no limit', and call retrieveInterestingTerms(...) on a one-word document. You expect that one word back, just as like with the same setting returns a one-clause query. Instead both overloads begin with 'new ArrayList&lt;&gt;(maxQueryTerms)' (MoreLikeThis.java:889 and:914), sized from the limit rather than from the number of terms found, and throw java.lang.OutOfMemoryError: Requested array size exceeds VM limit.

## Expected vs actual

- Expected: You expect that one word back, just as like with the same setting returns a one-clause query.
- Actual: Both overloads begin with 'new ArrayList&lt;&gt;(maxQueryTerms)' (MoreLikeThis.java:889 and:914), sized from the limit rather than from the number of terms found, and throw java.lang.OutOfMemoryError: Requested array size exceeds VM limit.

## Files

- [src/org/apache/lucene/queries/mlt/TestMoreLikeThisInterestingTermsLargeMaxQueryTerms.java](src/org/apache/lucene/queries/mlt/TestMoreLikeThisInterestingTermsLargeMaxQueryTerms.java) (JUnit test)
- [src2/org/apache/lucene/queries/mlt/TestMoreLikeThisInterestingTermsLargeMaxQueryTerms.java](src2/org/apache/lucene/queries/mlt/TestMoreLikeThisInterestingTermsLargeMaxQueryTerms.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisInterestingTermsLargeMaxQueryTerms
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisInterestingTermsLargeMaxQueryTerms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
