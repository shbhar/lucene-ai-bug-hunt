# SimpleText points: PointTree.clone shares a saved split-value array with the tree it was cloned from, so moving one tree corrupts the node bounds the other reports

Bug LFV-753 · Area: codecs / SimpleText points format (SimpleTextBKDReader.SimpleTextPointTree#clone) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index points with the SimpleText codec, take a PointTree from PointValues.getPointTree, clone it and keep moving both the clone and the original, and expect each tree to report the bounds of its own current node, as PointTree#clone promises ('the current node becomes the root of the new tree') and as the default points format does. Instead SimpleTextPointTree.clone copies the reference to the original's saved split-value array for the current level, so a move of one tree overwrites the bound that the other tree restores when it moves back up: after tree.moveToChild, tree.moveToParent, clone = tree.clone, clone.moveToChild, tree.moveToChild, tree.moveToSibling, clone.moveToParent, the clone at its root reports max 0 instead of 2047 for the values 0..2047. Any traversal that prunes on those bounds can then miss or wrongly include cells; no in-tree query moves trees this way.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: SimpleTextPointTree.clone copies the reference to the original's saved split-value array for the current level, so a move of one tree overwrites the bound that the other tree restores when it moves back up: after tree.moveToChild, tree.moveToParent, clone = tree.clone, clone.moveToChild, tree.moveToChild, tree.moveToSibling, clone.moveToParent, the clone at its root reports max 0 instead of 2047 for the values 0..2047.

## Files

- [src/org/apache/lucene/codecs/simpletext/TestSimpleTextPointTreeCloneIndependence.java](src/org/apache/lucene/codecs/simpletext/TestSimpleTextPointTreeCloneIndependence.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions on, seed 254C10E
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=254C10E \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.simpletext.TestSimpleTextPointTreeCloneIndependence
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
