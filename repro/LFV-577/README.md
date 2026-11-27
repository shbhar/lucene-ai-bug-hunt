# MoreLikeThis.retrieveInterestingTerms(int) throws NullPointerException after the documented setFieldNames(null) ('determined at runtime from the IndexReader'), unless like(int) or like(Map) ran first on the same instance

Finding LFV-577 · Area: queries / MoreLikeThis field-name resolution (setFieldNames(null) and the convenience method retrieveInterestingTerms(int)) · Priority: low · Impact: crash · Novelty: known upstream

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

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- The block below has a javac line and a java command for each of two copies of one test, the file under src/ and the file under src2/, each compiled into a folder of its own: they are the same test for two ranges of Lucene versions and differ in the packages from which they import MockAnalyzer, MockTokenizer, RandomIndexWriter and LuceneTestCase. The file under src/ is the copy for a build of Lucene main or of branch_11x (there V=11.0.0-SNAPSHOT) and for the jars of the 10.6.0 release (Maven Central, org.apache.lucene, in place of the four $LUCENE jars; V=10.6.0): there the first javac line compiles it, and the second javac line (src2) stops with 14 errors and the second java command finds no test, so leave those two commands out. Run on 2026-11-23 on a build of main b5a4546a5964, on a build of branch_11x 262e019500ec and with the 10.6.0 jars, with assertions on and off, as printed and with the two commands left out: the first java command (run 1) reports 1 test found and 1 failed, with java.lang.NullPointerException: Cannot read the array length because "&lt;local4&gt;" is null, thrown in MoreLikeThis.retrieveTerms; with the jars of 9.12.3 and of 9.1.0 (V=9.12.3, V=9.1.0) the same two commands report 1 test found and 1 failed with a NullPointerException thrown in the same method (run the same day with assertions on). The file under src2/ imports the four classes from org.apache.lucene.analysis, org.apache.lucene.index and org.apache.lucene.util, their packages in the lucene-test-framework jars of the releases from 4.0.0 to 9.0.0 (in those of 9.1.0 to 10.6.0 they are org.apache.lucene.tests.analysis, org.apache.lucene.tests.index and org.apache.lucene.tests.util): with the jars of 9.0.0 and of 8.11.4 (V=9.0.0, V=8.11.4) it is the first javac line that stops with 14 errors, so leave it and the first java command out there; the second javac line then compiles the file under src2/ and the second java command reports 1 test found and 1 failed with a NullPointerException thrown in MoreLikeThis.retrieveTerms (run the same day with assertions on).
- How to read the outcome: a failing test is the finding reproducing: with the block as the note above says, the java command of the copy that compiles reports 1 test found and 1 test failed, with a java.lang.NullPointerException thrown in MoreLikeThis.retrieveTerms (on a build of main or of branch_11x and with the jars of the 10.6.0 release its message is Cannot read the array length because "&lt;local4&gt;" is null).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
