import unittest
import aivora_akcp as a
class TestAkcp(unittest.TestCase):
    def setUp(self): self.c=[{'config_name':'测试','provider':'Synthetic','api_key':'not-a-real-key','endpoint':'https://example.invalid/v1','models':[], 'default_model':'','notes':'','enabled':True,'extensions':{}}]
    def test_roundtrip(self):
        data,cred=a.export(self.c); self.assertEqual(a.decrypt(data,cred),self.c)
    def test_wrong_credential(self):
        data,_=a.export(self.c)
        with self.assertRaises(a.AkcpError): a.decrypt(data,a.credential())
    def test_tamper(self):
        data,cred=a.export(self.c); data=data[:-1]+bytes([data[-1]^1])
        with self.assertRaises(a.AkcpError): a.decrypt(data,cred)
    def test_truncated(self):
        data,cred=a.export(self.c)
        with self.assertRaises(a.AkcpError): a.decrypt(data[:-1],cred)
    def test_version(self):
        data,cred=a.export(self.c); data=data[:8]+b'\0\x02'+data[10:]
        with self.assertRaises(a.AkcpError): a.decrypt(data,cred)
if __name__=='__main__': unittest.main()