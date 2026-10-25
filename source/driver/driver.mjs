// 工作岛的 driver：跑在 cccodroid 网关的终端里（不是 Claude 那一轮的进程树），
// 订阅网关事件流，把状态经 am start-foreground-service 送给「工作岛」前台服务。
// 完成后再等 8 秒发 IDLE，让岛收起来。ColorOS 会冻结工作岛自己，所以定时器放在这里。
//
// 收尾判定要点：run_end 可能来自别的轮次（排队 / 自动压缩 / 子任务），
// 不能收到就收工；先看事件里的 runId，再回 /api/state 确认 runs 真的空了，才发完成。
import http from 'node:http';
import fs from 'node:fs';
import { execFile, execFileSync } from 'node:child_process';

const HOME = '/data/user/0/cc.codroid/files/home';
const TOKEN = fs.readFileSync(HOME + '/.gateway-token', 'utf8').trim();
try { fs.writeFileSync(HOME + '/.work-island.pid', String(process.pid)); } catch {}
const PORT = 8791;
const PKG = 'com.dafeiyu.workisland';
const SVC = PKG + '/.IslandService';
const A = {
  start: PKG + '.START',
  update: PKG + '.UPDATE',
  done: PKG + '.DONE',
  idle: PKG + '.IDLE',
};

const TOOL_CN = {
  Bash: '执行命令', Read: '读文件', Write: '写文件', Edit: '改文件',
  Glob: '找文件', Grep: '搜代码', WebSearch: '查资料', WebFetch: '读网页',
  Task: '子任务', TodoWrite: '整理清单', NotebookEdit: '改笔记',
  Agent: '子任务', Skill: '调用技能', KillShell: '收尾命令',
};

const TOOL_STATE = {
  Bash: 'running', KillShell: 'running',
  Read: 'reading', Glob: 'reading', Grep: 'reading',
  Write: 'writing', Edit: 'writing', NotebookEdit: 'writing',
  WebSearch: 'searching', WebFetch: 'searching',
  Task: 'subtask', Agent: 'subtask', Skill: 'working',
};

const toolLabel = (name) => TOOL_CN[name] || name || '工具';
const toolState = (name) => TOOL_STATE[name] || 'working';

/** TodoWrite 的清单进度：{ title, text }；不是清单工具返回 null */
function todoProgress(ev) {
  const todos = ev && ev.input && Array.isArray(ev.input.todos) ? ev.input.todos : null;
  if (!todos) return null;
  const total = todos.length;
  const done = todos.filter((t) => t && t.status === 'completed').length;
  return { title: '整理清单', text: `清单 ${done}/${total}`, state: 'planning' };
}

let active = null;          // { id, startedAt, last }
let doneTimer = null;
let idleTimer = null;
let verifying = false;

const log = (...a) => console.log(new Date().toISOString(), ...a);

function send(action, title, text, since, state) {
  const args = ['start-foreground-service', '--user', '0', '-n', SVC, '-a', action];
  if (title) args.push('--es', 'title', title);
  if (text) args.push('--es', 'text', text);
  if (state) args.push('--es', 'state', state);
  if (since) args.push('--el', 'since', String(since));
  const attempt = (n) => execFile('am', args, { timeout: 8000 }, (e, out, err) => {
    if (!e) return;
    if (n < 2) return setTimeout(() => attempt(n + 1), 800);
    log('am failed', e.message, String(err || '').slice(0, 120));
  });
  attempt(0);
}

function start(run, text) {
  if (active) {
    if (run && run.id) active.id = run.id;
    return update(text);
  }
  if (doneTimer) { clearTimeout(doneTimer); doneTimer = null; }
  if (idleTimer) { clearTimeout(idleTimer); idleTimer = null; }
  verifying = false;
  active = { id: (run && run.id) || '', startedAt: Number(run && run.startedAt) || Date.now(), last: '' };
  log('start', active.id || '?', active.startedAt);
  update(text || '正在处理…', '正在干活', 'working');
}

function update(text, title, state) {
  if (!active) return;
  const t = title || '正在干活';
  const st = state || 'working';
  const key = t + '|' + text + '|' + st;
  if (key === active.last) return;
  active.last = key;
  send(A.update, t, text, active.startedAt, st);
}

function finish(reason) {
  if (!active) return;
  const ms = Math.max(0, Date.now() - active.startedAt);
  active = null;
  verifying = false;
  const sec = Math.round(ms / 1000);
  const human = sec < 60 ? `${sec} 秒` : `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`;
  // 连续两轮之间的空隙不急着报完成：等 3 秒，真有新的一轮就直接接着干。
  if (doneTimer) clearTimeout(doneTimer);
  doneTimer = setTimeout(() => {
    doneTimer = null;
    if (active) return;
    log('done', human, reason || '');
    send(A.done, '', '', 0, 'done');
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = setTimeout(() => { idleTimer = null; if (!active) { log('idle'); send(A.idle, '', '', 0, 'idle'); } }, 8000);
  }, 3000);
}

