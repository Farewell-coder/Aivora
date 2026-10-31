#!/usr/bin/env python3
"""Independent AKCP 1.0 implementation; does not import Aivora code."""
import argparse, base64, json, os, struct
from argon2.low_level import hash_secret_raw, Type
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
MAGIC=b"AKCP\r\n\x1a\n"; HEADER=64; MAX=16*1024*1024
class AkcpError(Exception): pass

def credential(): return base64.b32encode(os.urandom(32)).decode().rstrip('=')
def export(configs, cred=None):
    cred=cred or credential(); normalized=cred.replace('-','').upper()
    if len(normalized)!=52: raise AkcpError('credential must encode 256 bits')
    payload=json.dumps({'schema':'akcp.config-set/1','configs':configs},ensure_ascii=False,indent=2).encode()
    salt,nonce=os.urandom(16),os.urandom(12)
    header=struct.pack('>8sHHHBBBBIIII16s12s2s',MAGIC,1,1,1,1,1,0,64,65536,3,1,len(payload)+16,salt,nonce,b'\0\0')
    key=hash_secret_raw(normalized.encode(),salt,3,65536,1,32,Type.ID)
    return header+AESGCM(key).encrypt(nonce,payload,header),cred

def decrypt(data,cred):
    if len(data)<80 or len(data)>MAX: raise AkcpError('invalid container size')
    h=data[:64]
    try: magic,pv,cv,crypto,kdf,cipher,flags,hs,m,it,par,n,salt,nonce,res=struct.unpack('>8sHHHBBBBIIII16s12s2s',h)
    except struct.error as e: raise AkcpError('invalid header') from e
    if magic!=MAGIC or hs!=64 or len(data)!=64+n or n<16: raise AkcpError('invalid container')
    if (pv,cv,crypto,kdf,cipher,flags,m,it,par,res)!=(1,1,1,1,1,0,65536,3,1,b'\0\0'): raise AkcpError('unsupported parameters')
    try:
        key=hash_secret_raw(cred.replace('-','').upper().encode(),salt,it,m,par,32,Type.ID)
        obj=json.loads(AESGCM(key).decrypt(nonce,data[64:],h))
    except Exception as e: raise AkcpError('authentication or payload failed') from e
    if obj.get('schema')!='akcp.config-set/1' or not isinstance(obj.get('configs'),list): raise AkcpError('invalid payload')
    for c in obj['configs']:
        if any(not isinstance(c.get(k),str) or not c[k] for k in ('config_name','provider','api_key','endpoint')): raise AkcpError('missing core field')
    return obj['configs']

def main():
    p=argparse.ArgumentParser(); p.add_argument('mode',choices=['encrypt','decrypt']); p.add_argument('file'); p.add_argument('--credential'); p.add_argument('--out'); a=p.parse_args()
    if not a.file.endswith('.aivora'): raise SystemExit('INVALID_EXTENSION: only lowercase .aivora is accepted')
    if a.mode=='decrypt':
        if not a.credential: raise SystemExit('credential required')
        print(json.dumps(decrypt(open(a.file,'rb').read(),a.credential),ensure_ascii=False,indent=2))
    else:
        configs=json.load(open(a.file)); data,cred=export(configs,a.credential); out=a.out or 'export.aivora'; open(out,'wb').write(data); print(cred)
if __name__=='__main__': main()