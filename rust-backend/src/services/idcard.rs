use crate::app_state::AppState;
use crate::error::{AppError, Result};
use crate::models::membership::MembershipWithPlan;
use crate::models::user::User;
use crate::services::membership::find_current_membership;
use crate::services::ids;
use barcoders::sym::code128::Code128;
use imageproc::drawing::{draw_text_mut, text_size};
use printpdf::image_crate::{imageops, DynamicImage, GenericImageView, ImageOutputFormat};
use printpdf::path::{PaintMode, WindingOrder};
use printpdf::{BuiltinFont, Color, Image, ImageTransform, Mm, PdfDocument, Point, Polygon, Pt, Rgb};
use rusttype::{Font, Scale};
use std::sync::Arc;
use uuid::Uuid;

// ── Layout — mirrors the Java backend's IdCardPdfGenerator exactly ────────────
// (common-lib/src/main/java/com/library/common/idcard/IdCardPdfGenerator.java).
// All constants are in PDF points (Java's native unit here), on a 1536×1024 px
// design canvas scaled down to a 342×228 pt (3:2) card via SCALE.

const PAGE_W: f32 = 342.0;
const PAGE_H: f32 = 228.0;
const SCALE: f32 = 342.0 / 1536.0;

const HEADER_W: f32 = 1536.0 * SCALE;
const HEADER_H: f32 = 243.0 * SCALE;
const HEADER_Y: f32 = PAGE_H - HEADER_H;

const TITLEBAR_W: f32 = 915.0 * SCALE;
const TITLEBAR_H: f32 = 106.0 * SCALE;
const TITLEBAR_Y: f32 = HEADER_Y - TITLEBAR_H;

const FOOTER_W: f32 = 1536.0 * SCALE;
const FOOTER_H: f32 = 97.0 * SCALE;

const PHOTO_X: f32 = 1035.0 * SCALE;
const PHOTO_W: f32 = 401.0 * SCALE;
const PHOTO_H: f32 = 447.0 * SCALE;
const PHOTO_Y: f32 = PAGE_H - 277.0 * SCALE - PHOTO_H;
const PHOTO_DPI: f32 = 300.0;
/// Not scaled by SCALE — Java's radius is a fixed 6pt regardless of card size.
const PHOTO_RADIUS: f32 = 6.0;

const SIG_X: f32 = PHOTO_X;
const SIG_W: f32 = 401.0 * SCALE;
const SIG_H: f32 = 150.0 * SCALE;
const SIG_Y: f32 = PAGE_H - 735.0 * SCALE - SIG_H;

const FIELDS_TOP_Y: f32 = TITLEBAR_Y;
const FIELD_X: f32 = 100.0 * SCALE;
const COLON_X: f32 = 370.0 * SCALE;
const VALUE_X: f32 = 425.0 * SCALE;
const FIELD_ROW0_Y: f32 = FIELDS_TOP_Y - 74.0 * SCALE;
const FIELD_GAP: f32 = 72.0 * SCALE;
const FIELD_FONT_PT: f32 = 8.0;

const BARCODE_X: f32 = FIELD_X;
const BARCODE_W: f32 = 180.0;
const BARCODE_H: f32 = 16.0;
const BARCODE_Y: f32 = 48.0;
const BARCODE_TEXT_Y: f32 = BARCODE_Y - 9.0;
const BARCODE_TEXT_PT: f32 = 7.0;

const NAVY: (f32, f32, f32) = (0x0d as f32 / 255.0, 0x1b as f32 / 255.0, 0x4b as f32 / 255.0);
const GRAY_LABEL: (f32, f32, f32) = (0x44 as f32 / 255.0, 0x44 as f32 / 255.0, 0x44 as f32 / 255.0);
const GRAY_BORDER: (f32, f32, f32) = (0xd1 as f32 / 255.0, 0xd5 as f32 / 255.0, 0xdb as f32 / 255.0);
const BLACK: (f32, f32, f32) = (0.0, 0.0, 0.0);
const WHITE: (f32, f32, f32) = (1.0, 1.0, 1.0);

