#!/usr/bin/env node
import https from 'node:https';
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const env = (k, d) => process.env[k] ?? d;
const BASE = new URL(env('BASE_URL', 'https://api.pulsoescolar.com.br'));
const ACCOUNTS_FILE = env('ACCOUNTS_FILE', 'accounts.csv'); // role,email,password
const STAGES = env('STAGES', '50:120,100:120,250:180,500:180,1000:300') // "usuarios:segundos,..."
  .split(',').map(s => { const [vus, sec] = s.split(':').map(Number); return { vus, sec }; });
const THINK_MIN = Number(env('THINK_MIN_MS', 1000));
const THINK_MAX = Number(env('THINK_MAX_MS', 3000));
const TEACHER_RATIO = Number(env('TEACHER_VU_RATIO', 0.05));
const LOGIN_PER_MIN = Number(env('LOGIN_PER_MIN', 8)); 
const TOKEN_TTL_MIN = Number(env('TOKEN_TTL_MIN', 50));
const TIMEOUT_MS = Number(env('REQUEST_TIMEOUT_MS', 30000));
const ABORT_ERR = Number(env('ABORT_ERROR_RATE', 0.20));
const ABORT_P95 = Number(env('ABORT_P95_MS', 10000));
const ABORT_ENABLED = !process.argv.includes('--no-abort');
const DRY = process.argv.includes('--dry-run');

if (!process.argv.includes('--confirm-production') && !DRY) {
  console.error(`Destino: ${BASE.origin}\nEste script gera carga e grava respostas (dados fictícios).\nRepita com --confirm-production para executar, ou --dry-run para validar a configuração.`);
  process.exit(2);
}

const agent = new (BASE.protocol === 'https:' ? https : http).Agent({ keepAlive: true, maxSockets: 1500 });
const lib = BASE.protocol === 'https:' ? https : http;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const rnd = (a, b) => a + Math.random() * (b - a);

function request(method, p, { token, body } = {}) {
  return new Promise(resolve => {
    const t0 = performance.now();
    const data = body ? JSON.stringify(body) : null;
    const req = lib.request({ agent, hostname: BASE.hostname, port: BASE.port || undefined, path: p, method, timeout: TIMEOUT_MS,
      headers: { Accept: 'application/json', ...(data && { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }),
        ...(token && { Authorization: `Bearer ${token}` }) } }, res => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { /* corpo não JSON */ }
        resolve({ status: res.statusCode, ms: performance.now() - t0, json, retryAfter: Number(res.headers['retry-after']) || 0,
          origin: res.statusCode === 429 ? `server=${res.headers.server} content-type=${res.headers['content-type']} retry-after=${res.headers['retry-after']} body=${Buffer.concat(chunks).toString('utf8').slice(0, 120)}` : undefined });
      });
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', e => resolve({ status: 0, ms: performance.now() - t0, json: null, error: e.message }));
    if (data) req.write(data);
    req.end();
  });
}

// ---------- contas ----------
const accounts = fs.existsSync(ACCOUNTS_FILE)
  ? fs.readFileSync(ACCOUNTS_FILE, 'utf8').split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith('#') && !l.startsWith('role,'))
      .map(l => { const [role, email, password] = l.split(','); return { role: role.toUpperCase(), email, password }; })
  : [];
const students = accounts.filter(a => a.role === 'STUDENT');
const teachers = accounts.filter(a => a.role === 'TEACHER');
const maxVus = Math.max(...STAGES.map(s => s.vus));
const maxLoggable = Math.floor(TOKEN_TTL_MIN * LOGIN_PER_MIN * 0.9); // contas com token válido durante todo o teste
console.log(`Destino ${BASE.origin} | contas: ${students.length} alunos, ${teachers.length} professores | estágios: ${STAGES.map(s => `${s.vus}u/${s.sec}s`).join(' → ')}`);
const totalSec = STAGES.reduce((a, s) => a + s.sec, 0);
console.log(`Duração da carga: ${totalSec}s. Login pacing: ${LOGIN_PER_MIN}/min → ~${Math.ceil(accounts.length / LOGIN_PER_MIN)} min para ${accounts.length} contas; tokens duram ${TOKEN_TTL_MIN} min (máx. ~${maxLoggable} contas com token válido durante todo o teste).`);
if (DRY) process.exit(0);
if (!students.length) { console.error('Nenhum aluno em ' + ACCOUNTS_FILE); process.exit(1); }