/** 收到疑似收尾的信号后，回 /api/state 再确认一次：runs 真空了才算完。 */
function verifyEnd(reason) {
  if (!active || verifying) return;
  verifying = true;
  setTimeout(async () => {
    try {
      const st = await getState();
      const runs = st ? (st.runs || (st.run ? [st.run] : [])) : [];
      if (!st || runs.length === 0) {
        finish(reason || '');
      } else if (active && !active.id && runs[0] && runs[0].id) {
        active.id = runs[0].id;
      }
    } finally {
      verifying = false;
    }
  }, 2500);
}

function handleEvent(ev) {
  if (!ev || !ev.type) return;
  switch (ev.type) {
    case 'hello':
      if (ev.run) start(ev.run, '正在处理…');
      else if (ev.runs && ev.runs.length === 0) verifyEnd('');
      break;
    case 'state':
      if (ev.run) start(ev.run, '正在处理…');
      else if (ev.runs && ev.runs.length === 0) verifyEnd('');
      break;
    case 'run_start':
      start(ev, '正在处理…');
      break;
    case 'run_busy':
      update('继续处理…', '正在干活', 'working');
      break;
    case 'phase':
      if (ev.phase === 'wait') update('等模型回复…', '等模型', 'thinking');
      else if (ev.phase === 'think') update('正在想…', '思考中', 'thinking');
      else if (ev.phase === 'text') update('正在写回复…', '写回复', 'writing');
      else if (ev.phase === 'tool') update('用 ' + toolLabel(ev.name) + '…', toolLabel(ev.name), toolState(ev.name));
      else if (ev.phase === 'retry') update('重试中…', '重试中', 'thinking');
      break;
    case 'tool_use': {
      const todo = todoProgress(ev);
      if (todo) update(todo.text, todo.title, todo.state);
      else update('用 ' + toolLabel(ev.name) + '…', toolLabel(ev.name), toolState(ev.name));
      break;
    }
    case 'perm_request':
      update('需要你确认：' + toolLabel(ev.tool || ev.name), '等你确认', 'waiting');
      break;
    case 'perm_resolved':
      update('继续…', '正在干活', 'working');
      break;
    case 'compacting':
      update('整理上下文…', '整理上下文', 'thinking');
      break;
    case 'sub_progress':
      update('子任务：' + (ev.desc || '进行中') + '…', '子任务', 'subtask');
      break;
    case 'run_idle':
      if (ev.bg > 0) update('后台任务还在跑…', '后台任务', 'working');
      break;
    case 'error':
      // 不转发 message：可能带路径/命令输出
      update('', '出错了', 'error');
      break;
    case 'run_end':
      // 不按 runId 直接排除：换轮时 active.id 可能还是上一轮的。
      // 统一回 /api/state 看 runs —— 只要还有一轮在跑，就不算完。
      if (!active) break;
      verifyEnd(ev.aborted ? '已中止' : '');
      break;
  }
}

function connect() {
  const req = http.get({
    host: '127.0.0.1', port: PORT, path: '/api/events',
    headers: { 'X-CC-Token': TOKEN, Accept: 'text/event-stream' },
  }, (res) => {
    if (res.statusCode !== 200) {
      res.resume();
      return setTimeout(connect, 3000);
    }
    log('sse connected');
    let buf = '';
    res.setEncoding('utf8');
    res.on('data', (chunk) => {
      buf += chunk;
      let i;
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const block = buf.slice(0, i);
        buf = buf.slice(i + 2);
        for (const line of block.split('\n')) {
          if (!line.startsWith('data:')) continue;
          const raw = line.slice(5).trim();
          if (!raw) continue;
          try { handleEvent(JSON.parse(raw)); } catch {}
        }
      }
      if (buf.length > 2_000_000) buf = '';
    });
    res.on('end', () => setTimeout(connect, 2000));
    res.on('error', () => setTimeout(connect, 2000));
  });
  req.on('error', (e) => { log('sse error', e.message); setTimeout(connect, 3000); });
  req.setTimeout(0);
}

function getState() {
  return new Promise((resolve) => {
    const req = http.get({
      host: '127.0.0.1', port: PORT, path: '/api/state',
      headers: { 'X-CC-Token': TOKEN },
    }, (res) => {
      let s = '';
      res.setEncoding('utf8');
      res.on('data', (d) => { s += d; });
      res.on('end', () => { try { resolve(JSON.parse(s)); } catch { resolve(null); } });
    });
    req.on('error', () => resolve(null));
    req.setTimeout(4000, () => { req.destroy(); resolve(null); });
  });
}

async function poll() {
  const st = await getState();
  if (!st) return;
  const runs = st.runs || (st.run ? [st.run] : []);
  if (runs.length) {
    if (!active) start(st.run || runs[0], '正在处理…');
    else if (!active.id) active.id = (st.run || runs[0]).id || '';
  } else if (active) {
    verifyEnd('');
  }
}

function shutdown() {
  try {
    execFileSync('am', ['start-foreground-service', '--user', '0', '-n', SVC,
      '-a', A.idle], { timeout: 3000 });
  } catch {}
  process.exit(0);
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
process.on('SIGHUP', shutdown);

log('work island driver starting, port', PORT);
connect();
setInterval(poll, 5000);
poll();
