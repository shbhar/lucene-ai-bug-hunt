# IndexWriter keeps a full-flush merge that registerMerge rejected: commit does not throw the IllegalStateException that MergePolicy#findFullFlushMerges documents, the rejected merge's readers stay open after IndexWriter.close, and commit/getReader wait the full maxFullFlushMergeWaitMillis for a merge that never runs

Bug LFV-502 · Area: index / IndexWriter point-in-time merges (merge-on-commit, merge-on-getReader: updatePendingMerges, registerMerge, preparePointInTimeMerge, MergeSpecification.await) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You install a merge policy whose findFullFlushMerges (merge-on-commit or merge-on-getReader) proposes a segment that another merge is already working on, a case whose outcome the javadoc spells out: commit 'will throw a IllegalStateException'. Instead IndexWriter quietly refuses to register that merge but still opens its segment readers and waits for it, so commit returns normally only after the full maxFullFlushMergeWaitMillis, the rejected merge's files stay open after IndexWriter.close, and DirectoryReader.open(writer) keeps waiting even after another thread has called rollback. The merges that did register are applied normally.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: IndexWriter quietly refuses to register that merge but still opens its segment readers and waits for it, so commit returns normally only after the full maxFullFlushMergeWaitMillis, the rejected merge's files stay open after IndexWriter.close, and DirectoryReader.open(writer) keeps waiting even after another thread has called rollback.

## Files

- [src/org/apache/lucene/index/TestRejectedFullFlushMerge.java](src/org/apache/lucene/index/TestRejectedFullFlushMerge.java) (JUnit test)
- [src2/org/apache/lucene/index/TestRejectedFullFlushMerge.java](src2/org/apache/lucene/index/TestRejectedFullFlushMerge.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRejectedFullFlushMerge
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRejectedFullFlushMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/merge-on-commit-refresh-tla/](../../models/merge-on-commit-refresh-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
