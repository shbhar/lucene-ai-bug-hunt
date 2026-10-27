# Classify syntax-level toQueryString round-trip mismatches from FlexRoundTrip DUMP lines (read from stdin).
# Tags (each a separate defect class):
#   AT_IN_TERM        the output has an unescaped '@' that is not the min-should-match operator after ')'
#   ANDOR_WORD        an output term (optionally with a ~fuzzy suffix) is exactly '&&' or '||'
#   FIELD_NAME_ESCAPE the source has an explicit field name that contains an escaped character
import re, sys, collections

def unescaped_positions(s, ch):
    out, i = [], 0
    while i < len(s):
        if s[i] == '\\': i += 2; continue
        if s[i] == ch: out.append(i)
        i += 1
    return out

def explicit_field_has_escape(s):
    # scan the first clause: skip leading modifiers/space/paren, read a field name up to the first unescaped ':' or '='
    i = 0
    while i < len(s) and s[i] in ' +-!(': i += 1
    j, esc = i, False
    while j < len(s):
        c = s[j]
        if c == '\\': esc = True; j += 2; continue
        if c in ':=': return esc and j > i
        if c == ' ':
            k = j
            while k < len(s) and s[k] == ' ': k += 1
            return esc and k < len(s) and s[k] in ':='
        if c in '()"[{~^/@': return False
        j += 1
    return False

cls = collections.Counter(); ex = collections.defaultdict(list)
for line in sys.stdin:
    if not line.startswith('DUMP\t'): continue
    _, kind, rest = line.rstrip('\n').split('\t', 2)
    m = re.match(r'\[(.*?)\] -> \[(.*?)\]  ', rest + '  ')
    s, s2 = m.group(1), m.group(2)
    tags = []
    def group_close_before(p):
        q = s2[:p].rstrip()
        if not q.endswith(')'): return False
        k, n = len(q) - 2, 0
        while k >= 0 and q[k] == '\\': n += 1; k -= 1
        return n % 2 == 0   # the ')' is not itself escaped
    if any(not group_close_before(p) for p in unescaped_positions(s2, '@')):
        tags.append('AT_IN_TERM')
    for t in re.split(r'[ ()]+', s2):
        t = t.split(':')[-1]
        t = re.sub(r'([~^][0-9.]*)+$', '', t)
        if t in ('&&', '||'): tags.append('ANDOR_WORD'); break
    if explicit_field_has_escape(s): tags.append('FIELD_NAME_ESCAPE')
    if not tags: tags.append('OTHER')
    key = kind + ' | ' + '+'.join(tags)
    cls[key] += 1
    if len(ex[key]) < 5: ex[key].append(rest)
tot = sum(cls.values())
for k, v in sorted(cls.items()):
    print(f'{v:6d}  {k}')
    for e in ex[k]: print('          ', e)
print(f'{tot:6d}  total; OTHER = {sum(v for k, v in cls.items() if k.endswith("OTHER"))}')