const meta = { startedAt: new Date().toISOString(), base: BASE.origin, node: process.version, host: `${os.type()} ${os.release()} ${os.cpus().length} CPU ${Math.round(os.totalmem() / 2 ** 30)} GB`,
  stages: STAGES, think: [THINK_MIN, THINK_MAX], loginPerMin: LOGIN_PER_MIN, accountsFile: accounts.length };

// ---------- login paceado ----------
async function login(acc) {
  for (;;) {
    const r = await request('POST', '/api/auth/login', { body: { email: acc.email, password: acc.password } });
    if (r.status === 429) { await sleep((r.retryAfter || 10) * 1000 + 500); continue; }
    if (r.status === 200 && r.json?.accessToken && !r.json.twoFactorRequired) {
      const token = r.json.accessToken;
      if (!r.json.user.termsAccepted) { // primeiro acesso das contas fictícias: aceite dos termos (autorizado pelo responsável)
        const v = (await request('GET', acc.role === 'STUDENT' ? '/api/student-term' : '/api/terms', { token })).json?.version;
        const pv = (await request('GET', '/api/privacy-policy', { token })).json?.version;
        const a = await request('POST', '/api/terms/accept', { token, body: { version: v, termsAccepted: true, privacyAccepted: true, privacyVersion: pv } });
        if (a.status >= 300) return { ...acc, error: `aceite de termos HTTP ${a.status}` };
      }
      return { ...acc, token, expiresAt: Date.parse(r.json.expiresAt) };
    }
    return { ...acc, error: r.status === 200 ? '2FA obrigatório' : `HTTP ${r.status}` };
  }
}
const needStudents = Math.min(students.length, Math.max(maxVus, 1), maxLoggable);
const needTeachers = Math.min(teachers.length, Math.max(1, Math.ceil(maxVus * TEACHER_RATIO)), Math.max(0, maxLoggable - needStudents));
const toLogin = [...teachers.slice(0, needTeachers), ...students.slice(0, needStudents)];
const sessions = [], loginFailures = [];
console.log(`Autenticando ${toLogin.length} contas (pode levar ~${Math.ceil(toLogin.length / LOGIN_PER_MIN)} min)...`);
const loginStart = Date.now();
for (const acc of toLogin) {
  const s = await login(acc);
  (s.token ? sessions : loginFailures).push(s);
  await sleep(60000 / LOGIN_PER_MIN);
  if (sessions.length % 20 === 0) process.stdout.write(`\r  ${sessions.length} ok, ${loginFailures.length} falhas`);
}
console.log(`\nLogin: ${sessions.length} ok, ${loginFailures.length} falhas em ${Math.round((Date.now() - loginStart) / 1000)}s`);
meta.login = { attempted: toLogin.length, ok: sessions.length, failed: loginFailures.length, failureReasons: [...new Set(loginFailures.map(f => f.error))] };
const sStudents = sessions.filter(s => s.role === 'STUDENT'), sTeachers = sessions.filter(s => s.role === 'TEACHER');
if (!sStudents.length) { console.error('Nenhum aluno autenticado.'); process.exit(1); }
const testStart = Date.now();
const tokenDeadline = Math.min(...sessions.map(s => s.expiresAt || Infinity));

// ---------- coleta ----------
const samples429 = new Map();
const records = []; // {t, stage, scenario, status, ms, cls}
let stageIdx = 0, aborted = null;
function record(scenario, r, expectReject = false) {
  const cls = r.status === 0 ? 'failure' : r.status === 429 ? 'rate_limited' : r.status < 300 ? 'ok'
    : expectReject && r.status >= 400 && r.status < 500 ? 'expected_rejection' : 'failure';
  if (r.origin) samples429.set(r.origin, (samples429.get(r.origin) || 0) + 1);
  records.push({ t: Date.now() - testStart, stage: stageIdx, scenario, status: r.status, ms: r.ms, cls, err: r.error });
  return cls;
}

