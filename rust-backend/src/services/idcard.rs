use crate::app_state::AppState;
use crate::error::{AppError, Result};
use crate::models::membership::MembershipWithPlan;
use crate::models::user::User;
use crate::services::membership::get_active_membership;
use chrono::{Datelike, Local};
use printpdf::path::{PaintMode, WindingOrder};
use printpdf::{BuiltinFont, Color, Mm, PdfDocument, Point, Polygon, Rgb};
use qrcode::QrCode;
use rust_decimal::Decimal;
use std::sync::Arc;
use uuid::Uuid;

// Page: 300×200 pt → mm (1 pt = 0.352778 mm); all layout in f32 (printpdf uses f32)
const W: f32 = 105.83;
const H: f32 = 70.56;
const MARGIN: f32 = 1.41;
const HEADER_H: f32 = 9.88;
const HEADER_Y: f32 = 59.27;
const FIELD_X: f32 = 3.53;
const FIELD_START_Y: f32 = 54.33;
const FIELD_GAP: f32 = 6.35;
const PHOTO_SIZE: f32 = 22.93;
const QR_SIZE: f32 = 22.93;
const RIGHT_X: f32 = 81.50;
const PHOTO_Y: f32 = 34.57;
const QR_Y: f32 = 10.23;

pub async fn generate(state: &Arc<AppState>, user_id: Uuid) -> Result<Vec<u8>> {
    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;

    let membership = get_active_membership(state, user_id)
        .await?
        .ok_or_else(|| {
            AppError::BadRequest(
                "No active membership found. Purchase a plan to download your ID card.".into(),
            )
        })?;

    let photo_bytes = if let Some(ref url) = user.photo_url {
        read_photo(&state.config.upload_dir, url).await
    } else {
        None
    };

    build_pdf(&user, &membership, photo_bytes)
        .map_err(|e| AppError::Internal(format!("PDF generation failed: {e}")))
}

async fn read_photo(upload_dir: &str, url: &str) -> Option<Vec<u8>> {
    let rel = url
        .trim_start_matches('/')
        .strip_prefix("uploads/")
        .unwrap_or(url.trim_start_matches('/'));
    let path = format!("{}/{}", upload_dir.trim_end_matches('/'), rel);
    tokio::fs::read(&path).await.ok()
}

fn build_pdf(
    user: &User,
    m: &MembershipWithPlan,
    _photo_bytes: Option<Vec<u8>>,
) -> std::result::Result<Vec<u8>, Box<dyn std::error::Error>> {
    let (doc, page1, layer1) = PdfDocument::new("ID Card", Mm(W), Mm(H), "Layer 1");
    let layer = doc.get_page(page1).get_layer(layer1);

    let font = doc.add_builtin_font(BuiltinFont::Helvetica)?;
    let font_bold = doc.add_builtin_font(BuiltinFont::HelveticaBold)?;

    // White background
    fill_color(&layer, 1.0, 1.0, 1.0);
    filled_rect(&layer, 0.0, 0.0, W, H);

    // Outer border (1.5 pt)
    stroke_color(&layer, 0.0, 0.0, 0.0);
    layer.set_outline_thickness(1.5);
    stroked_rect(&layer, MARGIN, MARGIN, W - 2.0 * MARGIN, H - 2.0 * MARGIN);

    // Black header block
    fill_color(&layer, 0.0, 0.0, 0.0);
    filled_rect(&layer, MARGIN, HEADER_Y, W - 2.0 * MARGIN, HEADER_H);

    // White header text centered
    let title = "TARGET ZONE LIBRARY";
    let title_pt: f32 = 8.5;
    // Helvetica-Bold average char width ≈ 0.58 × pt, converted to mm
    let title_w = title.len() as f32 * title_pt * 0.58 * 0.352778;
    let title_x = (W - title_w) / 2.0;
    let title_y = HEADER_Y + (HEADER_H - title_pt * 0.352778) / 2.0 + 0.5;
    fill_color(&layer, 1.0, 1.0, 1.0);
    layer.use_text(title, title_pt, Mm(title_x), Mm(title_y), &font_bold);

    // Field rows
    let name = user.name.as_str();
    let father = user.father_name.as_deref().unwrap_or("\u{2014}");
    let age = calc_age(user.date_of_birth);
    let shift = format_shift(m.shift.as_deref());
    let phone = user.mobile.as_deref().unwrap_or("\u{2014}");
    let paid = format_paid(m.plan_price.as_ref());

    let rows: &[(&str, &str)] = &[
        ("Name", name),
        ("Father's Name", father),
        ("Age", age.as_str()),
        ("Shift", shift.as_str()),
        ("Phone", phone),
        ("Paid", paid.as_str()),
    ];

    let label_pt: f32 = 4.5;
    for (i, (label, value)) in rows.iter().enumerate() {
        let y = FIELD_START_Y - i as f32 * FIELD_GAP;
        let lbl = format!("{}: ", label);
        // Helvetica average char width ≈ 0.50 × pt
        let lbl_w = lbl.len() as f32 * label_pt * 0.50 * 0.352778;
        fill_color(&layer, 0.267, 0.267, 0.267);
        layer.use_text(lbl.as_str(), label_pt, Mm(FIELD_X), Mm(y), &font);
        fill_color(&layer, 0.0, 0.0, 0.0);
        layer.use_text(*value, label_pt, Mm(FIELD_X + lbl_w), Mm(y), &font_bold);
    }

    // Photo placeholder (gray box + "No Photo")
    fill_color(&layer, 0.612, 0.639, 0.686);
    filled_rect(&layer, RIGHT_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE);
    fill_color(&layer, 1.0, 1.0, 1.0);
    let np = "No Photo";
    let np_pt: f32 = 4.0;
    let np_w = np.len() as f32 * np_pt * 0.50 * 0.352778;
    layer.use_text(
        np,
        np_pt,
        Mm(RIGHT_X + (PHOTO_SIZE - np_w) / 2.0),
        Mm(PHOTO_Y + PHOTO_SIZE / 2.0 - 1.5),
        &font,
    );
    stroke_color(&layer, 0.0, 0.0, 0.0);
    layer.set_outline_thickness(0.5);
    stroked_rect(&layer, RIGHT_X, PHOTO_Y, PHOTO_SIZE, PHOTO_SIZE);

    // QR code
    let short_id: String = m.id.to_string().chars().take(8).collect::<String>().to_uppercase();
    let qr_data = format!(
        "Name: {}\nFather's Name: {}\nPhone: {}\nShift: {}\nSeat: {}\nValid Till: {}\nMember ID: {}",
        name,
        father,
        phone,
        shift,
        m.seat_number.as_deref().unwrap_or("\u{2014}"),
        m.end_date,
        short_id,
    );

    fill_color(&layer, 1.0, 1.0, 1.0);
    filled_rect(&layer, RIGHT_X, QR_Y, QR_SIZE, QR_SIZE);

    if let Ok(code) = QrCode::new(qr_data.as_bytes()) {
        let qr_w = code.width();
        let module = QR_SIZE / qr_w as f32;
        fill_color(&layer, 0.0, 0.0, 0.0);
        for row in 0..qr_w {
            for col in 0..qr_w {
                if code[(row, col)].select(true, false) {
                    let x = RIGHT_X + col as f32 * module;
                    let y = QR_Y + (qr_w - row - 1) as f32 * module;
                    filled_rect(&layer, x, y, module, module);
                }
            }
        }
    }

    stroke_color(&layer, 0.0, 0.0, 0.0);
    layer.set_outline_thickness(0.5);
    stroked_rect(&layer, RIGHT_X, QR_Y, QR_SIZE, QR_SIZE);

    // Footer
    fill_color(&layer, 0.267, 0.267, 0.267);
    let footer = format!(
        "Issued: {}   |   This card is non-transferable.",
        Local::now().format("%Y-%m-%d")
    );
    layer.use_text(&footer, 3.5, Mm(FIELD_X), Mm(MARGIN + 1.76), &font);

    Ok(doc.save_to_bytes()?)
}

