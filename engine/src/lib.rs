//! Ceet Engine: inti privasi dan pencarian lokal yang dipakai semua platform.
//! Tanpa dependensi, tanpa jaringan, tanpa pelatihan model.

pub mod json;
pub mod omnibox;
pub mod phishing;
pub mod privacy;
pub mod protocol;
pub mod search;
pub mod summarize;
pub mod text;
pub mod url;

pub const VERSION: &str = env!("CARGO_PKG_VERSION");
