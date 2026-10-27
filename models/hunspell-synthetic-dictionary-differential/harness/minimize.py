"""minimize.py DIR OUTDIR [max_dicts] [kinds]: shrink every differing dictionary in DIR/diffs.tsv to a minimal
(.aff, .dic, word) triple on which Lucene (knownfix shadow classes) and C Hunspell without the 3+-part heuristic
still disagree in the same direction (greedy one-element-at-a-time removal to a fixpoint).

Removal units: a directive line, a table row (COMPOUNDRULE, CHECKCOMPOUNDPATTERN, REP, ICONV, OCONV), an affix rule,
a whole affix class, a continuation flag of an affix rule, a .dic entry, a flag of a .dic entry. The word is fixed
(the shortest differing word of the dictionary). Writes OUTDIR/<id>.aff/.dic/.word and OUTDIR/min.tsv.
"""
import os
import subprocess
import sys

U = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
L = "$LUCENE-a9779ef"
J = "$WORK"
CP = (f"{U}/scratch/cls:{os.environ.get('EXTRA_CLS', '')}:{U}/scratch/knownfix-cls:"
      f"{L}/lucene/core/build/libs/lucene-core-11.0.0-SNAPSHOT.jar:"
      f"{L}/lucene/analysis/common/build/libs/lucene-analysis-common-11.0.0-SNAPSHOT.jar")
HS = f"{U}/scratch/hsbuild/hsnative"
TABLES = ("COMPOUNDRULE", "CHECKCOMPOUNDPATTERN", "REP", "ICONV", "OCONV")


def parse(aff, dic):
    """-> (directives, tables{name: rows}, classes[[header_parts, rules]], entries[(word, flags list)], ftype)"""
    lines = [l for l in aff.split("\n") if l.strip()]
    directives, tables, classes = [], {}, []
    ftype = "default"
    i = 0
    while i < len(lines):
        p = lines[i].split()
        if p[0] in TABLES and len(p) == 2 and p[1].isdigit():
            n = int(p[1])
            tables[p[0]] = [" ".join(lines[i + 1 + k].split()[1:]) for k in range(n)]
            i += n + 1
            continue
        if p[0] in ("PFX", "SFX") and len(p) == 4:
            n = int(p[3])
            rules = [lines[i + 1 + k].split()[2:] for k in range(n)]
            classes.append([p[:3], rules])
            i += n + 1
            continue
        if p[0] == "FLAG":
            ftype = p[1]
        directives.append(lines[i])
        i += 1
    entries = []
    for l in dic.split("\n")[1:]:
        if not l.strip():
            continue
        w, _, fl = l.partition("/")
        entries.append((w, split_flags(fl, ftype)))
    return [directives, tables, classes, entries, ftype]


def split_flags(fl, ftype):
    if not fl:
        return []
    if ftype == "long":
        return [fl[k:k + 2] for k in range(0, len(fl), 2)]
    if ftype == "num":
        return fl.split(",")
    return list(fl)


def join_flags(fl, ftype):
    return ",".join(fl) if ftype == "num" else "".join(fl)


def render(m):
    directives, tables, classes, entries, ftype = m
    out = list(directives)
    for name, rows in tables.items():
        if rows:
            out.append(f"{name} {len(rows)}")
            out.extend(f"{name} {r}" for r in rows)
    for hdr, rules in classes:
        if not rules:
            continue
        out.append("")
        out.append(f"{hdr[0]} {hdr[1]} {hdr[2]} {len(rules)}")
        for r in rules:
            out.append(" ".join([hdr[0], hdr[1]] + r))
    dic = [str(len(entries))] + [w + ("/" + join_flags(f, ftype) if f else "") for w, f in entries]
    return "\n".join(out) + "\n", "\n".join(dic) + "\n"


def variants(m):
    directives, tables, classes, entries, ftype = m
    for k in range(len(directives)):
        if directives[k].startswith("FLAG"):
            continue
        yield [directives[:k] + directives[k + 1:], tables, classes, entries, ftype]
    for name in tables:
        for k in range(len(tables[name])):
            t = dict(tables); t[name] = tables[name][:k] + tables[name][k + 1:]
            yield [directives, t, classes, entries, ftype]
    for c in range(len(classes)):
        yield [directives, tables, classes[:c] + classes[c + 1:], entries, ftype]
        hdr, rules = classes[c]
        for k in range(len(rules)):
            nc = list(classes); nc[c] = [hdr, rules[:k] + rules[k + 1:]]
            yield [directives, tables, nc, entries, ftype]
            r = rules[k]
            if "/" in r[1]:
                aff, _, cont = r[1].partition("/")
                fl = split_flags(cont, ftype)
                for j in range(len(fl)):
                    rest = fl[:j] + fl[j + 1:]
                    nr = list(r); nr[1] = aff + ("/" + join_flags(rest, ftype) if rest else "")
                    nc = list(classes); nrules = list(rules); nrules[k] = nr; nc[c] = [hdr, nrules]
                    yield [directives, tables, nc, entries, ftype]
            if r[0] != "0":
                nr = list(r); nr[0] = "0"; nr[2] = "."
                nc = list(classes); nrules = list(rules); nrules[k] = nr; nc[c] = [hdr, nrules]
                yield [directives, tables, nc, entries, ftype]
            if len(r) > 2 and r[2] != ".":
                nr = list(r); nr[2] = "."
                nc = list(classes); nrules = list(rules); nrules[k] = nr; nc[c] = [hdr, nrules]
                yield [directives, tables, nc, entries, ftype]
    for k in range(len(entries)):
        if len(entries) > 1:
            yield [directives, tables, classes, entries[:k] + entries[k + 1:], ftype]
        w, fl = entries[k]
        for j in range(len(fl)):
            ne = list(entries); ne[k] = (w, fl[:j] + fl[j + 1:])
            yield [directives, tables, classes, ne, ftype]


