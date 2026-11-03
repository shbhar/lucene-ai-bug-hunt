# Util.readCeilArc returns a wrong arc or null, with no error, when one Arc object is passed as both follow and arc and the target node is list-encoded, and reads FST address -1 when follow is final (since 4.1.0)

Finding LFV-1159 · Area: util / FST (Util.readCeilArc: follow overwritten by readFirstTargetArc before the list-node scan re-reads follow.target()) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1159.html](../../bugs/LFV-1159.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a small FST (keys ab, ac, x) with the default FSTCompiler and call Util.readCeilArc('a', fst, arc, arc, in) with the start arc as both follow and arc, the same call shape Lucene's own TestUtil uses. You expect the arc 'a', the first arc &gt;= 'a' leaving the start arc, which the same call with two distinct Arc objects returns. Instead it returns the arc 'b' from the node one level down (and null instead of 'x' for label 'w'), because readFirstTargetArc overwrites the shared object before the list-node scan re-reads follow.target(); when follow is a final arc the call reads FST address -1 and throws ArrayIndexOutOfBoundsException.

## Expected vs actual

- Expected: The arc `a` (label 97), as with two distinct `Arc` objects.
- Actual: The arc `b` (label 98): `java.lang.AssertionError: expected:<97> but was:<98>`.

## Files

- [src/org/apache/lucene/util/fst/TestReadCeilArcSameFollowAndArc.java](src/org/apache/lucene/util/fst/TestReadCeilArcSameFollowAndArc.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestReadCeilArcSameFollowAndArc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReadCeilArcSameFollowAndArc.java](TestReadCeilArcSameFollowAndArc.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
