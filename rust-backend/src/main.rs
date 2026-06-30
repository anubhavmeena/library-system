mod app_state;
mod config;
mod error;
mod handlers;
mod middleware;
mod models;
mod response;
mod routes;
mod services;

use app_state::AppState;
use config::Config;
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio_cron_scheduler::{Job, JobScheduler};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    dotenvy::dotenv().ok();

    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "library_backend=debug,tower_http=debug".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let config = Config::from_env();
    let port = config.port;

    tracing::info!("Connecting to database…");
    let db = sqlx::postgres::PgPoolOptions::new()
        .max_connections(10)
        .connect(&config.database_url)
        .await
        .expect("Failed to connect to PostgreSQL");

    tracing::info!("Connecting to Redis…");
    let redis = redis::Client::open(config.redis_url.as_str())
        .expect("Failed to create Redis client");

    // Ensure upload dir exists
    tokio::fs::create_dir_all(&config.upload_dir)
        .await
        .expect("Failed to create upload directory");

    let state = Arc::new(AppState::new(db, redis, config));

    // Start daily expiry reminder scheduler (9:00 AM)
    start_scheduler(state.clone()).await;

    let app = routes::build_router(state);
    let addr = format!("0.0.0.0:{port}");
    let listener = TcpListener::bind(&addr).await?;

    tracing::info!("Library backend listening on {addr}");
    axum::serve(listener, app).await?;

    Ok(())
}

async fn start_scheduler(state: Arc<AppState>) {
    let sched = JobScheduler::new()
        .await
        .expect("Failed to create job scheduler");

    let s1 = state.clone();
    let reminder_job = Job::new_async("0 0 9 * * *", move |_uuid, _lock| {
        let s = s1.clone();
        Box::pin(async move {
            services::admin::run_expiry_reminder_job(s).await;
        })
    })
    .expect("Failed to create reminder cron job");

    let s2 = state.clone();
    let expiry_job = Job::new_async("0 0 10 * * *", move |_uuid, _lock| {
        let s = s2.clone();
        Box::pin(async move {
            services::admin::run_mark_expired_job(s).await;
        })
    })
    .expect("Failed to create expiry cron job");

    sched.add(reminder_job).await.expect("Failed to add reminder job");
    sched.add(expiry_job).await.expect("Failed to add expiry job");
    sched.start().await.expect("Failed to start scheduler");
}