// ---------- usuários virtuais ----------
async function studentVu(id, stop) {
  const s = sStudents[id % sStudents.length];
  const st = { pending: [], done: [] };
  async function refresh() {
    const r = await request('GET', '/api/student/activities', { token: s.token });
    record('student_activities', r);
    if (Array.isArray(r.json)) { st.pending = r.json.filter(a => !a.submission).map(a => a.activity.id); st.done = r.json.filter(a => a.submission).map(a => a.activity.id); }
  }
  await refresh();
  while (!stop.v) {
    await sleep(rnd(THINK_MIN, THINK_MAX));
    const x = Math.random();
    if (x < 0.45 || (!st.pending.length && !st.done.length)) await refresh();
    else if (x < 0.70) {
      const id2 = [...st.pending, ...st.done][Math.floor(Math.random() * (st.pending.length + st.done.length))];
      record('quiz_open', await request('GET', `/api/activities/${id2}/quiz`, { token: s.token }));
    } else if (x < 0.90 && st.pending.length) {
      const act = st.pending[0];
      const r = await request('POST', `/api/activities/${act}/submissions`, { token: s.token, body: { answers: Array(10).fill(0) } });
      // Outro VU da mesma conta pode ter enviado antes: a regra de entrega única responde com 4xx esperado.
      const c = record('submit_first', r, true);
      if (c === 'ok' || c === 'expected_rejection') { st.pending.shift(); st.done.push(act); }
    } else if (st.done.length) {
      const act = st.done[Math.floor(Math.random() * st.done.length)];
      record('submit_duplicate', await request('POST', `/api/activities/${act}/submissions`, { token: s.token, body: { answers: Array(10).fill(0) } }), true);
    } else await refresh();
  }
}
async function teacherVu(id, stop) {
  const s = sTeachers[id % sTeachers.length];
  while (!stop.v) {
    await sleep(rnd(THINK_MIN, THINK_MAX));
    if (Math.random() < 0.7) record('teacher_report', await request('GET', '/api/teacher/reports', { token: s.token }));
    else record('teacher_activities', await request('GET', '/api/teacher/activities', { token: s.token }));
  }
}

// ---------- execução progressiva ----------
const stop = { v: false };
const workers = [];
const stageBounds = [];
const watchdog = setInterval(() => {
  if (!ABORT_ENABLED || aborted) return;
  const now = Date.now() - testStart, win = records.filter(r => r.t > now - 10000);
  if (win.length < 30) return;
  const err = win.filter(r => r.cls === 'failure' && (r.status === 0 || r.status >= 500)).length / win.length;
  const lat = win.filter(r => r.status >= 200 && r.status < 300).map(r => r.ms).sort((a, b) => a - b);
  const p95 = lat.length ? lat[Math.floor(lat.length * 0.95)] : 0;
  if (err > ABORT_ERR || p95 > ABORT_P95) { aborted = `Interrompido por segurança: erros 5xx/rede ${(err * 100).toFixed(1)}% e p95 ${Math.round(p95)} ms na janela de 10 s (estágio ${stageIdx + 1}).`; console.error('\n' + aborted); stop.v = true; }
}, 2000);

let spawned = 0;
for (let i = 0; i < STAGES.length && !stop.v; i++) {
  stageIdx = i;
  const { vus, sec } = STAGES[i];
  const t0 = Date.now() - testStart;
  const rampMs = Math.min(sec * 200, 20000); // sobe os usuários novos em até 20% do estágio
  const add = Math.max(0, vus - spawned), delay = add ? rampMs / add : 0;
  for (let k = 0; k < add && !stop.v; k++) {
    const id = spawned++;
    workers.push(Math.random() < TEACHER_RATIO && sTeachers.length ? teacherVu(id, stop) : studentVu(id, stop));
    if (delay) await sleep(delay);
  }
  const remaining = t0 + sec * 1000 - (Date.now() - testStart);
  if (Date.now() + remaining > tokenDeadline - 30000) { aborted ??= 'Token JWT próximo de expirar; teste encerrado.'; stop.v = true; }
  if (remaining > 0 && !stop.v) for (let w = 0; w < remaining && !stop.v; w += 500) await sleep(Math.min(500, remaining - w));
  stageBounds.push({ vus: spawned, targetVus: vus, startMs: t0, endMs: Date.now() - testStart });
  process.stdout.write(`\rEstágio ${i + 1}/${STAGES.length} concluído (${spawned} VUs, ${records.length} requisições)   `);
}
stop.v = true; clearInterval(watchdog);
await Promise.race([Promise.all(workers), sleep(TIMEOUT_MS + 5000)]);
console.log('\nCarga encerrada; gerando relatório...');