// ── Drawing helpers ───────────────────────────────────────────────────────────

fn fill_color(layer: &printpdf::PdfLayerReference, r: f32, g: f32, b: f32) {
    layer.set_fill_color(Color::Rgb(Rgb::new(r, g, b, None)));
}

fn stroke_color(layer: &printpdf::PdfLayerReference, r: f32, g: f32, b: f32) {
    layer.set_outline_color(Color::Rgb(Rgb::new(r, g, b, None)));
}

fn filled_rect(layer: &printpdf::PdfLayerReference, x: f32, y: f32, w: f32, h: f32) {
    layer.add_polygon(Polygon {
        rings: vec![vec![
            (Point::new(Mm(x), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y + h)), false),
            (Point::new(Mm(x), Mm(y + h)), false),
        ]],
        mode: PaintMode::Fill,
        winding_order: WindingOrder::NonZero,
    });
}

fn stroked_rect(layer: &printpdf::PdfLayerReference, x: f32, y: f32, w: f32, h: f32) {
    layer.add_polygon(Polygon {
        rings: vec![vec![
            (Point::new(Mm(x), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y)), false),
            (Point::new(Mm(x + w), Mm(y + h)), false),
            (Point::new(Mm(x), Mm(y + h)), false),
        ]],
        mode: PaintMode::Stroke,
        winding_order: WindingOrder::NonZero,
    });
}

// ── Field helpers ─────────────────────────────────────────────────────────────

fn calc_age(dob: Option<chrono::NaiveDate>) -> String {
    let d = match dob {
        Some(d) => d,
        None => return "\u{2014}".into(),
    };
    let today = Local::now().date_naive();
    let years = today.year() - d.year()
        - if (today.month(), today.day()) < (d.month(), d.day()) {
            1
        } else {
            0
        };
    format!("{} years", years)
}

fn format_shift(shift: Option<&str>) -> String {
    match shift {
        Some("MORNING") => "Morning Hours".into(),
        Some("EVENING") => "Evening Hours".into(),
        Some("FULL_DAY") => "Full Day Hours".into(),
        Some(s) => s.into(),
        None => "\u{2014}".into(),
    }
}

fn format_paid(price: Option<&Decimal>) -> String {
    match price {
        None => "\u{2014}".into(),
        Some(p) => format!("Rs. {}", p.normalize()),
    }
}
