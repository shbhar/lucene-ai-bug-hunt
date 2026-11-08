# PerFieldPostingsFormat's 'no suffix for format name' check misses equal formats with different names when another field already used the second name, so the commit succeeds and that field's postings cannot be read

Finding LFV-1125 · Area: codecs / per-field postings format (PerFieldPostingsFormat field-to-group mapping) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1125.html](../../bugs/LFV-1125.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give a per-field postings codec two SPI-registered postings formats with different names whose equals() compares only a setting, map field a to the second format with setting 2, field x to the first format with setting 1 and field y to the second format with setting 1, index one document and commit. You expect either the IllegalStateException 'no suffix for format name' that Lucene already throws when only x and y exist, or a segment in which every field's terms can be found. Instead the commit succeeds: y's postings are written into x's group while y's recorded format attributes point to a's group, so a TermQuery on y finds 0 documents and CheckIndex reports the index clean.

## Expected vs actual

- Expected: `commit` throws `IllegalStateException: no suffix for format name: EqB`, as without field `a`; or `docFreq(y:v)` is 1.
- Actual: `commit` succeeds and `docFreq(y:v)` is 0 (`AssertionError: expected:<1> but was:<0>`), with assertions on and off.

## Files

- [src/org/apache/lucene/codecs/perfield/TestPerFieldEqualFormatNames.java](src/org/apache/lucene/codecs/perfield/TestPerFieldEqualFormatNames.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- The test's postings formats EqA and EqB are looked up by name through the SPI file org.apache.lucene.codecs.PostingsFormat in this folder, so the recipe first copies it to resources/META-INF/services/ and puts resources/ on the class path; without it the index cannot be read back and the test fails for another reason.
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

mkdir -p resources/META-INF/services
cp org.apache.lucene.codecs.PostingsFormat resources/META-INF/services/

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:resources:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldEqualFormatNames
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPerFieldEqualFormatNames.java](TestPerFieldEqualFormatNames.java)
- [org.apache.lucene.codecs.PostingsFormat](org.apache.lucene.codecs.PostingsFormat)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
