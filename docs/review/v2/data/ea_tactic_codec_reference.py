# EA FC27 taktik kodu referans codec (EaTacticCodec.java birebir portu). Test oracle olarak kullan.
# Kullanım: FCAREER_BACKEND=/yol/FCarrierTacticBuilderBackend python3 ea_tactic_codec_reference.py KOD [KOD...]
import json,sys
import os
B=os.environ.get('FCAREER_BACKEND','.').rstrip('/')+'/'
D=json.load(open(B+'src/main/resources/seed/ea-tactic-codec.json'))
ALPHA=D['alphabet']; SUB=D['substitutions']; UNSUB={v:k for k,v in SUB.items()}
FORM={f['id']:f for f in D['formations']}; ROLE={r['id']:r for r in D['roles']}
BU={'BALANCED':(2,0),'COUNTER':(3,1),'SHORT_PASSING':(0,2)}
ABS={}; REV={}
for pos,txt in D['positionTables'].items():
    rel={}
    for e in txt.split(','):
        k,v=e.split(':'); v=int(v); v=v-64 if v>32 else v; rel[k]=v
    m=min(rel.values()); ABS[pos]={k:v-m for k,v in rel.items()}; REV[pos]={v-m:tuple(map(int,k.split('.'))) for k,v in rel.items()}
def split(v,sh):
    x=v<<sh; return [(x>>(6*i))&63 for i in range(12)]
def acc(add,v,ref,sh):
    a=split(v,sh); b=split(ref,sh)
    for i in range(12): add[i]+=a[i]-b[i]
def encode(fid,bu,lh,slots):
    f=FORM[fid]; add=[0]*12; shift=0
    for s in range(11):
        pos=f['positions'][s]; r,fo=slots[s]; a=ABS[pos][f'{r}.{fo}']; d=ABS[pos][f"{f['roleIds'][s]}.0"]
        shift+=a-d; acc(add,a,d,59-4*s)
    shift+=lh-50; acc(add,lh,50,63)
    shift+=BU[bu][1]; acc(add,BU[bu][0],2,70)
    base=f['base']; out=[]
    for i in range(12):
        if i==1:
            c=(base[0]+shift+5)//64-(base[0]+5)//64; out.append((base[i]+add[i]+c)%64)
        else: out.append((base[i]+add[i]+shift)%64)
    return ''.join(SUB.get(ALPHA[d],ALPHA[d]) for d in out)
def digits(code):
    return [ALPHA.index(UNSUB.get(c,c)) for c in code.strip()]
def decode(code):
    dg=digits(code); res=[]
    for f in D['formations']:
        base=f['base']; sh=(dg[0]-base[0])%64
        if (dg[2]-base[2])%64!=sh: continue
        # brute: buildUp x lh, then slots via DFS with digit pruning like Java
        resid=[(dg[i]-base[i]-sh)%64 for i in range(12)]
        def final(add,nxt):
            hi=-1 if nxt<0 else (nxt+3)//6
            for i in range(11,max(hi,2),-1):
                if add[i]%64!=resid[i]: return False
            return True
        for bu,(fv,cv) in BU.items():
            add0=[0]*12; acc(add0,fv,2,70)
            for lh in range(1,101):
                add=add0[:]; acc(add,lh,50,63)
                if not final(add,59): continue
                chosen=[None]*11
                def dfs(s,add):
                    if s==11:
                        tot=lh-50+cv
                        for k in range(11):
                            pos=f['positions'][k]; tot+=ABS[pos][f'{chosen[k][0]}.{chosen[k][1]}']-ABS[pos][f"{f['roleIds'][k]}.0"]
                        if tot%64!=sh: return False
                        c=(base[0]+tot+5)//64-(base[0]+5)//64
                        return dg[1]==(base[1]+c)%64 and final(add,-1)
                    pos=f['positions'][s]; d=ABS[pos][f"{f['roleIds'][s]}.0"]
                    for a,rf in REV[pos].items():
                        ad=add[:]; acc(ad,a,d,59-4*s); chosen[s]=rf
                        nxt=59-4*(s+1) if s<10 else -1
                        if final(ad,nxt) and dfs(s+1,ad): return True
                    return False
                if dfs(0,add): res.append((f['id'],f['label'],bu,lh,list(chosen)))
    return res
def pretty(r):
    fid,lab,bu,lh,ch=r; f=FORM[fid]
    return f"{lab} ({fid}) buildUp={bu} line={lh}\n"+'\n'.join(f"  {i:2d} {f['positions'][i]:4s} {ROLE[x[0]]['name']} / {ROLE[x[0]]['focuses'][x[1]]}" for i,x in enumerate(ch))
if __name__=='__main__':
    for c in sys.argv[1:]:
        rs=decode(c); print(c,'->',len(rs),'match')
        for r in rs: print(pretty(r))
