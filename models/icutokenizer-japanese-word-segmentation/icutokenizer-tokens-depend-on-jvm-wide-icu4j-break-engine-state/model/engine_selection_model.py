#!/usr/bin/env python3
"""Executable model of the break-engine selection of ICU4J's word iterator (differential/MBT, unit icutokenizer-japanese-word-segmentation).

It models ONLY which engine gets each run of dictionary characters, with the JVM-wide state carried
from one analysis to the next. It does not model the CJK dictionary itself, so its verdict is
"the engine assignment of the two analyses differs" = the tokens CAN differ (a necessary condition;
scenario S9 below is a case where the assignment differs and the tokens do not).

Mapping, model element -> source (ICU4J tag release-79.1;
the same code is in tag release-77-1 at the lines in brackets):

  STATE.engines (JVM-wide list, the unhandled engine FIRST)
        RuleBasedBreakIterator.java:329,347-349 [331,347-349]  gUnhandledBreakEngine, gAllBreakEngines
  STATE.unhandled (JVM-wide set of the unhandled engine, starts empty, only grows)
        UnhandledBreakEngine.java:35 [36]  'volatile UnicodeSet fHandled = new UnicodeSet()'
  select(c): the FIRST engine of the list that handles c wins
        RuleBasedBreakIterator.java:711-715, 719-723 [721-725, 729-733]
  select(c), nobody handles c: by script; Katakana/Hiragana/Han -> a new CJK engine, appended
        RuleBasedBreakIterator.java:726-731, 760-761, 775-777 [736-741, 766-767, 781-783]
  select(c), any other script (U+30FC and U+30A0 are script Common): the unhandled engine takes the
  WHOLE SCRIPT of c into its set
        RuleBasedBreakIterator.java:766-768 [772-774]; UnhandledBreakEngine.java:66-74 [64-72]
  CJK engine's set: Han, Hiragana, Katakana and U+30FC, U+FF70, U+FF9E, U+FF9F
        CjkBreakEngine.java:53-55 [54-55]
  an engine consumes the maximal run of characters of ITS set from the current position
        DictionaryBreakEngine.java:199-202 [198-201]; UnhandledBreakEngine.java:54-57 [51-54]
  a rule segment of one character is never looked at
        RuleBasedBreakIterator.java:1186-1188 [1187-1189]  'if ((endPos - startPos) <= 1) return;'
  the library's own note on the effect
        UnhandledBreakEngine.java:31-33 [32-34]  'TODO: it's odd that findBreaks() can produce
        different results, depending on which scripts have been previously seen by handleChar().'

Alphabet of the model: Katakana letters and Han (script != Common, in the CJK set), U+30FC (script
Common, in the CJK set), U+30A0 (script Common, not in the CJK set), anything else = not a
dictionary character (ends a segment). A segment is a maximal run of dictionary characters (true
for this alphabet with the root word rules, where Han and kana chain).
"""
import sys

P = "ー"  # prolonged sound mark: script Common, in the CJK engine's set
D = "゠"  # double hyphen: script Common, NOT in the CJK engine's set


def kind(ch):
    o = ord(ch)
    if ch == P:
        return "P"
    if ch == D:
        return "D"
    if 0x30A1 <= o <= 0x30FA or 0x4E00 <= o <= 0x9FFF:
        return "K"  # Katakana letter or Han: its own script, in the CJK set
    return None  # not a dictionary character in this model


class Jvm:
    def __init__(self):
        self.cjk_loaded = False
        self.common_unhandled = False

    def select(self, k):
        # list order: unhandled first, then CJK if it was created
        if k in ("P", "D") and self.common_unhandled:
            return "U"
        if k in ("K", "P") and self.cjk_loaded:
            return "C"
        if k == "K":
            self.cjk_loaded = True
            return "C"
        self.common_unhandled = True  # the whole Common script joins the unhandled set
        return "U"

    def consumes(self, eng, k):
        return (k in ("P", "D") and self.common_unhandled) if eng == "U" else k in ("K", "P")

    def analyze(self, text):
        out, i, n = [], 0, len(text)
        while i < n:
            if kind(text[i]) is None:
                i += 1
                continue
            j = i
            while j < n and kind(text[j]) is not None:
                j += 1
            if j - i > 1:  # a one-character segment is skipped
                p = i
                while p < j:
                    eng = self.select(kind(text[p]))
                    q = p
                    while q < j and self.consumes(eng, kind(text[q])):
                        q += 1
                    out.append((eng, text[p:q]))
                    p = q
            i = j
        return out


# scenario: (id, texts analysed first in the same JVM, the text analysed twice, observed 'tokens differ'
#            on branch_11x 75eea467ac52 with ICU4J 79.1, where it was observed)
SCENARIOS = [
    ("S1", ["コンピューター"], "ーング゠ギエゲピノ", True, "a recorded run (one text analysed first)"),
    ("S2", [], "ーング゠ギエゲピノ", False, "a recorded run"),
    ("S3", [], "コ ーング゠ギエゲピノ", False, "a recorded run"),
    ("S4", [], "口訣 ーデキモサラェレョ゠イ ゠h", True, "a recorded run (the upstream issue's text)"),
    ("S5", [], "コーング゠", False, "a recorded run"),
    ("S6", ["パパ゠ママ", "コンピューター"], "ーング゠ギエゲピノ", False, "a recorded run"),
    ("S7", [], "コンピューター ーング゠ギエゲピノ", True, "a recorded run"),
    ("S8", [], "コン ーング゠", True, "a recorded run (the text of the repro test)"),
    ("S9", [], "コン ーン゠", False, "a recorded run (assignment differs, tokens do not)"),
    ("S10", ["パパ゠ママ"], "コン ーング゠", False, "a recorded run (a text with U+30A0 analysed first)"),
]


def main():
    bad = 0
    for sid, before, text, observed, where in SCENARIOS:
        jvm = Jvm()
        for b in before:
            jvm.analyze(b)
        first = jvm.analyze(text)
        second = jvm.analyze(text)
        predicted = first != second
        # soundness direction: tokens observed to differ => the model must say the assignment differs
        ok = predicted or not observed
        note = "" if predicted == observed else "  (assignment differs, tokens equal: necessary condition only)"
        if not ok:
            bad += 1
            note = "  MODEL MISMATCH"
        fmt = lambda a: " ".join("%s[%s]" % e for e in a)
        print("%-3s before=%s text=%s" % (sid, before, text))
        print("    analysis 1: %s" % fmt(first))
        print("    analysis 2: %s" % fmt(second))
        print("    model: assignment %s | observed: tokens %s | %s%s"
              % ("DIFFERS" if predicted else "same", "DIFFER" if observed else "same", where, note))
    print("RESULT: %d scenarios, %d where the tokens differ and the model says the assignment is the same" % (len(SCENARIOS), bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
