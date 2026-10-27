"""Pretty-print an Alloy text solution of FieldSchemaCache.als as a document trace."""
import re, sys

def rel(lines, name):
    for l in lines:
        if l.startswith('this/' + name + '='):
            body = l.split('=', 1)[1].strip()[1:-1]
            if not body:
                return []
            return [tuple(x.strip().split('->')) for x in body.split(',')]
    return []

def main(path):
    lines = [l.strip() for l in open(path)]
    io = dict(rel(lines, 'Schema<:io')); dv = dict(rel(lines, 'Schema<:dv')); sk = dict(rel(lines, 'Schema<:sk'))
    def sch(a):
        if a is None:
            return '-'
        return '(%s,%s,%s)' % (io[a].split('$')[0], dv[a].split('$')[0], sk[a].split('$')[0])
    fts = dict(rel(lines, 'FType<:s')); frozen = {t[0] for t in rel(lines, 'Frozen')}
    def ft(t):
        if t is None:
            return '-'
        return t.split('$')[1] + sch(fts[t]) + ('F' if t in frozen else 'u')
    i1 = dict(rel(lines, 'Doc<:i1')); i2 = dict(rel(lines, 'Doc<:i2')); gf = dict(rel(lines, 'Doc<:gf'))
    col = dict(rel(lines, 'Doc<:col'))
    cfg = dict(rel(lines, 'Config<:fixSkipReset')); cfc = dict(rel(lines, 'Config<:fixCache'))
    print('config fixSkipReset=%s fixCache=%s' % (list(cfg.values()), list(cfc.values())))
    states = sorted({t[0] for t in rel(lines, 'State<:sch')}, key=lambda x: int(x.split('$')[1]))
    S = {r: dict(rel(lines, 'State<:' + r)) for r in ['fi', 'gs', 'sch', 'val', 'doc', 'acc', 'racc', 'rfi', 'rgs']}
    # ordering: State$0 first ... (util/ordering uses atom order)
    for s in states:
        g = lambda r: S[r].get(s)
        print('%s fi=%s gs=%s sch=%s val=%s | rfi=%s rgs=%s' % (s, sch(g('fi')), sch(g('gs')), sch(g('sch')),
              ft(g('val')), sch(g('rfi')), sch(g('rgs'))))
        d = g('doc')
        if d:
            lacc = dict(rel(lines, 'State<:lacc')).get(s)
            kind = ('batch col=%s' % ft(col[d])) if d in col else ('f=[%s, %s] g=%s' % (ft(i1.get(d)), ft(i2.get(d)), gf[d].split('$')[0]))
            print('   doc %s: %s  -> impl=%s ref=%s local-ref=%s' % (d, kind, g('acc').split('$')[0],
                  g('racc').split('$')[0], lacc.split('$')[0] if lacc else '-'))

main(sys.argv[1])
