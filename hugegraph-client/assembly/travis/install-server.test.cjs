/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync, spawnSync } = require('node:child_process');
const { mkdtempSync, mkdirSync, rmSync, writeFileSync, readFileSync, readdirSync, existsSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');

function temp(t) {
  const root = mkdtempSync(join(tmpdir(), 'server-install-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return root;
}

test('build-only installs candidate dependencies without starting a server', t => {
  const root = temp(t);
  const source = join(root, 'source');
  const git = (...args) => execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git('init', source);
  git('-C', source, 'config', 'user.name', 'Fixture');
  git('-C', source, 'config', 'user.email', 'fixture@example.invalid');
  writeFileSync(join(source, 'pom.xml'), 'fixture');
  git('-C', source, 'add', 'pom.xml');
  git('-C', source, 'commit', '-m', 'initial');
  const sha = git('-C', source, 'rev-parse', 'HEAD');
  const bin = join(root, 'bin');
  mkdirSync(bin);
  writeFileSync(join(bin, 'mvn'), '#!/bin/bash\nprintf "%s\\n" "$@" > "$BUILD_ARGS"\nprintf "%s" "$MAVEN_ARGS" > "$BUILD_REPO"\n', { mode: 0o755 });
  const env = { ...process.env, SERVER_REPOSITORY: 'example/server', SERVER_FETCH_REF: sha,
    PATH: `${bin}:${process.env.PATH}`, BUILD_ARGS: join(root, 'args'), BUILD_REPO: join(root, 'repo'),
    MAVEN_ARGS: `-Dmaven.repo.local=${root}/isolated-m2`, GIT_CONFIG_COUNT: '1',
    GIT_CONFIG_KEY_0: `url.file://${source}.insteadOf`, GIT_CONFIG_VALUE_0: 'https://github.com/example/server.git' };
  const run = args => spawnSync('bash', [join(__dirname, 'install-hugegraph-from-source.sh'), ...args],
    { cwd: root, env, encoding: 'utf8' });
  assert.notEqual(run([sha, '--unknown']).status, 0);
  assert.equal(existsSync(join(root, 'hugegraph')), false);
  const result = run([sha, '--build-only']);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(readFileSync(env.BUILD_ARGS, 'utf8').trim().split('\n'),
    ['install', '-DskipTests', '-Dmaven.javadoc.skip=true', '-ntp']);
  assert.equal(readFileSync(env.BUILD_REPO, 'utf8'), env.MAVEN_ARGS);
  // No archive exists: success proves build-only did not attempt extraction/startup.
  assert.equal(readdirSync(root).some(name => name.startsWith('hugegraph-servers.')), false);
});

test('shared starter configures and launches HTTP/auth and HTTPS with caller JVM', t => {
  const root = temp(t);
  const fixture = join(root, 'apache-hugegraph-fixture');
  mkdirSync(join(fixture, 'conf/graphs'), { recursive: true });
  mkdirSync(join(fixture, 'bin'));
  writeFileSync(join(fixture, 'conf/graphs/hugegraph.properties'),
    'gremlin.graph=org.apache.hugegraph.HugeFactory\n');
  writeFileSync(join(fixture, 'conf/rest-server.properties'),
    'restserver.url=http://127.0.0.1:8080\n#auth.authenticator=none\n#auth.admin_pa=none\n');
  writeFileSync(join(fixture, 'conf/gremlin-server.yaml'), '#port: 8182\n');
  writeFileSync(join(fixture, 'bin/init-store.sh'),
    '#!/bin/bash\nread -r password\n[[ "$password" == pa ]]\n', { mode: 0o755 });
  writeFileSync(join(fixture, 'bin/start-hugegraph.sh'),
    '#!/bin/bash\nprintf "%s" "$JAVA_HOME" > started-with-jvm\n', { mode: 0o755 });
  const archive = join(root, 'server.tar.gz');
  execFileSync('tar', ['czf', archive, '-C', root, 'apache-hugegraph-fixture']);
  const result = spawnSync('bash', [join(__dirname, 'start-hugegraph-servers.sh'), archive],
    { env: { ...process.env, RUNNER_TEMP: root, JAVA_HOME: '/fixture/java11' }, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  const serverRoot = join(root, readdirSync(root).find(name => name.startsWith('hugegraph-servers.')));
  for (const dir of ['apache-hugegraph-fixture', 'hugegraph_https']) {
    const deployed = join(serverRoot, dir);
    assert.equal(readFileSync(join(deployed, 'started-with-jvm'), 'utf8'), '/fixture/java11');
    assert.match(readFileSync(join(deployed, 'conf/graphs/hugegraph.properties'), 'utf8'), /HugeFactoryAuthProxy/);
    const rest = readFileSync(join(deployed, 'conf/rest-server.properties'), 'utf8');
    assert.match(rest, /auth.authenticator=org.apache.hugegraph.auth.StandardAuthenticator/);
    assert.match(rest, /auth.admin_pa=pa/);
    assert.match(rest, dir === 'hugegraph_https' ? /https:\/\/127.0.0.1:8443/ : /http:\/\/127.0.0.1:8080/);
  }
  assert.match(readFileSync(join(serverRoot, 'hugegraph_https/conf/gremlin-server.yaml'), 'utf8'), /^port: 8282/m);
});