class Oracle:
    def __init__(self, tmp):
        self.tmp = tmp
        self.p = subprocess.Popen([J, "-ea", "-Xss1m", "-cp", CP, "SpellServer"], stdin=subprocess.PIPE,
                                  stdout=subprocess.PIPE, text=True, encoding="latin-1")
        self.n = 0

    def run(self, m, word):
        aff, dic = render(m)
        self.n += 1
        a, d = f"{self.tmp}/m.aff", f"{self.tmp}/m.dic"
        with open(a, "w", encoding="latin-1") as f: f.write(aff)
        with open(d, "w", encoding="latin-1") as f: f.write(dic)
        self.p.stdin.write(f"{a}\t{d}\t{word}\n"); self.p.stdin.flush()
        lres = self.p.stdout.readline().strip()
        with open(f"{self.tmp}/w", "w", encoding="latin-1") as f: f.write(word + "\n")
        with open(f"{self.tmp}/man", "w") as f: f.write(f"{a}\t{d}\t{self.tmp}/w\t{self.tmp}/o\n")
        env = dict(os.environ, H237_NOHEUR="1", H237_CFIX="1")
        subprocess.run([HS, f"{self.tmp}/man"], env=env, timeout=30)
        with open(f"{self.tmp}/o", encoding="latin-1") as f:
            cres = f.read().split("\t")[1] if True else ""
        return lres, cres


def minimize(orc, m, word, want):
    changed = True
    while changed:
        changed = False
        for v in variants(m):
            if orc.run(v, word) == want:
                m = v
                changed = True
                break
    return m


def main():
    d, out = sys.argv[1], sys.argv[2]
    maxd = int(sys.argv[3]) if len(sys.argv) > 3 else 10 ** 9
    kinds = sys.argv[4].split(",") if len(sys.argv) > 4 else ["SPELL_L0_C1", "SPELL_L1_C0", "EXC"]
    os.makedirs(out, exist_ok=True)
    tmp = os.path.join(out, "tmp"); os.makedirs(tmp, exist_ok=True)
    best = {}
    with open(os.path.join(d, "diffs.tsv"), encoding="latin-1") as f:
        next(f)
        for line in f:
            p = line.rstrip("\n").split("\t")
            if p[1] not in kinds:
                continue
            key = (p[0], p[1])
            if key not in best or (len(p[2]), p[2]) < (len(best[key]), best[key]):
                best[key] = p[2]
    orc = Oracle(tmp)
    rows = []
    for (i, kind), word in sorted(best.items())[:maxd]:
        aff = open(os.path.join(d, i + ".aff"), encoding="latin-1").read()
        dic = open(os.path.join(d, i + ".dic"), encoding="latin-1").read()
        m = parse(aff, dic)
        want = orc.run(m, word)
        if want[0] == want[1] or want[1] in ("CRASH", "TIMEOUT"):
            rows.append((i, kind, word, "NOREPRO", str(want)))
            continue
        m = minimize(orc, m, word, want)
        a, dd = render(m)
        base = os.path.join(out, f"{i}.{kind}")
        open(base + ".aff", "w", encoding="latin-1").write(a)
        open(base + ".dic", "w", encoding="latin-1").write(dd)
        open(base + ".word", "w", encoding="latin-1").write(word + "\n")
        sig = " | ".join(l for l in a.split("\n") if l and not l.startswith(("PFX", "SFX")) or
                         (l.startswith(("PFX", "SFX")) and len(l.split()) > 4))
        rows.append((i, kind, word, f"L={want[0]} C={want[1]}", sig.replace("\t", " ") + " || " +
                     " ".join(dd.split("\n")[1:]).strip()))
        print(rows[-1], flush=True)
    with open(os.path.join(out, "min.tsv"), "w", encoding="latin-1") as f:
        for r in rows:
            f.write("\t".join(r) + "\n")
    orc.p.stdin.close()


if __name__ == "__main__":
    main()