const NAVY_U8: [u8; 3] = [0x0d, 0x1b, 0x4b];
const GRAY_LABEL_U8: [u8; 3] = [0x44, 0x44, 0x44];
const BLACK_U8: [u8; 3] = [0, 0, 0];
const WHITE_U8: [u8; 3] = [255, 255, 255];

const HEADER_PNG: &[u8] = include_bytes!("../../assets/idcard/header.png");
const TITLEBAR_PNG: &[u8] = include_bytes!("../../assets/idcard/titlebar.png");
const FOOTER_PNG: &[u8] = include_bytes!("../../assets/idcard/footer.png");
const PHOTO_PLACEHOLDER_PNG: &[u8] = include_bytes!("../../assets/idcard/photo-placeholder.png");
const SIGNATURE_PNG: &[u8] = include_bytes!("../../assets/idcard/signature.png");

const FONT_REGULAR: &[u8] = include_bytes!("../../assets/fonts/LiberationSans-Regular.ttf");
const FONT_BOLD: &[u8] = include_bytes!("../../assets/fonts/LiberationSans-Bold.ttf");

pub async fn generate(state: &Arc<AppState>, user_id: Uuid) -> Result<Vec<u8>> {
    let (user, membership, photo_bytes) = load_card_data(state, user_id).await?;
    build_pdf(&user, &membership, photo_bytes)
        .map_err(|e| AppError::Internal(format!("PDF generation failed: {e}")))
}

/// Renders the same ID card as a flat PNG image rather than a PDF — needed
/// because WhatsApp's "image" header template requires an actual image
/// content-type, not a PDF (unlike the "document" header used for receipts).
pub async fn generate_image(state: &Arc<AppState>, user_id: Uuid) -> Result<Vec<u8>> {
    let (user, membership, photo_bytes) = load_card_data(state, user_id).await?;
    build_image(&user, &membership, photo_bytes)
        .map_err(|e| AppError::Internal(format!("ID card image generation failed: {e}")))
}

async fn load_card_data(
    state: &Arc<AppState>,
    user_id: Uuid,
) -> Result<(User, MembershipWithPlan, Option<Vec<u8>>)> {
    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;

    // GRACE falls back correctly here too — a student mid-grace-period still
    // holds their seat and should still be able to download their ID card.
    let membership = find_current_membership(state, user_id)
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

    Ok((user, membership, photo_bytes))
}

async fn read_photo(upload_dir: &str, url: &str) -> Option<Vec<u8>> {
    let rel = url
        .trim_start_matches('/')
        .strip_prefix("uploads/")
        .unwrap_or(url.trim_start_matches('/'));
    let path = format!("{}/{}", upload_dir.trim_end_matches('/'), rel);
    tokio::fs::read(&path).await.ok()
}

/// Card field values shared by both the PDF and image renderers.
struct CardFields {
    name: String,
    mobile: String,
    id_number: String,
    plan_name: String,
    valid_upto: String,
}

fn card_fields(user: &User, m: &MembershipWithPlan) -> CardFields {
    CardFields {
        name: user.name.clone(),
        mobile: user.mobile.clone().unwrap_or_else(|| "\u{2014}".into()),
        id_number: ids::member_id(m.id),
        plan_name: m.plan_name.clone(),
        valid_upto: m.end_date.format("%d %b %Y").to_string(),
    }
}

/// Code128 (Code Set B) bar pattern for the ID number — a `Vec<u8>` of 0/1
/// module widths, one element per equal-width bar/space unit. Mirrors Java's
/// use of ZXing's `Code128Writer`/`BarcodeFormat.CODE_128`.
fn barcode_bars(id_number: &str) -> std::result::Result<Vec<u8>, Box<dyn std::error::Error>> {
    // 'Ɓ' (U+0181) is barcoders' START-B trigger character, selecting Code Set B
    // (all printable ASCII), matching our uppercase-hex ID numbers.
    let payload = format!("\u{0181}{id_number}");
    Ok(Code128::new(payload)?.encode())
}

fn barcode_text(id_number: &str) -> String {
    id_number.chars().map(|c| c.to_string()).collect::<Vec<_>>().join(" ")
}

// ── PDF rendering ───────────────────────────────────────────────────────────

