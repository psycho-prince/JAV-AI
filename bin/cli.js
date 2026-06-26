#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');

const pomPath = path.join(__dirname, '..', 'pom.xml');
const args = ['exec:java', '-f', pomPath];

const isWin = process.platform === 'win32';
const cmd = isWin ? 'mvn.cmd' : 'mvn';

const child = spawn(cmd, args, {
    stdio: 'inherit'
});

child.on('close', (code) => {
    process.exit(code);
});
