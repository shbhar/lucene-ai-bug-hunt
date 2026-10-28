"""Table-internal laws of romaji_map.txt (kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables, formal leg 1: no external standard needed except L3).

Model: the table is a finite relation R subset of Key x Spelling. KatakanaRomanizer turns it into a longest-match
transducer T (model element -> Java line, KatakanaRomanizer.java @ a9779ef3c193):
  parse rows, skip '#' lines, split(',') drops trailing empty fields    -> lines 52-65
  keys grouped by length, binary search, longest first                    -> lines 67-78, 171-181
  product of candidate lists per matched key                              -> lines 117-156
  unmatched remainder copied verbatim                                     -> lines 162-167
  input domain = katakana U+30A0..U+30FF or 'a'..'z' (assert at line 104) -> CharSequenceUtils.java:45-53

Laws checked here (each one is a property the table itself states or applies):
  L1 domain: every key lies in the romanizer's input domain (katakana or a-z). A key outside it can never match,
     because romanize() only receives katakana/a-z (JapaneseCompletionFilter.java:224 filters other readings out).
  L2 gemination closure (the table's own rule): for every row 'ッK' whose 'K' is also a row, every spelling s of K that
     starts with a consonant the table doubles elsewhere must appear doubled (s[0]+s) among the spellings of 'ッK'
     (the table does exactly this for ッシャ -> ssha, ッチャ -> ccha, ッジ -> jji, ッフ -> ffu ...).
     L2s (soundness): every spelling of 'ッK' must be such a doubled spelling of some segmentation of K.
     L2p (partial input): the 'ッ<letter>' rows (mid-composition input, upstream test 'サッｋ' -> 'sakk') must cover every
     letter the full ッ rows double.
  L3 IME soundness: every spelling, typed into an IME (the open-source mozc romaji table, model/standards), composes to
     the key itself (record only: a sound but unusual spelling is harmless; used to find copy errors).
  L4 sibling closure (lower tier): extended-katakana families that the table covers for one member but not another.
Usage: python3 -I table_laws.py <romaji_map.txt> <mozc-romanji-hiragana.tsv>
"""
import sys
import unicodedata

VOWELS = set("aeiou")


def load_table(path):
    rows = []  # (lineno, key, [spellings]) exactly as KatakanaRomanizer parses it
    with open(path, encoding="utf-8") as f:
        for no, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if line.startswith("#"):
                continue
            cols = line.strip().split(",")
            # Java String.split drops trailing empty strings; keep that semantics
            while len(cols) > 1 and cols[-1] == "":
                cols.pop()
            if len(cols) < 2:
                continue
            rows.append((no, cols[0], cols[1:]))
    return rows


def in_domain(ch):
    return 0x30A0 <= ord(ch) <= 0x30FF or "a" <= ch <= "z"


def to_hiragana(s):
    out = []
    for ch in s:
        c = ord(ch)
        if 0x30A1 <= c <= 0x30F6:
            out.append(chr(c - 0x60))
        else:
            out.append(ch)
    return "".join(out)


def load_mozc(path):
    rules = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            parts = line.split("\t")
            key = parts[0]
            out = parts[1] if len(parts) > 1 else ""
            pend = parts[2] if len(parts) > 2 else ""
            rules[key] = (out, pend)
    return rules


def mozc_compose(rules, keys):
    """Simulate a romaji IME composer (mozc table semantics): returns (committed, pending)."""
    prefixes = set()
    for k in rules:
        for i in range(1, len(k)):
            prefixes.add(k[:i])
    committed = []
    pending = ""
    i = 0
    queue = list(keys)
    while queue:
        c = queue.pop(0)
        buf = pending + c
        if buf in rules and buf not in prefixes:
            out, pend = rules[buf]
            committed.append(out)
            pending = pend
        elif buf in prefixes:
            pending = buf
        else:
            # buf cannot be extended: resolve pending first
            if pending in rules:
                out, pend = rules[pending]
                committed.append(out)
                pending = pend
                queue.insert(0, c)
            elif pending:
                committed.append(pending[0])
                rest = pending[1:]
                pending = ""
                queue[0:0] = list(rest + c)
            else:
                committed.append(c)
    return "".join(committed), pending


def mozc_decode_full(rules, s):
    committed, pending = mozc_compose(rules, s)
    if pending:
        if pending in rules:
            out, pend = rules[pending]
            committed += out
            pending = pend
    return committed + pending


def segmentations(key, table):
    """All spellings of key over ANY segmentation into table keys (not only longest match)."""
    res = set()
    n = len(key)

    def rec(pos, acc):
        if pos == n:
            res.add(acc)
            return
        for l in range(1, n - pos + 1):
            k = key[pos:pos + l]
            if k in table:
                for s in table[k]:
                    rec(pos + l, acc + s)

    rec(0, "")
    return res