fn pt(v: f32) -> Mm {
    Mm::from(Pt(v))
}

fn build_pdf(
    user: &User,
    m: &MembershipWithPlan,
    photo_bytes: Option<Vec<u8>>,
) -> std::result::Result<Vec<u8>, Box<dyn std::error::Error>> {
    let fields = card_fields(user, m);

    let (doc, page1, layer1) = PdfDocument::new("ID Card", pt(PAGE_W), pt(PAGE_H), "Layer 1");
    let layer = doc.get_page(page1).get_layer(layer1);

    let font = doc.add_builtin_font(BuiltinFont::Helvetica)?;
    let font_bold = doc.add_builtin_font(BuiltinFont::HelveticaBold)?;

    // White background
    fill_color(&layer, WHITE);
    filled_rect_pt(&layer, 0.0, 0.0, PAGE_W, PAGE_H);

    // Static artwork, in Java's z-order: header, titlebar, footer.
    place_image_pdf(&layer, HEADER_PNG, 0.0, HEADER_Y, HEADER_W, HEADER_H)?;
    place_image_pdf(&layer, TITLEBAR_PNG, 0.0, TITLEBAR_Y, TITLEBAR_W, TITLEBAR_H)?;
    place_image_pdf(&layer, FOOTER_PNG, 0.0, 0.0, FOOTER_W, FOOTER_H)?;

    // Field rows: label (navy bold) : (gray) value (black bold)
    let rows: &[(&str, &str)] = &[
        ("Name", &fields.name),
        ("Mobile No.", &fields.mobile),
        ("ID No.", &fields.id_number),
        ("Plan", &fields.plan_name),
        ("Valid Upto", &fields.valid_upto),
    ];
    for (i, (label, value)) in rows.iter().enumerate() {
        let y = FIELD_ROW0_Y - i as f32 * FIELD_GAP;
        fill_color(&layer, NAVY);
        layer.use_text(*label, FIELD_FONT_PT, pt(FIELD_X), pt(y), &font_bold);
        fill_color(&layer, GRAY_LABEL);
        layer.use_text(":", FIELD_FONT_PT, pt(COLON_X), pt(y), &font);
        fill_color(&layer, BLACK);
        layer.use_text(*value, FIELD_FONT_PT, pt(VALUE_X), pt(y), &font_bold);
    }

    // Barcode + human-readable ID text beneath it
    let bars = barcode_bars(&fields.id_number)?;
    let module_w = BARCODE_W / bars.len() as f32;
    fill_color(&layer, BLACK);
    for (i, bit) in bars.iter().enumerate() {
        if *bit == 1 {
            filled_rect_pt(&layer, BARCODE_X + i as f32 * module_w, BARCODE_Y, module_w, BARCODE_H);
        }
    }
    let barcode_text = barcode_text(&fields.id_number);
    let text_w = barcode_text.len() as f32 * BARCODE_TEXT_PT * 0.5;
    let text_x = BARCODE_X + (BARCODE_W - text_w) / 2.0;
    layer.use_text(&barcode_text, BARCODE_TEXT_PT, pt(text_x), pt(BARCODE_TEXT_Y), &font);

    // Photo — cover-scaled (fills the box, crops overflow) so portrait/
    // landscape uploads never letterbox; falls back to the placeholder
    // artwork if there's no photo or it fails to decode. Clipped to a
    // rounded rect (matching Java's 6pt-radius clip path) before drawing.
    layer.save_graphics_state();
    layer.add_polygon(Polygon {
        rings: vec![rounded_rect_path(PHOTO_X, PHOTO_Y, PHOTO_W, PHOTO_H, PHOTO_RADIUS)],
        mode: PaintMode::Clip,
        winding_order: WindingOrder::NonZero,
    });

    let target_px = (
        (PHOTO_W / 72.0 * PHOTO_DPI).round() as u32,
        (PHOTO_H / 72.0 * PHOTO_DPI).round() as u32,
    );
    if let Some(cropped) = photo_bytes.as_deref().and_then(|b| cover_crop(b, target_px.0, target_px.1)) {
        let image = Image::from_dynamic_image(&cropped);
        image.add_to_layer(
            layer.clone(),
            ImageTransform {
                translate_x: Some(pt(PHOTO_X)),
                translate_y: Some(pt(PHOTO_Y)),
                dpi: Some(PHOTO_DPI),
                ..Default::default()
            },
        );
    } else {
        place_image_pdf(&layer, PHOTO_PLACEHOLDER_PNG, PHOTO_X, PHOTO_Y, PHOTO_W, PHOTO_H)?;
    }
    layer.restore_graphics_state();

    stroke_color(&layer, NAVY);
    layer.set_outline_thickness(1.0);
    layer.add_polygon(Polygon {
        rings: vec![rounded_rect_path(PHOTO_X, PHOTO_Y, PHOTO_W, PHOTO_H, PHOTO_RADIUS)],
        mode: PaintMode::Stroke,
        winding_order: WindingOrder::NonZero,
    });

    // Signature artwork
    place_image_pdf(&layer, SIGNATURE_PNG, SIG_X, SIG_Y, SIG_W, SIG_H)?;

    // Outer card border
    stroke_color(&layer, GRAY_BORDER);
    layer.set_outline_thickness(0.75);
    stroked_rect_pt(&layer, 0.5, 0.5, PAGE_W - 1.0, PAGE_H - 1.0);

    Ok(doc.save_to_bytes()?)
}

