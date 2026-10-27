# MultiPassIndexSplitter's round-robin split loses or duplicates the last few documents of an index at about IndexWriter.MAX_DOCS for most part counts of 145 or more, because its int test (j + numParts - i) % numParts overflows

Bug LFV-531 · Area: misc / index tools (MultiPassIndexSplitter.split, round-robin mode) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index of IndexWriter.MAX_DOCS (2,147,483,519) documents with MultiPassIndexSplitter in round-robin mode, the command-line default, into 145 parts, and expect every document in exactly one part (part i gets the documents whose id j has j % numParts == i), as the split javadoc says: ids are 'assigned in a deterministic round-robin fashion to one of the output splits'. Instead the parts hold 2,147,483,527 documents in total: documents 2,147,483,511..518 belong to parts 16..23 but are also copied into parts 0..7, and with 149 parts documents 2,147,483,509..518 end up in no part at all; nothing reports an error. The cause is the keep test (j + numParts - i) % numParts == 0 at MultiPassIndexSplitter.java:103, computed in int: for the top doc ids j + numParts - i wraps negative and Java's % keeps the sign, while the equivalent j % numParts == i cannot overflow.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The parts hold 2,147,483,527 documents in total: documents 2,147,483,511..518 belong to parts 16..23 but are also copied into parts 0..7, and with 149 parts documents 2,147,483,509..518 end up in no part at all; nothing reports an error.

## Files

- [src/MultiPassSplitterRoundRobinOverflowMain.java](src/MultiPassSplitterRoundRobinOverflowMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" MultiPassSplitterRoundRobinOverflowMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/misc-reorder-split/](../../models/misc-reorder-split/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
