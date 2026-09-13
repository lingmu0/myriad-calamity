import json,http.client,sys
from pathlib import Path
BASE=Path(__file__).resolve().parents[1]
STATE=BASE/'.work/bb-session.json'
STATE.parent.mkdir(parents=True,exist_ok=True)
def request(method,params=None,notification=False):
    state=json.loads(STATE.read_text()) if STATE.exists() else {}
    headers={'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
    if 'session' in state:headers['Mcp-Session-Id']=state['session']
    body={'jsonrpc':'2.0','method':method}
    if not notification:body['id']=1
    if params is not None:body['params']=params
    conn=http.client.HTTPConnection('127.0.0.1',3000,timeout=45)
    conn.request('POST','/bb-mcp',json.dumps(body),headers)
    response=conn.getresponse()
    session=response.getheader('Mcp-Session-Id')
    if session:STATE.write_text(json.dumps({'session':session}))
    if response.status>=400:
        raise RuntimeError(str(response.status)+' '+response.read().decode())
    if response.status==202 or notification:conn.close();return {}
    if 'text/event-stream' in response.getheader('Content-Type',''):
        while True:
            line=response.readline().decode().strip()
            if line.startswith('data:'):
                obj=json.loads(line[5:])
                if obj.get('id')==1:break
            if not line and response.isclosed():raise RuntimeError('No MCP response')
    else:obj=json.loads(response.read())
    conn.close()
    if 'error' in obj:raise RuntimeError(obj['error'])
    return obj.get('result',{})
if __name__=='__main__':
    if sys.argv[1]=='init':
        if STATE.exists():STATE.unlink()
        print(json.dumps(request('initialize',{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'MyriadCalamityModeling','version':'0.2.0'}}),ensure_ascii=False))
        request('notifications/initialized',notification=True)
        data=request('tools/list')
        (BASE/'.work/bb-tools.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),'utf8')
        for tool in data.get('tools',[]):print(tool['name'],':',tool.get('description','')[:180])
    else:
        params=json.loads(Path(sys.argv[1]).read_text('utf-8-sig'))
        result=request('tools/call',params)
        (BASE/'.work/bb-last.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf8')
        for content in result.get('content',[]):
            if content.get('type')=='text':print(content['text'])
            elif content.get('type')=='image':print('[Image returned; stored in .work/bb-last.json]')