/// Stretches a static PNG asset to an exact `(w_pt, h_pt)` box at `(x_pt, y_pt)`
/// — matches Java's `Image.scaleAbsolute(w, h)`. Setting dpi to 72 makes 1 source
/// pixel = 1 point, so scale_x/scale_y become simple target/native ratios,
/// independent of the asset's real resolution.
fn place_image_pdf(
    layer: &printpdf::PdfLayerReference,
    png_bytes: &[u8],
    x_pt: f32,
    y_pt: f32,
    w_pt: f32,
    h_pt: f32,
) -> std::result::Result<(), Box<dyn std::error::Error>> {
    let img = printpdf::image_crate::load_from_memory(png_bytes)?;
    let (px_w, px_h) = img.dimensions();
    let image = Image::from_dynamic_image(&img);
    image.add_to_layer(
        layer.clone(),
        ImageTransform {
            translate_x: Some(pt(x_pt)),
            translate_y: Some(pt(y_pt)),
            scale_x: Some(w_pt / px_w as f32),
            scale_y: Some(h_pt / px_h as f32),
            dpi: Some(72.0),
            ..Default::default()
        },
    );
    Ok(())
}

fn fill_color(layer: &printpdf::PdfLayerReference, (r, g, b): (f32, f32, f32)) {
    layer.set_fill_color(Color::Rgb(Rgb::new(r, g, b, None)));
}

fn stroke_color(layer: &printpdf::PdfLayerReference, (r, g, b): (f32, f32, f32)) {
    layer.set_outline_color(Color::Rgb(Rgb::new(r, g, b, None)));
}

fn filled_rect_pt(layer: &printpdf::PdfLayerReference, x: f32, y: f32, w: f32, h: f32) {
    layer.add_polygon(Polygon {
        rings: vec![vec![
            (Point::new(pt(x), pt(y)), false),
            (Point::new(pt(x + w), pt(y)), false),
            (Point::new(pt(x + w), pt(y + h)), false),
            (Point::new(pt(x), pt(y + h)), false),
        ]],
        mode: PaintMode::Fill,
        winding_order: WindingOrder::NonZero,
    });
}

fn stroked_rect_pt(layer: &printpdf::PdfLayerReference, x: f32, y: f32, w: f32, h: f32) {
    layer.add_polygon(Polygon {
        rings: vec![vec![
            (Point::new(pt(x), pt(y)), false),
            (Point::new(pt(x + w), pt(y)), false),
            (Point::new(pt(x + w), pt(y + h)), false),
            (Point::new(pt(x), pt(y + h)), false),
        ]],
        mode: PaintMode::Stroke,
        winding_order: WindingOrder::NonZero,
    });
}

