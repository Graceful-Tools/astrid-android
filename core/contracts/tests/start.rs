//! What the library the app loads refuses at start (AATD-90, carried over from core/astrid-android
//! when the app moved onto core's astrid-uniffi, AATD-99).

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use astrid_android_contracts::astrid_uniffi::{CoreClient, CredentialStore};

#[derive(Default)]
struct Memory(Mutex<HashMap<String, String>>);

impl CredentialStore for Memory {
    fn get(&self, key: String) -> Option<String> {
        self.0.lock().unwrap().get(&key).cloned()
    }
    fn set(&self, key: String, value: String) -> bool {
        self.0.lock().unwrap().insert(key, value);
        true
    }
    fn delete(&self, key: String) -> bool {
        self.0.lock().unwrap().remove(&key);
        true
    }
}

#[test]
fn aatd_90_a_core_without_a_server_is_refused() {
    let refused = CoreClient::start(
        r#"{"cachePath":":memory:","platform":"android-app"}"#.into(),
        Arc::new(Memory::default()),
        false,
    );
    let error = refused.err().expect("refused").to_string();
    assert!(error.contains("CONFIG_BASE_URL_REQUIRED"), "{error}");
}
