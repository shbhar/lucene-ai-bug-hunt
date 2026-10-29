# AttributeSource.removeAllAttributes leaves the cached State chain in place, so until the next addAttribute captureState/restoreState, copyTo, hashCode, the impls iterator, reflection and clearAttributes/endAttributes still act on the removed attributes

Bug LFV-500 · Area: core / util AttributeSource (removeAllAttributes and the cached State chain, getCurrentState / currentState[0]) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call removeAllAttributes on an AttributeSource (for example a token stream you reuse with a different set of attributes) after it has been used, for example by clearAttributes, which every Tokenizer calls for each token. You expect it to behave like a new, empty source until you add attributes again, as hasAttributes (false) and equals (equal to a new AttributeSource) already say. Instead the method clears its two maps but not the cached chain of attribute instances that captureState, copyTo, hashCode, the impls iterator, reflection and clearAttributes/endAttributes walk, so until the next addAttribute they still see the removed attributes: restoreState(captureState) on the same source throws IllegalArgumentException, hashCode differs from that of an equal empty source, and reflectAsString still prints 'term=foo,...'.

The test shows that AttributeSource.removeAllAttributes() leaves the cached State chain in place, so captureState() still returns the removed attributes and restoring that state into the same source fails.

## Expected vs actual

Expected: restoreState(captureState()) after removeAllAttributes() succeeds (captureState javadoc: the state can be restored into this source); actual: `IllegalArgumentException: State contains AttributeImpl of type...CharTermAttributeImpl that is not in in this AttributeSource`.

## Files

- [src/org/apache/lucene/util/TestRemoveAllAttributesCachedState.java](src/org/apache/lucene/util/TestRemoveAllAttributesCachedState.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestRemoveAllAttributesCachedState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
