/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

'use strict';

async function payload(response, name) {
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw new Error(`${name} returned unreadable JSON: ${error.message}`);
  }
  if (!response.ok()) {
    throw new Error(`${name} failed: HTTP ${response.status()}`);
  }
  if (!body || body.status !== 200) {
    const detail = body && typeof body.message === 'string' && body.message.trim();
    throw new Error(`${name} failed: ${detail || `business status ${body?.status}`}`);
  }
  return body.data;
}

async function authenticateUi(context, page, hubbleUrl, username, password) {
  const baseUrl = hubbleUrl.replace(/\/$/, '');
  const user = await payload(await context.request.post(
    `${baseUrl}/api/v1.3/auth/login`,
    {data: {user_name: username, user_password: password}}
  ), 'login');
  const status = await payload(await context.request.get(
    `${baseUrl}/api/v1.3/auth/status`
  ), 'auth status');
  if (!status || !status.level) {
    throw new Error('auth status response did not include level');
  }
  if (!user || !user.user_name) {
    throw new Error('login response did not include user_name');
  }
  await page.addInitScript(serverUser => {
    window.sessionStorage.setItem('user_', JSON.stringify(serverUser));
  }, user);
  return {user, level: status.level};
}

module.exports = {authenticateUi};