/// Builds a closed rounded-rectangle path (4 corner arcs as cubic beziers,
/// connected by straight edges) usable as a fill/stroke/clip `Polygon` ring.
/// `kappa` is the standard constant for approximating a quarter-circle with
/// a single cubic bezier (radius * kappa = control-point offset).
fn rounded_rect_path(x: f32, y: f32, w: f32, h: f32, r: f32) -> Vec<(Point, bool)> {
    const KAPPA: f32 = 0.552_284_7;
    let k = r * KAPPA;
    let p = |px: f32, py: f32| Point::new(pt(px), pt(py));

    vec![
        (p(x + r, y), false),
        (p(x + w - r, y), true),
        (p(x + w - r + k, y), true),
        (p(x + w, y + r - k), false),
        (p(x + w, y + r), false),
        (p(x + w, y + h - r), true),
        (p(x + w, y + h - r + k), true),
        (p(x + w - r + k, y + h), false),
        (p(x + w - r, y + h), false),
        (p(x + r, y + h), true),
        (p(x + r - k, y + h), true),
        (p(x, y + h - r + k), false),
        (p(x, y + h - r), false),
        (p(x, y + r), true),
        (p(x, y + r - k), true),
        (p(x + r - k, y), false),
        (p(x + r, y), false),
    ]
}

// ── Image rendering (for WhatsApp's image-header template) ────────────────────
// Mirrors build_pdf's layout on a raster canvas — PDF coordinates are
// bottom-left origin (Y up), image coordinates are top-left origin (Y down),
// so every position is flipped via `flip_y_px`.

const IMG_DPI: f32 = 300.0;

fn px(v: f32) -> i32 {
    (v / 72.0 * IMG_DPI).round() as i32
}

fn px_u32(v: f32) -> u32 {
    (v / 72.0 * IMG_DPI).round().max(0.0) as u32
}

fn flip_y_px(y_pt: f32, h_pt: f32) -> i32 {
    px(PAGE_H - y_pt - h_pt)
}

type RgbImage = printpdf::image_crate::ImageBuffer<printpdf::image_crate::Rgb<u8>, Vec<u8>>;

fn place_image_raster(canvas: &mut RgbImage, png_bytes: &[u8], x_pt: f32, y_pt: f32, w_pt: f32, h_pt: f32) {
    let Ok(img) = printpdf::image_crate::load_from_memory(png_bytes) else { return };
    let resized = img.resize_exact(px_u32(w_pt).max(1), px_u32(h_pt).max(1), imageops::FilterType::Lanczos3);
    imageops::overlay(canvas, &resized.to_rgb8(), px(x_pt) as i64, flip_y_px(y_pt, h_pt) as i64);
}

/// True if pixel `(lx, ly)`, relative to a `w`×`h` box's top-left corner, falls
/// inside that box's rounded-rect region of corner radius `r` — used to mask
/// raster compositing since imageproc/image have no native clip-path support
/// (unlike the PDF path's true bezier clip via `rounded_rect_path`).
fn in_rounded_rect(lx: f32, ly: f32, w: f32, h: f32, r: f32) -> bool {
    let cx = lx.clamp(r, w - r);
    let cy = ly.clamp(r, h - r);
    let (dx, dy) = (lx - cx, ly - cy);
    dx * dx + dy * dy <= r * r
}

fn fill_rounded_rect_raster(canvas: &mut RgbImage, x0: i32, y0: i32, w: u32, h: u32, r: f32, color: [u8; 3]) {
    for yy in 0..h {
        for xx in 0..w {
            if in_rounded_rect(xx as f32 + 0.5, yy as f32 + 0.5, w as f32, h as f32, r) {
                let (dx, dy) = (x0 + xx as i32, y0 + yy as i32);
                if dx >= 0 && dy >= 0 && (dx as u32) < canvas.width() && (dy as u32) < canvas.height() {
                    canvas.put_pixel(dx as u32, dy as u32, printpdf::image_crate::Rgb(color));
                }
            }
        }
    }
}

