import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const base = __ENV.CCOJ_BASE_URL || 'http://127.0.0.1:18180';
const mode = __ENV.CCOJ_SCENARIO || 'query';
const problemId = Number(__ENV.CCOJ_PROBLEM_ID);
const rejected = new Counter('submission_429');
const admitted = new Counter('submission_admitted');
const queryRejected = new Counter('query_429');
const queryAdmitted = new Counter('query_admitted');
const pollFailures = new Counter('poll_failures');
const unexpected = new Rate('unexpected_result');
const elapsed = new Trend('submission_to_terminal_ms', true);
const admittedQueryLatency = new Trend('query_admitted_http_ms', true);
const admittedSubmitLatency = new Trend('submit_admitted_http_ms', true);
const pollLatency = new Trend('poll_http_ms', true);

const queryLoad = {
  executor: 'constant-vus',
  vus: Number(__ENV.CCOJ_QUERY_VUS || __ENV.CCOJ_VUS || 5),
  duration: __ENV.CCOJ_DURATION || '10s',
  exec: 'query',
};
const sustainedSubmit = {
  executor: 'constant-arrival-rate',
  rate: Number(__ENV.CCOJ_SUBMIT_RATE || 1),
  timeUnit: '1s',
  duration: __ENV.CCOJ_DURATION || '10s',
  preAllocatedVUs: 8,
  maxVUs: 32,
  gracefulStop: '90s',
  exec: 'submit',
};

const scenarios = {
  query: { query: queryLoad },
  submit: { submit: { executor: 'per-vu-iterations', vus: Number(__ENV.CCOJ_VUS || 8),
    iterations: 1, maxDuration: '90s', exec: 'submit' } },
  sustained: { submit: sustainedSubmit },
  mixed: { query: queryLoad, submit: sustainedSubmit },
};
if (!scenarios[mode]) throw new Error('CCOJ_SCENARIO must be query, submit, sustained or mixed.');

export const options = {
  scenarios: scenarios[mode],
  thresholds: {
    unexpected_result: ['rate==0'],
    checks: ['rate==1'],
    ...(mode !== 'query' ? { submission_admitted: ['count>0'] } : {}),
    ...(['sustained', 'mixed'].includes(mode) ? { dropped_iterations: ['count==0'] } : {}),
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function data(response) {
  try {
    const body = response.json();
    return body && body.code === 200 ? body.data : undefined;
  } catch {
    return undefined;
  }
}

export function setup() {
  if (mode === 'query') return {};
  if (!Number.isSafeInteger(problemId) || problemId <= 0 || !__ENV.CCOJ_ACCOUNT || !__ENV.CCOJ_PASSWORD) {
    throw new Error('Submit scenario needs CCOJ_PROBLEM_ID, CCOJ_ACCOUNT and CCOJ_PASSWORD.');
  }
  const response = http.post(base + '/user/login',
    JSON.stringify({ account: __ENV.CCOJ_ACCOUNT, password: __ENV.CCOJ_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  const session = response.cookies.CCOJ_SESSION?.[0]?.value;
  if (!data(response) || !session) throw new Error('Login did not return a session.');
  return { session };
}

export function query() {
  const response = http.get(base + '/problem?page=1&size=20', {
    responseCallback: http.expectedStatuses(200, 429),
  });
  const admitted = response.status === 200 && Array.isArray(data(response)?.records);
  const limited = response.status === 429 && response.json('code') === 429;
  if (admitted) {
    queryAdmitted.add(1);
    admittedQueryLatency.add(response.timings.duration);
  }
  if (limited) queryRejected.add(1);
  const valid = admitted || limited;
  check(response, { 'query response valid': () => valid });
  unexpected.add(!valid);
  sleep(0.25);
}

export function submit({ session }) {
  const code = 'import java.util.Scanner; class Main { public static void main(String[] args) {'
    + ' Scanner input = new Scanner(System.in); System.out.println(input.nextInt() + input.nextInt()); } }';
  const headers = { 'Content-Type': 'application/json', Cookie: 'CCOJ_SESSION=' + session };
  const started = Date.now();
  const response = http.post(base + '/submission/judge',
    JSON.stringify({ problemId, language: 'JAVA', sourceCode: code }),
    { headers, responseCallback: http.expectedStatuses(200, 429), tags: { name: 'submit' } });
  if (response.status === 429) {
    rejected.add(1);
    const valid = response.json('code') === 429;
    check(response, { 'valid rejection': () => valid });
    unexpected.add(!valid);
    return;
  }
  const id = data(response);
  if (response.status !== 200 || !id) {
    check(response, { 'admitted submission returned an id': () => false });
    unexpected.add(true);
    return;
  }
  admitted.add(1);
  admittedSubmitLatency.add(response.timings.duration);
  let passed = false;
  while (Date.now() - started < 75_000) {
    const poll = http.get(base + '/submission/' + id, { headers, tags: { name: 'poll' } });
    const details = data(poll);
    if (poll.status !== 200 || !details?.status) {
      pollFailures.add(1);
      check(poll, { 'poll response valid': () => false });
      unexpected.add(true);
      return;
    }
    pollLatency.add(poll.timings.duration);
    if (!['PENDING', 'RUNNING'].includes(details.status)) {
      passed = details.status === 'ACCEPTED';
      elapsed.add(Date.now() - started);
      break;
    }
    sleep(0.5);
  }
  check(id, { 'admitted submission reached ACCEPTED': () => passed });
  unexpected.add(!passed);
}
