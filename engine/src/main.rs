//! Biner `ceet-engine`: membaca satu perintah JSON per baris dari stdin, menulis satu jawaban per baris ke stdout.

use ceet_engine::protocol::Engine;
use std::io::{self, BufRead, Write};

fn main() {
    let stdin = io::stdin();
    let stdout = io::stdout();
    let mut out = stdout.lock();
    let mut engine = Engine::new();
    for line in stdin.lock().lines() {
        let Ok(line) = line else { break };
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let reply = engine.handle_line(line);
        if writeln!(out, "{}", reply).is_err() || out.flush().is_err() {
            break;
        }
    }
}