/// Overlays `src` (resized to exactly `w`×`h`) at `(x0, y0)`, masked to a
/// rounded-rect of corner radius `r` so out-of-round corner pixels are left
/// untouched (revealing whatever's already on the canvas beneath, e.g. the
/// navy frame drawn by `fill_rounded_rect_raster` first).
fn overlay_rounded(canvas: &mut RgbImage, src: &DynamicImage, x0: i32, y0: i32, w: u32, h: u32, r: f32) {
    let resized = src.resize_exact(w.max(1), h.max(1), imageops::FilterType::Lanczos3).to_rgb8();
    for yy in 0..h {
        for xx in 0..w {
            if in_rounded_rect(xx as f32 + 0.5, yy as f32 + 0.5, w as f32, h as f32, r) {
                let (dx, dy) = (x0 + xx as i32, y0 + yy as i32);
                if dx >= 0 && dy >= 0 && (dx as u32) < canvas.width() && (dy as u32) < canvas.height() {
                    canvas.put_pixel(dx as u32, dy as u32, *resized.get_pixel(xx, yy));
                }
            }
        }
    }
}

fn build_image(
    user: &User,
    m: &MembershipWithPlan,
    photo_bytes: Option<Vec<u8>>,
) -> std::result::Result<Vec<u8>, Box<dyn std::error::Error>> {
    let fields = card_fields(user, m);

    let font_regular = Font::try_from_bytes(FONT_REGULAR).ok_or("failed to load regular font")?;
    let font_bold = Font::try_from_bytes(FONT_BOLD).ok_or("failed to load bold font")?;

    let mut canvas: RgbImage = printpdf::image_crate::ImageBuffer::from_pixel(
        px_u32(PAGE_W), px_u32(PAGE_H), printpdf::image_crate::Rgb(WHITE_U8),
    );

    place_image_raster(&mut canvas, HEADER_PNG, 0.0, HEADER_Y, HEADER_W, HEADER_H);
    place_image_raster(&mut canvas, TITLEBAR_PNG, 0.0, TITLEBAR_Y, TITLEBAR_W, TITLEBAR_H);
    place_image_raster(&mut canvas, FOOTER_PNG, 0.0, 0.0, FOOTER_W, FOOTER_H);

    let field_scale = Scale::uniform(FIELD_FONT_PT / 72.0 * IMG_DPI);
    let rows: &[(&str, &str)] = &[
        ("Name", &fields.name),
        ("Mobile No.", &fields.mobile),
        ("ID No.", &fields.id_number),
        ("Plan", &fields.plan_name),
        ("Valid Upto", &fields.valid_upto),
    ];
    for (i, (label, value)) in rows.iter().enumerate() {
        let y_pt = FIELD_ROW0_Y - i as f32 * FIELD_GAP;
        let y_px = flip_y_px(y_pt, FIELD_FONT_PT * 0.352_778 * (72.0 / 25.4));
        draw_text_mut(&mut canvas, printpdf::image_crate::Rgb(NAVY_U8), px(FIELD_X), y_px, field_scale, &font_bold, label);
        draw_text_mut(&mut canvas, printpdf::image_crate::Rgb(GRAY_LABEL_U8), px(COLON_X), y_px, field_scale, &font_regular, ":");
        draw_text_mut(&mut canvas, printpdf::image_crate::Rgb(BLACK_U8), px(VALUE_X), y_px, field_scale, &font_bold, value);
    }

    // Barcode
    let bars = barcode_bars(&fields.id_number)?;
    let module_w_px = px_u32(BARCODE_W) as f32 / bars.len() as f32;
    let barcode_top = flip_y_px(BARCODE_Y, BARCODE_H);
    for (i, bit) in bars.iter().enumerate() {
        if *bit == 1 {
            let rect = imageproc::rect::Rect::at(px(BARCODE_X) + (i as f32 * module_w_px).round() as i32, barcode_top)
                .of_size(module_w_px.ceil().max(1.0) as u32, px_u32(BARCODE_H).max(1));
            imageproc::drawing::draw_filled_rect_mut(&mut canvas, rect, printpdf::image_crate::Rgb(BLACK_U8));
        }
    }
    let barcode_text_str = barcode_text(&fields.id_number);
    let barcode_text_scale = Scale::uniform(BARCODE_TEXT_PT / 72.0 * IMG_DPI);
    let (btw, _) = text_size(barcode_text_scale, &font_regular, &barcode_text_str);
    let btx = px(BARCODE_X) + (px_u32(BARCODE_W) as i32 - btw) / 2;
    let bty = flip_y_px(BARCODE_TEXT_Y, BARCODE_TEXT_PT * 0.352_778 * (72.0 / 25.4));
    draw_text_mut(&mut canvas, printpdf::image_crate::Rgb(BLACK_U8), btx, bty, barcode_text_scale, &font_regular, &barcode_text_str);

    // Photo — cover-scaled, falling back to placeholder artwork. Rounded corners
    // (matching Java's 6pt clip) are approximated since raster has no clip path:
    // a navy rounded-rect frame is filled first, then the photo is composited
    // inset by the border thickness with its own (slightly smaller) rounding.
    let photo_x0 = px(PHOTO_X);
    let photo_y0 = flip_y_px(PHOTO_Y, PHOTO_H);
    let photo_w = px_u32(PHOTO_W);
    let photo_h = px_u32(PHOTO_H);
    let border_px = px_u32(1.0).max(1);
    let radius_px = px(PHOTO_RADIUS) as f32;

    fill_rounded_rect_raster(&mut canvas, photo_x0, photo_y0, photo_w, photo_h, radius_px, NAVY_U8);
    let inner_w = photo_w.saturating_sub(2 * border_px);
    let inner_h = photo_h.saturating_sub(2 * border_px);
    let inner_radius = (radius_px - border_px as f32).max(0.0);
    if let Some(cropped) = photo_bytes.as_deref().and_then(|b| cover_crop(b, inner_w, inner_h)) {
        overlay_rounded(
            &mut canvas, &cropped,
            photo_x0 + border_px as i32, photo_y0 + border_px as i32,
            inner_w, inner_h, inner_radius,
        );
    } else {
        let placeholder = printpdf::image_crate::load_from_memory(PHOTO_PLACEHOLDER_PNG)?;
        overlay_rounded(
            &mut canvas, &placeholder,
            photo_x0 + border_px as i32, photo_y0 + border_px as i32,
            inner_w, inner_h, inner_radius,
        );
    }

    place_image_raster(&mut canvas, SIGNATURE_PNG, SIG_X, SIG_Y, SIG_W, SIG_H);

    let mut bytes: Vec<u8> = Vec::new();
    DynamicImage::ImageRgb8(canvas).write_to(&mut std::io::Cursor::new(&mut bytes), ImageOutputFormat::Png)?;
    Ok(bytes)
}