def main():
    rows = load_table(sys.argv[1])
    mozc = load_mozc(sys.argv[2])
    table = {}
    lineno = {}
    for no, k, sp in rows:
        table[k] = sp  # HashMap.put: a later duplicate key overwrites (none expected)
        lineno[k] = no
    print(f"rows parsed {len(rows)} distinct keys {len(table)}")
    viol = []

    # L1 domain law
    for no, k, sp in rows:
        bad = [ch for ch in k if not in_domain(ch)]
        if bad:
            viol.append(("L1-domain", no, k, sp,
                         "key chars outside romanize() input domain: " +
                         ", ".join(f"U+{ord(b):04X} {unicodedata.name(b, '?')}" for b in bad)))

    # doubling letters used by full ッ rows
    sokuon_rows = {k: sp for k, sp in table.items() if k.startswith("ッ") and len(k) >= 2 and all(0x30A0 <= ord(c) <= 0x30FF for c in k)}
    doubled_letters = set()
    for k, sp in sokuon_rows.items():
        for s in sp:
            if len(s) >= 2 and s[0] == s[1] and s[0] not in VOWELS:
                doubled_letters.add(s[0])
    partial_letters = {k[1] for k in table if len(k) == 2 and k[0] == "ッ" and "a" <= k[1] <= "z"}
    print("letters doubled by full ッ rows:", "".join(sorted(doubled_letters)))
    print("letters covered by partial 'ッ<letter>' rows:", "".join(sorted(partial_letters)))

    # L2 closure
    for k, sp in sorted(sokuon_rows.items(), key=lambda kv: lineno[kv[0]]):
        base = k[1:]
        if base not in table:
            continue
        expected = {s[0] + s for s in table[base] if s and s[0] in doubled_letters}
        missing = sorted(expected - set(sp))
        if missing:
            viol.append(("L2-closure", lineno[k], k, sp,
                         f"base {base} (line {lineno[base]}) spells {table[base]}; doubled spellings missing: {missing}"))
        # L2s soundness
        segs = segmentations(base, table)
        allowed = {s[0] + s for s in segs if s and s[0] in doubled_letters}
        unsound = [s for s in sp if s not in allowed]
        if unsound:
            viol.append(("L2s-soundness", lineno[k], k, sp,
                         f"spellings that are not a doubled spelling of {base} (segmentation spellings {sorted(segs)}): {unsound}"))
    # consonant-initial base rows with no ッ row at all (falls back to 'xtu' + base)
    for no, k, sp in rows:
        if k.startswith("ッ") or not all(0x30A0 <= ord(c) <= 0x30FF for c in k):
            continue
        if all(s and s[0] in doubled_letters for s in sp) and ("ッ" + k) not in table:
            viol.append(("L2-no-sokuon-row", no, k, sp,
                         f"no row ッ{k}: ッ{k} romanizes as 'xtu'+{sp} (or via ッ<sub-key> rows)"))
    # L2p partial rows
    for c in sorted(doubled_letters - partial_letters):
        viol.append(("L2p-partial", 0, "ッ" + c, [], f"full ッ rows double '{c}' but there is no partial row ッ{c},{c}{c}"))

    # L3 IME soundness (record)
    for no, k, sp in rows:
        if not all(0x30A0 <= ord(c) <= 0x30FF for c in k):
            continue
        hk = to_hiragana(k)
        for s in sp:
            d = mozc_decode_full(mozc, s)
            if d != hk:
                viol.append(("L3-ime-decode", no, k, sp, f"spelling '{s}' composes to '{d}' in the IME, not '{hk}'"))

    # duplicate spellings for different keys (record)
    by_sp = {}
    for k, sp in table.items():
        for s in sp:
            by_sp.setdefault(s, []).append(k)
    for s, ks in sorted(by_sp.items()):
        if len(ks) > 1:
            viol.append(("REC-shared-spelling", 0, "/".join(ks), [s], f"spelling '{s}' is listed for {ks}"))

    # L4 sibling closure (lower tier)
    dakuten = {"カ": "ガ", "キ": "ギ", "ク": "グ", "ケ": "ゲ", "コ": "ゴ", "サ": "ザ", "シ": "ジ", "ス": "ズ", "セ": "ゼ", "ソ": "ゾ",
               "タ": "ダ", "チ": "ヂ", "ツ": "ヅ", "テ": "デ", "ト": "ド", "ハ": "バ", "ヒ": "ビ", "フ": "ブ", "ヘ": "ベ", "ホ": "ボ"}
    small = set("ァィゥェォャュョ")
    for no, k, sp in rows:
        if len(k) == 2 and k[0] in dakuten and k[1] in small and k[0] not in ("フ", "ウ"):
            v = dakuten[k[0]] + k[1]
            if v not in table:
                viol.append(("L4-voicing-sibling", no, k, sp, f"voiceless {k} has a row but voiced {v} has none"))
    families = [("palatal+ェ", ["シェ", "ジェ", "チェ"]), ("ウ+small vowel", ["ウィ", "ウェ", "ウォ"]),
                ("イェ vs ッイェ", ["ッイェ", "イェ"]), ("ツ+small vowel", ["ツァ", "ツィ", "ツェ", "ツォ"]),
                ("ヴ+small vowel", ["ヴァ", "ヴィ", "ヴェ", "ヴォ"])]
    for name, members in families:
        present = [m for m in members if m in table]
        absent = [m for m in members if m not in table]
        if present and absent:
            viol.append(("L4-family", 0, name, [], f"rows present {present}, absent {absent}"))
        elif not present:
            viol.append(("L4-family-none", 0, name, [], f"no row for any of {members}"))

    for v in viol:
        tag, no, k, sp, msg = v
        print(f"{tag:20s} line {no:3d}  {k:6s} {sp}  {msg}")
    from collections import Counter
    print("summary:", dict(Counter(v[0] for v in viol)))


if __name__ == "__main__":
    main()
