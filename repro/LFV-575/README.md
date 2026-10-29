# IndexWriter's merge-on-commit / merge-on-getReader wrapper forwards mergeFinished to the merge policy's OneMerge but never calls its mergeInit, so a merge that ran and committed reaches the policy's OneMerge as mergeFinished(success=true) with no mergeInit before it (since 8.6.0)

Bug LFV-575 · Area: index / IndexWriter point-in-time merges (merge-on-commit, merge-on-getReader: the preparePointInTimeMerge OneMerge wrapper and the OneMerge lifecycle hooks) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You install a merge policy whose OneMerge subclass overrides the public mergeInit hook (for example to start a timer or set up per-merge state that mergeFinished later releases) and let IndexWriter merge on commit or on getReader, which it does by default since 9.3.0. You expect that OneMerge to get mergeInit when the merge starts and mergeFinished when it ends, as the same OneMerge does when it comes from findMerges or forceMerge. Instead IndexWriter wraps the policy's merge in its own OneMerge that forwards mergeFinished and the other hooks but not mergeInit, so the policy's OneMerge sees mergeFinished(success=true) for a merge that ran and committed and never sees mergeInit (in the repro: mergeInit=0, mergeFinished=1 for the merge on commit and on getReader; 1 and 1 for forceMerge).

The test shows that a merge on commit (default 500 ms full-flush wait) runs and commits, and the merge policy's `OneMerge` gets `mergeFinished` but never `mergeInit`, because IndexWriter's point-in-time wrapper does not forward `mergeInit`.

## Expected vs actual

Expected, per the `OneMerge#mergeInit` javadoc ("Called by IndexWriter after the merge started"): 1 call. Actual: `mergeInit calls before mergeFinished expected:<1> but was:<0>`.

## Files

- [src/org/apache/lucene/index/TestMergeOnCommitCallsMergeInit.java](src/org/apache/lucene/index/TestMergeOnCommitCallsMergeInit.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergeOnCommitCallsMergeInit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