// ── Photo cropping ────────────────────────────────────────────────────────────

/// Decodes `bytes` and cover-crops/resizes it to exactly `(target_w, target_h)`
/// pixels — "cover" scaling (fills the box, crops overflow) rather than
/// "contain" (letterboxed), matching Java's `Math.max`-ratio cover scale.
/// Returns `None` on any decode failure so the caller can fall back to the
/// placeholder artwork.
fn cover_crop(bytes: &[u8], target_w: u32, target_h: u32) -> Option<DynamicImage> {
    let img = printpdf::image_crate::load_from_memory(bytes).ok()?;
    let (w, h) = img.dimensions();
    let target_ratio = target_w as f32 / target_h as f32;
    let src_ratio = w as f32 / h as f32;

    let (crop_w, crop_h) = if src_ratio > target_ratio {
        ((h as f32 * target_ratio).round() as u32, h)
    } else {
        (w, (w as f32 / target_ratio).round() as u32)
    };
    let x = (w - crop_w) / 2;
    let y = (h - crop_h) / 2;
    let cropped = img.crop_imm(x, y, crop_w.max(1), crop_h.max(1));
    Some(cropped.resize_exact(target_w.max(1), target_h.max(1), imageops::FilterType::Lanczos3))
}

#[cfg(test)]
mod tests {
    use super::*;
    use printpdf::image_crate::{ImageBuffer, Rgb as ImgRgb};

