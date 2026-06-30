use crate::config::Config;
use sqlx::PgPool;

#[derive(Clone)]
pub struct AppState {
    pub db: PgPool,
    pub redis: redis::Client,
    pub config: Config,
    pub http: reqwest::Client,
}

impl AppState {
    pub fn new(db: PgPool, redis: redis::Client, config: Config) -> Self {
        Self {
            db,
            redis,
            config,
            http: reqwest::Client::new(),
        }
    }
}
