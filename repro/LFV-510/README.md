# ProtectedTermFilterFactory does not pass its luceneMatchVersion to the filters it builds from wrappedFilters, so since 10.5 a wrapped truncate with prefixLength under a pinned pre-10.5 version truncates by code points instead of the legacy UTF-16 chars

Bug LFV-510 · Area: analysis / common miscellaneous (ProtectedTermFilterFactory#populateInnerFilters: wrappedFilters factory construction; symptom in TruncateTokenFilterFactory prefixLength) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure ProtectedTermFilterFactory with wrappedFilters=truncate and truncate.prefixLength=4 and pin luceneMatchVersion to 10.4.0 (in the factory args, or through CustomAnalyzer.withDefaultMatchVersion), on Lucene 10.5 or later. You expect the wrapped truncate to keep the legacy behaviour that the pinned version selects for back-compat, the first 4 UTF-16 chars, as an unwrapped truncate and the equivalent when/endwhen form do under the same version. Instead populateInnerFilters builds the wrapped factory from the 'truncate.*' arguments only, so it runs at Version.LATEST and keeps 4 code points: the token 'ab😀😀cd' becomes 'ab😀😀' instead of 'ab😀', with no error.

## Expected vs actual

- Expected: You expect the wrapped truncate to keep the legacy behaviour that the pinned version selects for back-compat, the first 4 UTF-16 chars, as an unwrapped truncate and the equivalent when/endwhen form do under the same version.
- Actual: populateInnerFilters builds the wrapped factory from the 'truncate.*' arguments only, so it runs at Version.LATEST and keeps 4 code points: the token 'ab😀😀cd' becomes 'ab😀😀' instead of 'ab😀', with no error.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestProtectedTermWrappedMatchVersion.java](src/org/apache/lucene/analysis/miscellaneous/TestProtectedTermWrappedMatchVersion.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestProtectedTermWrappedMatchVersion
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