    fn synthetic_png(w: u32, h: u32) -> Vec<u8> {
        let img: ImageBuffer<ImgRgb<u8>, Vec<u8>> = ImageBuffer::from_fn(w, h, |x, y| {
            ImgRgb([((x * 255) / w.max(1)) as u8, ((y * 255) / h.max(1)) as u8, 128])
        });
        let mut bytes = Vec::new();
        DynamicImage::ImageRgb8(img)
            .write_to(&mut std::io::Cursor::new(&mut bytes), ImageOutputFormat::Png)
            .unwrap();
        bytes
    }

    fn sample_user_and_membership() -> (User, MembershipWithPlan) {
        let user = User {
            id: Uuid::new_v4(),
            mobile: Some("9876543210".into()),
            email: Some("student@example.com".into()),
            name: "Test Student".into(),
            address: None,
            father_name: Some("Test Father".into()),
            photo_url: None,
            aadhaar_url: None,
            date_of_birth: chrono::NaiveDate::from_ymd_opt(2000, 1, 1),
            gender: Some("Male".into()),
            is_active: true,
            role: "STUDENT".into(),
            created_at: chrono::Local::now().naive_local(),
            updated_at: None,
        };
        let membership = MembershipWithPlan {
            id: Uuid::new_v4(),
            user_id: user.id,
            plan_id: Uuid::new_v4(),
            plan_name: "Full Day".into(),
            plan_type: "FULL_DAY".into(),
            seat_id: None,
            seat_number: Some("A1".into()),
            shift: Some("FULL_DAY".into()),
            start_date: chrono::Local::now().date_naive(),
            end_date: chrono::Local::now().date_naive() + chrono::Duration::days(30),
            status: "ACTIVE".into(),
            amount_paid: Some(rust_decimal::Decimal::from(1800)),
            plan_price: Some(rust_decimal::Decimal::from(1800)),
            created_at: None,
            dues_amount: None,
        };
        (user, membership)
    }

    #[test]
    fn cover_crop_landscape_photo_returns_target_size() {
        let png = synthetic_png(400, 200);
        let cropped = cover_crop(&png, 100, 150).expect("decode should succeed");
        assert_eq!(cropped.dimensions(), (100, 150));
    }

    #[test]
    fn cover_crop_portrait_photo_returns_target_size() {
        let png = synthetic_png(150, 500);
        let cropped = cover_crop(&png, 100, 150).expect("decode should succeed");
        assert_eq!(cropped.dimensions(), (100, 150));
    }

    #[test]
    fn cover_crop_garbage_bytes_returns_none() {
        assert!(cover_crop(b"not an image", 100, 100).is_none());
    }

    #[test]
    fn barcode_bars_encodes_id_number() {
        let bars = barcode_bars("4CBA604F").expect("should encode");
        assert!(!bars.is_empty());
        assert!(bars.iter().all(|b| *b == 0 || *b == 1));
    }

    #[test]
    fn barcode_text_spaces_out_characters() {
        assert_eq!(barcode_text("4CBA604F"), "4 C B A 6 0 4 F");
    }

    #[test]
    fn build_pdf_with_and_without_photo_produces_valid_pdf() {
        let (user, membership) = sample_user_and_membership();

        let no_photo = build_pdf(&user, &membership, None).expect("must build without a photo");
        assert!(no_photo.starts_with(b"%PDF"), "output must be a real PDF");

        let png = synthetic_png(300, 300);
        let with_photo = build_pdf(&user, &membership, Some(png)).expect("must build with a photo");
        assert!(with_photo.starts_with(b"%PDF"));
        assert_ne!(no_photo.len(), with_photo.len(), "embedding a photo must change the output");
    }

    #[test]
    fn build_image_with_and_without_photo_produces_valid_png() {
        let (user, membership) = sample_user_and_membership();

        let no_photo = build_image(&user, &membership, None).expect("must build without a photo");
        assert!(no_photo.starts_with(&[0x89, b'P', b'N', b'G']), "output must be a real PNG");

        let png = synthetic_png(300, 300);
        let with_photo = build_image(&user, &membership, Some(png)).expect("must build with a photo");
        assert!(with_photo.starts_with(&[0x89, b'P', b'N', b'G']));
        assert_ne!(no_photo.len(), with_photo.len(), "embedding a photo must change the output");
    }
}
