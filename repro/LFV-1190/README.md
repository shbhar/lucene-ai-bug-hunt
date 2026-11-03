# MultiPostingsEnum.toString() throws NullPointerException for the merged postings of a term that is missing from some segment (for example MultiTerms.getTermPostingsEnum on a two-segment index), because it prints every sub-reader slot and the unfilled slots have a null slice (since 4.10.0, LUCENE-5798)

Finding LFV-1190 · Area: index / merged multi-segment postings (MultiPostingsEnum.toString, MultiTermsEnum.postings) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1190.html](../../bugs/LFV-1190.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents into two segments, with the term 'a' only in the first, and ask MultiTerms.getTermPostingsEnum for the merged postings of 'a'; iterating them correctly gives doc 0 and then NO_MORE_DOCS. You expect toString() to describe the enum, as it does when the term is in every segment. Instead it throws NullPointerException 'Cannot invoke "org.apache.lucene.index.ReaderSlice.toString()" because "this.slice" is null': the enum keeps one slot per segment but fills only the slots of the segments that hold the term, and toString prints every slot, including the unfilled ones whose slice is null.

## Expected vs actual

- Expected: `toString()` returns a description of the merged enum.
- Actual: `NullPointerException: Cannot invoke "org.apache.lucene.index.ReaderSlice.toString()" because "this.slice" is null` at `MultiPostingsEnum$EnumWithSlice.toString(MultiPostingsEnum.java:178)`.

## Files

- [src/org/apache/lucene/index/TestMultiPostingsEnumToString.java](src/org/apache/lucene/index/TestMultiPostingsEnumToString.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMultiPostingsEnumToString
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiPostingsEnumToString.java](TestMultiPostingsEnumToString.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
