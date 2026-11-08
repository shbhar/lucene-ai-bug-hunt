# FloatArrayList.equals treats [0.0f] and [-0.0f] as equal (primitive ==), but hashCode hashes the raw float bits, so two equal lists get different hash codes (31 vs 1832674751); a latent Object#hashCode contract break in the @lucene.internal hppc list, with no in-tree caller of either method, since 9.11.0

Finding LFV-1165 · Area: util / internal hppc FloatArrayList (equals and hashCode contract) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1165.html](../../bugs/LFV-1165.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build FloatArrayList.from(0.0f) and FloatArrayList.from(-0.0f) and compare them: equals returns true (both ways), so by the Object#hashCode contract the two hash codes must be equal. Instead hashCode returns 31 for one list and 1832674751 for the other, because equals compares elements with primitive == (under which 0.0f == -0.0f) while hashCode mixes Float.floatToIntBits, which differs for the two zeros. A HashSet holding one list therefore does not find the other, and can hold both.

## Expected vs actual

- Expected: Equal lists have equal hash codes: `assertEquals(a.hashCode(), b.hashCode())` passes.
- Actual: `java.lang.AssertionError: expected:<31> but was:<1832674751>`, with assertions on and off.

## Files

- [src/org/apache/lucene/internal/hppc/TestFloatArrayListSignedZeroHashCode.java](src/org/apache/lucene/internal/hppc/TestFloatArrayListSignedZeroHashCode.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.internal.hppc.TestFloatArrayListSignedZeroHashCode
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFloatArrayListSignedZeroHashCode.java](TestFloatArrayListSignedZeroHashCode.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
