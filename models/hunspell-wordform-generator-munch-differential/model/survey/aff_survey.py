"""Survey real .aff files for affix-stacking shapes (read-only, inputs passed as paths).

For each .aff: parse FLAG type, AF aliases, PFX/SFX classes (flag, cross product Y/N) and every entry's
continuation flags. Report
  twofold_N: pairs (inner class I, outer class O) of the same kind (SFX->SFX, or PFX->PFX with COMPLEXPREFIXES)
             where an entry of I carries O in its continuation and I or O is cross-product N
  pfx_enables_sfx: PFX entries whose continuation contains a SFX class flag (suffix enabled only by a prefix)
"""
import sys, re, collections, json

def read_lines(p):
    raw = open(p, 'rb').read()
    enc = 'latin-1'
    for l in raw.split(b'\n')[:2000]:
        m = re.match(rb'^SET\s+(\S+)', l)
        if m:
            e = m.group(1).decode('ascii', 'replace').lower()
            enc = {'utf-8': 'utf-8', 'utf8': 'utf-8'}.get(e, e)
            break
    try:
        txt = raw.decode(enc, 'replace')
    except LookupError:
        txt = raw.decode('latin-1')
    return txt.split('\n')

def parse_flags(s, ftype):
    if ftype == 'long':
        return [s[i:i+2] for i in range(0, len(s) - 1, 2)]
    if ftype == 'num':
        return [x for x in s.split(',') if x]
    return list(s)

def survey(p):
    lines = read_lines(p)
    ftype = 'char'; af = {}; complex_ = False; needaffix = circumfix = None
    for l in lines:
        t = l.split()
        if not t: continue
        if t[0] == 'FLAG' and len(t) > 1: ftype = t[1].lower() if t[1].lower() in ('long', 'num') else 'char'
        if t[0] == 'COMPLEXPREFIXES': complex_ = True
        if t[0] == 'NEEDAFFIX' and len(t) > 1: needaffix = t[1]
        if t[0] == 'CIRCUMFIX' and len(t) > 1: circumfix = t[1]
    for l in lines:
        t = l.split()
        if len(t) >= 2 and t[0] == 'AF' and not t[1].isdigit() or (len(t) >= 2 and t[0] == 'AF' and af is not None and len(af) > 0):
            af[len(af) + 1] = parse_flags(t[1], ftype)
        elif len(t) == 2 and t[0] == 'AF' and t[1].isdigit() and not af:
            af = {}  # header line
    classes = {}  # flag -> (kind, cross)
    entries = collections.defaultdict(list)  # flag -> list of continuation flag lists
    for l in lines:
        t = l.split()
        if len(t) >= 4 and t[0] in ('PFX', 'SFX'):
            if t[2] in ('Y', 'N') and re.fullmatch(r'\d+', t[3]) and t[1] not in classes:
                classes[t[1]] = (t[0], t[2])
            elif len(t) >= 4 and t[1] in classes:
                a = t[3]
                if '/' in a:
                    cont = a.split('/', 1)[1]
                    if af and cont.isdigit():
                        flags = af.get(int(cont), [])
                    else:
                        flags = parse_flags(cont, ftype)
                    entries[t[1]].append(flags)
    twofold_n = collections.Counter(); pfx_sfx = collections.Counter()
    for inner, conts in entries.items():
        ik, ic = classes[inner]
        for flags in conts:
            for o in set(flags):
                if o not in classes: continue
                ok_, oc = classes[o]
                if ok_ == ik and ((ik == 'SFX' and not complex_) or (ik == 'PFX' and complex_)):
                    if ic == 'N' or oc == 'N':
                        twofold_n[(ik, inner, ic, o, oc)] += 1
                if ik == 'PFX' and ok_ == 'SFX':
                    pfx_sfx[(inner, ic, o, oc)] += 1
    return dict(n_classes=len(classes), twofold_N_pairs=len(twofold_n), twofold_N_entries=sum(twofold_n.values()),
                twofold_N_examples=[list(k) for k in list(twofold_n)[:5]],
                pfx_enables_sfx_pairs=len(pfx_sfx), pfx_enables_sfx_entries=sum(pfx_sfx.values()),
                pfx_enables_sfx_examples=[list(k) for k in list(pfx_sfx)[:5]], complexprefixes=complex_)

if __name__ == '__main__':
    out = {}
    for p in sys.argv[1:]:
        try:
            out[p] = survey(p)
        except Exception as e:
            out[p] = {'error': repr(e)}
    json.dump(out, sys.stdout, indent=1)
