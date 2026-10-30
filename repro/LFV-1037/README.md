# SortField(STRING_VAL), SortedSetSortField and KeywordField.newSortField accept a missing value other than STRING_FIRST/STRING_LAST and silently treat it as STRING_FIRST; as an index sort the value is lost when the segment is written, so a new IndexWriter with the same config refuses the index ('cannot change previous indexSort')

Bug LFV-1037 · Area: search / sorting (SortField STRING_VAL, SortedSetSortField and KeywordField.newSortField missing values; index sorting) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a string sort with a missing value that is not STRING_FIRST or STRING_LAST, for example new SortedSetSortField("tag", false, SortedSetSelector.Type.MIN, "zzz") or the same through KeywordField.newSortField, much as you would give a numeric sort field a substitute value. You expect an IllegalArgumentException when the sort field is built, as new SortField(f, Type.STRING, false, "zzz") throws and as the removed setMissingValue threw for these sorts too. Instead the value is accepted (getMissingValue and toString report it) but every comparator treats it as STRING_FIRST, so documents without a value silently sort first; and if the sort is the index sort, the segment is written without the value, so the next new IndexWriter with the very same config refuses the index with 'cannot change previous indexSort'.

## Expected vs actual

- Expected: You expect an IllegalArgumentException when the sort field is built, as new SortField(f, Type.STRING, false, "zzz") throws and as the removed setMissingValue threw for these sorts too.
- Actual: The value is accepted (getMissingValue and toString report it) but every comparator treats it as STRING_FIRST, so documents without a value silently sort first; and if the sort is the index sort, the segment is written without the value, so the next new IndexWriter with the very same config refuses the index with 'cannot change previous indexSort'.

## Files

- [src/org/apache/lucene/search/TestStringSortMissingValue.java](src/org/apache/lucene/search/TestStringSortMissingValue.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestStringSortMissingValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