// ---------- relatório ----------
const pct = (arr, p) => arr.length ? arr[Math.min(arr.length - 1, Math.floor(arr.length * p))] : null;
const f = v => v == null ? '—' : v >= 100 ? Math.round(v).toString() : v.toFixed(1);
function summarize(rows, secs) {
  const ok = rows.filter(r => r.cls === 'ok').map(r => r.ms).sort((a, b) => a - b);
  const all = rows.filter(r => r.status > 0).map(r => r.ms).sort((a, b) => a - b);
  const n = rows.length, c = k => rows.filter(r => r.cls === k).length;
  return { n, rps: secs ? n / secs : 0, p50ok: pct(ok, 0.5), p95ok: pct(ok, 0.95), p50all: pct(all, 0.5), p95all: pct(all, 0.95),
    ok: c('ok'), rate_limited: c('rate_limited'), expected_rejection: c('expected_rejection'), failure: c('failure'),
    pctOk: n ? 100 * c('ok') / n : 0, pct429: n ? 100 * c('rate_limited') / n : 0, pctExp: n ? 100 * c('expected_rejection') / n : 0, pctFail: n ? 100 * c('failure') / n : 0 };
}
const stageRows = stageBounds.map((b, i) => ({ b, s: summarize(records.filter(r => r.stage === i), (b.endMs - b.startMs) / 1000) }));
const scenarios = [...new Set(records.map(r => r.scenario))];
const codes = {}; records.forEach(r => { const k = `${r.status || 'rede/timeout'}`; codes[k] = (codes[k] || 0) + 1; });
const net = {}; records.filter(r => r.err).forEach(r => { net[r.err] = (net[r.err] || 0) + 1; });
const totalS = summarize(records, (Date.now() - testStart) / 1000);

const cols = ['Requisições', 'req/s', 'Mediana 2xx (ms)', 'P95 2xx (ms)', '2xx', '429', 'Rejeição esperada (4xx)', 'Falhas (5xx/rede/outros 4xx)'];
const cells = s => [s.n, f(s.rps), f(s.p50ok), f(s.p95ok), s.pctOk.toFixed(1) + '%', s.pct429.toFixed(1) + '%', s.pctExp.toFixed(1) + '%', s.pctFail.toFixed(1) + '%'];
const table = (lead, rows) => { const h = [...lead, ...cols]; return `| ${h.join(' | ')} |\n|${h.map(() => '---').join('|')}|\n` + rows.map(r => `| ${r.join(' | ')} |`).join('\n') + '\n'; };
const secsAll = (Date.now() - testStart) / 1000;
let md = `# Relatório do teste de carga\n\n## Ambiente\n- Destino: ${meta.base}\n- Início: ${meta.startedAt}\n- Gerador de carga: ${meta.host}, Node ${meta.node}\n- Contas: ${sStudents.length} alunos e ${sTeachers.length} professores autenticados (de ${accounts.length} no arquivo); falhas de login: ${loginFailures.length} ${meta.login.failureReasons.length ? '(' + meta.login.failureReasons.join('; ') + ')' : ''}\n- Think time: ${THINK_MIN}–${THINK_MAX} ms; razão de professores: ${TEACHER_RATIO}\n- Métricas do servidor (CPU, memória, conexões do banco): **não coletadas por este script** (sem acesso); preencher manualmente a partir do painel da hospedagem, se disponível.\n${aborted ? `- **${aborted}**\n` : ''}\n## Resultado por estágio\n\n`;
md += table(['Estágio', 'Usuários simultâneos', 'Duração (s)'], stageRows.map(({ b, s }, i) => [i + 1, `${b.vus} (alvo ${b.targetVus})`, f((b.endMs - b.startMs) / 1000), ...cells(s)]));
md += `\n## Total\n\n` + table([], [cells(totalS)]);
md += `\n## Por cenário (todos os estágios)\n\n` + table(['Cenário'], scenarios.map(sc => [sc, ...cells(summarize(records.filter(r => r.scenario === sc), secsAll))]));
md += `\n## Códigos HTTP\n${Object.entries(codes).sort().map(([k, v]) => `- ${k}: ${v}`).join('\n')}\n${Object.keys(net).length ? '\nErros de rede: ' + JSON.stringify(net) + '\n' : ''}`;
md += `\n## Origem dos 429 (cabeçalhos e corpo da resposta)\n${[...samples429].map(([k, v]) => `- ${v}× ${k}`).join('\n') || '- nenhum 429'}\n`;
md += `\n> Mediana e P95 calculados apenas sobre respostas 2xx. 429 (limite de requisições) e rejeições esperadas da regra de entrega única (\`submit_first\`/\`submit_duplicate\` com 4xx) são separados das falhas.\n`;

const outDir = path.join('results', new Date().toISOString().replace(/[:.]/g, '-'));
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'report.md'), md);
fs.writeFileSync(path.join(outDir, 'results.json'), JSON.stringify({ meta, aborted, stages: stageRows, total: totalS, codes, net }, null, 1));
fs.writeFileSync(path.join(outDir, 'requests.csv'), 't_ms,stage,scenario,status,ms,class\n' + records.map(r => `${r.t},${r.stage + 1},${r.scenario},${r.status},${r.ms.toFixed(1)},${r.cls}`).join('\n'));
console.log(md + `\nArquivos em ${outDir}`);
