/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {verifyUi, runBrowser, terminateBrowserProcess} from './verify-ui.mjs';
import {REQUIRED_CHECKS} from './run_ui_deployment_regressions.js';

const passed = () => ({status: 'passed', observedAt: new Date().toISOString(),
  browserVersion: 'Chromium test fixture', timezone: 'Asia/Taipei', scope: 'Mocked API responses',
  checks: REQUIRED_CHECKS.map(name => ({name, status: 'passed'})),
  unexpectedApi: [], pageErrors: [], failure: null});

async function fixture(t, overrides = {}) {
  const outputDir = await mkdtemp(path.join(os.tmpdir(), 'hubble-verify-ui-'));
  t.after(() => rm(outputDir, {recursive: true, force: true}));
  const state = {closed: 0, served: 0, ran: 0};
  const browserPath = path.join(outputDir, 'browser-report.json');
  const dependencies = {
    build: async () => ({inputHash: 'current-input-hash'}),
    serve: async () => {
      state.served++;
      return {url: 'http://127.0.0.1:43127', close: async () => {state.closed++;}};
    },
    run: async () => {state.ran++; await writeFile(browserPath, JSON.stringify(passed()));},
    unchanged: async () => true,
    ...overrides,
  };
  return {outputDir, browserPath, state, dependencies,
    run: () => verifyUi({frontend: outputDir, outputDir}, dependencies),
    saved: async () => JSON.parse(await readFile(path.join(outputDir, 'verification.json'), 'utf8'))};
}

test('passing fixture evidence retains its layer, build hash and named checks and closes preview', async t => {
  const f = await fixture(t);
  const report = await f.run();
  assert.equal(report.status, 'passed');
  assert.equal(report.layer, 'controlled-browser');
  assert.equal(report.inputHash, 'current-input-hash');
  assert.match(report.coverage, /not live/);
  assert.deepEqual(report.checks.map(check => check.name), REQUIRED_CHECKS);
  assert.equal(f.state.closed, 1);
  assert.deepEqual(await f.saved(), report);
});

test('build failure replaces stale success before any server or browser is started', async t => {
  const f = await fixture(t, {build: async () => {throw new Error('build failed');}});
  await writeFile(f.browserPath, JSON.stringify(passed()));
  await writeFile(path.join(f.outputDir, 'verification.json'), '{"status":"passed"}');
  const report = await f.run();
  assert.equal(report.status, 'failed');
  assert.equal(report.failure, 'build failed');
  assert.equal(f.state.served, 0);
  assert.equal(f.state.ran, 0);
  await assert.rejects(readFile(f.browserPath), {code: 'ENOENT'});
  assert.equal((await f.saved()).status, 'failed');
});

test('a zero-exit runner without a fresh report cannot reuse old evidence', async t => {
  const f = await fixture(t, {run: async () => {}});
  await writeFile(f.browserPath, JSON.stringify(passed()));
  assert.equal((await f.run()).status, 'failed');
  assert.equal(f.state.closed, 1);
});

test('a zero-exit runner with failed assertions still fails the gate', async t => {
  const f = await fixture(t);
  f.dependencies.run = async () => {
    const report = passed();
    report.checks[0].status = 'failed';
    await writeFile(f.browserPath, JSON.stringify(report));
  };
  assert.equal((await f.run()).status, 'failed');
  assert.equal(f.state.closed, 1);
});

test('changed inputs or tampered outputs invalidate otherwise passing browser evidence', async t => {
  const f = await fixture(t, {unchanged: async () => false});
  const report = await f.run();
  assert.equal(report.status, 'failed');
  assert.match(report.failure, /changed during verification/);
  assert.equal(f.state.closed, 1);
});

test('editing a runner during verification cannot certify its newer source', async t => {
  let reads = 0;
  const f = await fixture(t, {runnerHash: async () => ++reads === 1 ? 'before' : 'after'});
  const report = await f.run();
  assert.equal(report.status, 'failed');
  assert.match(report.failure, /Browser runner changed/);
  assert.equal(f.state.closed, 1);
});

test('interruption during final validation or cleanup cannot leave a passed report', async t => {
  for (const stage of ['validation', 'cleanup']) {
    const f = await fixture(t);
    if (stage === 'validation') f.dependencies.unchanged = async () => {
      process.emit('SIGTERM');
      return true;
    };
    else f.dependencies.serve = async () => ({url: 'http://127.0.0.1:43127',
      close: async () => {process.emit('SIGTERM');}});
    const report = await f.run();
    assert.equal(report.status, 'failed', stage);
    assert.equal(report.failure, 'Verification interrupted');
    assert.equal((await f.saved()).status, 'failed');
  }
});

test('browser failure and preview cleanup failure both remain failures', async t => {
  const f = await fixture(t, {run: async () => {throw new Error('browser crashed');}});
  assert.equal((await f.run()).failure, 'browser crashed');
  assert.equal(f.state.closed, 1);
  f.dependencies.run = async () => writeFile(f.browserPath, JSON.stringify(passed()));
  f.dependencies.serve = async () => ({url: 'http://127.0.0.1:43127',
    close: async () => {throw new Error('close failed');}});
  const report = await f.run();
  assert.equal(report.status, 'failed');
  assert.match(report.failure, /Preview cleanup failed/);
});

test('an already interrupted run never starts a browser process', async () => {
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(runBrowser('http://127.0.0.1:43127', '/unused', controller.signal), /interrupted/);
});

test('Windows cleanup terminates the complete wrapper process tree', () => {
  const calls = [];
  const child = {pid: 43127, kill: () => assert.fail('taskkill should own Windows tree cleanup')};
  terminateBrowserProcess(child, 'SIGTERM', {
    platform: 'win32',
    runTaskkill: (command, args, options) => {
      calls.push({command, args, options});
      return {status: 0};
    },
  });
  assert.deepEqual(calls, [{
    command: 'taskkill',
    args: ['/PID', '43127', '/T', '/F'],
    options: {stdio: 'ignore', windowsHide: true},
  }]);
});

test('POSIX cleanup keeps the detached process-group contract', () => {
  const calls = [];
  terminateBrowserProcess({pid: 91, kill: () => assert.fail('group kill should be used')}, 'SIGKILL', {
    platform: 'linux',
    killProcess: (pid, signal) => calls.push({pid, signal}),
  });
  assert.deepEqual(calls, [{pid: -91, signal: 'SIGKILL'}]);
});
