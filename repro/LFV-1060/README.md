# IndexWriter.hasUncommittedChanges can return false for an acknowledged, uncommitted delete while another thread flushes or refreshes an NRT reader, because it reads changeCount before the in-RAM state (every release since 4.5.1)

Bug LFV-1060 · Area: index / IndexWriter (hasUncommittedChanges: changeCount vs. buffered deletes in flight) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You delete a committed document with deleteDocuments, do not commit, and call hasUncommittedChanges while another thread calls flush or refreshes an NRT reader; you expect true, because the delete was acknowledged and is not in the last commit, and the javadoc allows only false positives. Instead the method reads changeCount first (still equal to the last commit's) and the in-RAM state second, and if the flush applies the delete, increments changeCount and empties the delete buffers between those two reads, it returns false; the next call returns true. A caller that commits only when the method returns true skips that commit.

## Expected vs actual

- Expected: You expect true, because the delete was acknowledged and is not in the last commit, and the javadoc allows only false positives.
- Actual: The method reads changeCount first (still equal to the last commit's) and the in-RAM state second, and if the flush applies the delete, increments changeCount and empties the delete buffers between those two reads, it returns false; the next call returns true.

## Files

- [src/org/apache/lucene/index/TestHasUncommittedChangesConcurrentFlush.java](src/org/apache/lucene/index/TestHasUncommittedChangesConcurrentFlush.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHasUncommittedChangesConcurrentFlush
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/iw-hasuncommittedchanges-read-order-race/](../../models/iw-hasuncommittedchanges-read-order-race/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
