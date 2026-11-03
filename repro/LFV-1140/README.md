# Index sorting by a STRING field with STRING_LAST: at merge, a document without a value gets the int key Integer.MAX_VALUE while documents with a value get long global ordinals, so when the inputs of one merge hold more than 2^31 - 1 distinct values (reachable through IndexWriter.addIndexes(CodecReader...)), the merged segment silently puts documents without a value ahead of documents with a value

Finding LFV-1140 · Area: index / index sorting at merge: IndexSorter.StringSorter merge keys (MultiSorter), reached through IndexWriter.addIndexes(CodecReader...) · Priority: low · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1140.html](../../bugs/LFV-1140.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure the index sort new SortField("s", SortField.Type.STRING, false, SortField.STRING_LAST) and merge, with IndexWriter.addIndexes(CodecReader...), sorted source indexes whose 's' dictionaries together hold more than 2^31 - 1 distinct values, expecting documents without a value to come after every document with one, as STRING_LAST and the in-segment comparator promise. At merge, IndexSorter.StringSorter gives a document with a value its long global ordinal (from an OrdinalMap over every input's full dictionary, deleted documents' values included) but gives a document without a value the int key Integer.MAX_VALUE, so every value whose global ordinal is above 2^31 - 1 sorts after the missing documents (a value at exactly 2^31 - 1 gets the same key as they do). The merged segment is written out of its declared sort with no error: in an end-to-end addIndexes(CodecReader...) test over two one-live-document segments whose dictionaries a FilterCodecReader enlarges to 2^30 + 1 terms each, the document without a value ends up at doc 0.

## Expected vs actual

- Expected: The key of the document without a value is above the largest present key, 2,147,483,649.
- Actual: `AssertionError: missing=2147483647 present=2147483649`, on main with assertions on and off, and on the 10.6.0 jars with assertions off.

## Files

- [src/org/apache/lucene/index/TestStringSorterMissingLast.java](src/org/apache/lucene/index/TestStringSorterMissingLast.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestStringSorterMissingLast
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStringSorterMissingLast.java](TestStringSorterMissingLast.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
