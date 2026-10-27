# Extensions javadoc says splitExtensionField inverts buildExtensionField, but the default split cuts at the first delimiter and nothing says the delimiter must not occur in field names, so with the '_' delimiter of the ExtendableQueryParser example a field like first_name silently never reaches its extension (documentation defect; the first-occurrence split itself is documented and intended)

Bug LFV-716 · Area: queryparser / ext: Extensions, ExtendableQueryParser (default extension-field encoding javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure ExtendableQueryParser with new Extensions('_') as in the class javadoc example, register an extension under customExt and parse buildExtensionField("customExt", "first_name") + ":foo", that is first_name_customExt:foo; because the Extensions javadoc twice says that splitExtensionField inverts buildExtensionField, with no restriction on field names, you expect the extension to be called for field first_name. Instead splitExtensionField cuts at the first '_' (as its own javadoc says), giving field 'first' and key 'name_customExt'; no extension has that key, so the parser silently builds the plain TermQuery first_name_customExt:foo. The default encoding field + delimiter + key cannot be inverted for every input ((user_id, exact) and (user, id_exact) both build user_id_exact), so the code is defensible and the fix is documentation: say that the delimiter must not occur in field names (keys may contain it) or that a subclass is needed, say it next to the '_' example, and make TestExtensions.testSplitExtensionField actually call splitExtensionField.

## Expected vs actual

- Expected: You expect the extension to be called for field first_name.
- Actual: splitExtensionField cuts at the first '_' (as its own javadoc says), giving field 'first' and key 'name_customExt'; no extension has that key, so the parser silently builds the plain TermQuery first_name_customExt:foo.

## Files

- [src/org/apache/lucene/queryparser/ext/TestExtensionFieldWithDelimiter.java](src/org/apache/lucene/queryparser/ext/TestExtensionFieldWithDelimiter.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.ext.TestExtensionFieldWithDelimiter
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
