#!/usr/bin/env node
// Prepara dados FICTÍCIOS da escola demo para o teste de carga: turma, matrículas, disciplinas e quizzes.
// Usa só a API pública, com as contas do DemoSeed (e-mails <escola>-<papel>-<n>@example.invalid).
// Uso: node setup.mjs --confirm-production   (SCHOOL=2 TEACHERS=5 STUDENTS=95 PASSWORD=...)
import fs from 'node:fs';
const B = process.env.BASE_URL ?? 'https://api.pulsoescolar.com.br';
const SCHOOL = Number(process.env.SCHOOL ?? 2), N_TEACH = Number(process.env.TEACHERS ?? 5), N_STUD = Number(process.env.STUDENTS ?? 95);
const PW = process.env.PASSWORD ?? 'Teste1234';
const QUIZZES_PER_SUBJECT = Number(process.env.QUIZZES_PER_SUBJECT ?? 2);
const CLOSES_AT = process.env.CLOSES_AT ?? '2026-12-30T23:00:00Z';
if (!process.argv.includes('--confirm-production')) { console.error(`Destino ${B}: cria turma, disciplinas e quizzes. Use --confirm-production.`); process.exit(2); }
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function j(m, p, t, b) {
  for (;;) {
    const r = await fetch(B + p, { method: m, headers: { 'content-type': 'application/json', ...(t && { authorization: 'Bearer ' + t }) }, body: b && JSON.stringify(b) });
    let x; try { x = await r.json(); } catch { /* sem corpo */ }
    if (r.status === 429) { await sleep((Number(r.headers.get('retry-after')) || 10) * 1000 + 500); continue; }
    return { s: r.status, x };
  }
}
const must = (r, what) => { if (r.s >= 300) { console.error(`FALHA ${what}: HTTP ${r.s} ${JSON.stringify(r.x)}`); process.exit(1); } return r.x; };
async function loginAndAccept(email) {
  const l = must(await j('POST', '/api/auth/login', null, { email, password: PW }), 'login ' + email);
  await sleep(7000); // limite de login: 10/min por IP
  if (l.twoFactorRequired) { console.error('2FA exigido: ' + email); process.exit(1); }
  const t = l.accessToken;
  if (!l.user.termsAccepted) {
    const role = l.user.role;
    const v = (await j('GET', role === 'STUDENT' ? '/api/student-term' : '/api/terms', t)).x.version;
    const pv = (await j('GET', '/api/privacy-policy', t)).x.version;
    must(await j('POST', '/api/terms/accept', t, { version: v, termsAccepted: true, privacyAccepted: true, privacyVersion: pv }), 'aceite ' + email);
  }
  return t;
}

// 1) Coordenador: turma, professores e alunos
const ct = await loginAndAccept(`${SCHOOL}-coordenador-1@example.invalid`);
let rooms = must(await j('GET', '/api/classrooms', ct), 'listar turmas');
rooms = rooms.content ?? rooms;
let room = rooms.find(r => r.name === '1º ano · Manhã' && r.identifier === 'A');
if (!room) room = must(await j('POST', '/api/classrooms', ct, { name: '1º ano · Manhã', identifier: 'A' }), 'criar turma');
console.log('Turma', room.id, room.name, room.identifier);
const teachers = must(await j('GET', '/api/teachers?size=100', ct), 'listar professores').content
  .filter(u => u.email.startsWith(`${SCHOOL}-professor-`)).sort((a, b) => a.email.localeCompare(b.email, 'en', { numeric: true })).slice(0, N_TEACH);
for (const t of teachers) must(await j('PUT', `/api/classrooms/${room.id}/teachers/${t.id}`, ct), 'vincular ' + t.email);
console.log('Professores vinculados:', teachers.length);
let studs = [];
for (let page = 0; ; page++) {
  const r = must(await j('GET', `/api/students?size=100&page=${page}`, ct), 'listar alunos');
  studs.push(...r.content.filter(u => u.email.startsWith(`${SCHOOL}-aluno-`)));
  if (page + 1 >= r.totalPages) break;
}
studs = studs.sort((a, b) => a.email.localeCompare(b.email, 'en', { numeric: true })).slice(0, N_STUD);
let enrolled = 0;
for (const s of studs) {
  if (s.classroomId === room.id) { enrolled++; continue; }
  const r = await j('PUT', `/api/classrooms/${room.id}/students/${s.id}`, ct);
  if (r.s >= 300) { console.error(`Matrícula ${s.email}: HTTP ${r.s} ${JSON.stringify(r.x)}`); break; }
  enrolled++;
}
console.log(`Alunos matriculados: ${enrolled}/${studs.length}`);

// 2) Professores: disciplina + quizzes
const SUBJECTS = ['Matemática', 'Língua Portuguesa', 'História', 'Física', 'Biologia', 'Geografia', 'Química', 'Inglês'];
let quizzes = 0;
for (const [i, t] of teachers.entries()) {
  const tt = await loginAndAccept(t.email);
  const name = SUBJECTS[i % SUBJECTS.length] + ' (carga)';
  const existing = must(await j('GET', `/api/classrooms/${room.id}/subjects`, tt), 'listar disciplinas');
  let subject = existing.find(s => s.name === name);
  const topics = [1, 2, 3].map(n => ({ title: `Tópico ${n}`, description: `Conteúdo fictício ${n} para teste de carga`, references: 'Material fictício' }));
  if (!subject) subject = must(await j('POST', `/api/classrooms/${room.id}/subjects`, tt, { name, syllabus: 'Ementa fictícia para teste de carga.', topics }), 'criar disciplina ' + name);
  const have = must(await j('GET', `/api/subjects/${subject.id}/activities`, tt), 'listar atividades');
  for (let q = have.length; q < QUIZZES_PER_SUBJECT; q++) {
    const questions = Array.from({ length: 10 }, (_, k) => ({
      question: `Questão ${k + 1} de ${name} (quiz ${q + 1})`,
      options: ['Alternativa A', 'Alternativa B', 'Alternativa C', 'Alternativa D'], theme: `Tópico ${(k % 3) + 1}`, correctOption: k % 4 }));
    must(await j('POST', `/api/subjects/${subject.id}/activities`, tt, { closesAt: CLOSES_AT, questions }), 'criar quiz');
    quizzes++;
  }
  console.log(`Professor ${t.email}: disciplina ${subject.id} (${name}), quizzes novos acumulados: ${quizzes}`);
}

// 3) Contas para o teste de carga
const rows = ['role,email,password', ...teachers.map(t => `TEACHER,${t.email},${PW}`), ...studs.map(s => `STUDENT,${s.email},${PW}`)];
fs.writeFileSync('accounts.csv', rows.join('\n') + '\n');
console.log(`accounts.csv gerado: ${teachers.length} professores, ${studs.length} alunos.`);
