#!/usr/bin/env node

const { spawn } = require('child_process');
const path = require('path');

const jarPath = path.join(__dirname, 'javai.jar');
const args = ['-jar', jarPath];

const cmd = 'java';

const child = spawn(cmd, args, {
    stdio: 'inherit'
});

child.on('error', (err) => {
    if (err.code === 'ENOENT') {
        console.error('\nError: "java" command not found.');
        console.error('Please ensure that Java Development Kit (JDK 17+) is installed and available in your PATH.');
        console.error('On Termux, you can install it using: pkg install openjdk-17');
    } else {
        console.error('\nError running JavAI:', err.message);
    }
    process.exit(1);
});

child.on('close', (code) => {
    process.exit(code);
});
