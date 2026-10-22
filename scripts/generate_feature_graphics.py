import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps
import numpy as np

W, H = 1024, 500

font_title_large = ImageFont.truetype('app/src/main/res/font/outfit.ttf', 48)
font_title_med = ImageFont.truetype('app/src/main/res/font/outfit.ttf', 38)
font_sub = ImageFont.truetype('app/src/main/res/font/outfit.ttf', 19)
font_badge_title = ImageFont.truetype('app/src/main/res/font/atkinson_bold.ttf', 14)
font_badge_desc = ImageFont.truetype('app/src/main/res/font/atkinson_regular.ttf', 13)

def create_base_canvas(include_right_stripes=True):
    # Rich royal blue gradient matching Vern brand: (69, 64, 216) -> (50, 45, 180)
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    grad = np.zeros((H, W, 4), dtype=np.uint8)
    for y in range(H):
        for x in range(W):
            factor = (y / H) * 0.65 + (x / W) * 0.35
            r = int(72 * (1 - factor) + 48 * factor)
            g = int(68 * (1 - factor) + 42 * factor)
            b = int(228 * (1 - factor) + 180 * factor)
            grad[y, x] = [r, g, b, 255]
    base = Image.fromarray(grad)
    
    if include_right_stripes:
        # Subtle accent stripes strictly on the far right behind the V logo (matching original)
        # Does NOT run across the graphic or behind the text/pills
        stripes = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(stripes)
        for xs in [710, 768, 826, 884, 942]:
            sdraw.line([(xs, 0), (xs - 70, H)], fill=(255, 255, 255, 14), width=2)
        base = Image.alpha_composite(base, stripes)
        
    return base

def make_phone_mockup(screenshot_path, target_height=420, corner_radius=22, bezel=6, shadow_opacity=130):
    ss = Image.open(screenshot_path).convert('RGBA')
    aspect = ss.width / ss.height
    screen_w = int(target_height * aspect)
    screen_h = target_height
    ss_resized = ss.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    
    # Screen with rounded corners
    mask = Image.new('L', (screen_w, screen_h), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle([(0, 0), (screen_w, screen_h)], radius=corner_radius - 4, fill=255)
    ss_rounded = ImageOps.fit(ss_resized, (screen_w, screen_h))
    ss_rounded.putalpha(mask)
    
    phone_w = screen_w + bezel * 2
    phone_h = screen_h + bezel * 2
    
    phone = Image.new('RGBA', (phone_w, phone_h), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(phone)
    
    # Phone frame (sleek dark bezel with fine metallic edge highlight)
    pdraw.rounded_rectangle([(0, 0), (phone_w, phone_h)], radius=corner_radius, fill=(18, 16, 26, 255), outline=(255, 255, 255, 60), width=1)
    phone.paste(ss_rounded, (bezel, bezel), ss_rounded)
    
    # Camera punch hole
    hole_w, hole_h = 8, 8
    hole_x = phone_w // 2 - hole_w // 2
    hole_y = bezel + 6
    pdraw.ellipse([(hole_x, hole_y), (hole_x + hole_w, hole_y + hole_h)], fill=(10, 10, 16, 255))
    
    # Soft realistic shadow
    pad = 40
    pw_tot, ph_tot = phone_w + pad * 2, phone_h + pad * 2
    comp = Image.new('RGBA', (pw_tot, ph_tot), (0, 0, 0, 0))
    
    s_mask = Image.new('RGBA', (pw_tot, ph_tot), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(s_mask)
    sdraw.rounded_rectangle([(pad + 4, pad + 14), (pad + phone_w + 4, pad + phone_h + 14)], radius=corner_radius, fill=(0, 0, 0, shadow_opacity))
    s_blur = s_mask.filter(ImageFilter.GaussianBlur(18))
    
    comp.paste(s_blur, (0, 0), s_blur)
    comp.paste(phone, (pad, pad), phone)
    return comp, phone_w, phone_h, pad

def draw_pill(draw, x, y, width, height, icon_text, title, desc):
    # Frosted pill background
    pill = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(pill)
    pdraw.rounded_rectangle([(0, 0), (width, height)], radius=height // 2, fill=(0, 0, 0, 45), outline=(255, 255, 255, 45), width=1)
    
    # Texts
    tx = 22
    pdraw.text((tx, height // 2 - 9), title, fill=(255, 246, 230, 255), font=font_badge_title)
    
    bbox = font_badge_title.getbbox(title)
    title_w = bbox[2] - bbox[0]
    
    pdraw.text((tx + title_w + 8, height // 2 - 8), "•", fill=(255, 255, 255, 120), font=font_badge_desc)
    pdraw.text((tx + title_w + 20, height // 2 - 8), desc, fill=(225, 222, 255, 240), font=font_badge_desc)
    return pill

def generate_option1_showcase():
    base = create_base_canvas()
    
    # Ambient radial light behind phones
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([(600, 100), (1000, 460)], fill=(120, 105, 255, 60))
    glow_blurred = glow.filter(ImageFilter.GaussianBlur(50))
    base = Image.alpha_composite(base, glow_blurred)
    
    # Left Header: Logo mark + Vern + Tagline
    v_mark = Image.open('release/v2.6.2/graphics/v_mark_highres.png').convert('RGBA')
    v_logo_small = v_mark.resize((int(v_mark.width * 0.16), int(v_mark.height * 0.16)), Image.Resampling.LANCZOS)
    
    lx = 68
    ly = 52
    base.paste(v_logo_small, (lx, ly + 4), v_logo_small)
    
    draw = ImageDraw.Draw(base)
    draw.text((lx + v_logo_small.width + 16, ly), "Vern", fill=(255, 255, 255, 255), font=font_title_large)
    
    # Tagline
    ty = ly + 62
    draw.text((lx, ty), "Read. Listen. Make it yours.", fill=(255, 246, 230, 255), font=font_sub)
    
    # Feature Pills
    pills = [
        ("Universal Reader", "PDF, EPUB, DOCX & OCR"),
        ("Neural Narration", "Studio-Quality Offline Voices"),
        ("Active-Recall Lab", "Flashcards & Study Hub"),
        ("100% Private", "On-Device • Zero Trackers")
    ]
    
    py = ty + 46
    pill_w = 420
    pill_h = 42
    for title, desc in pills:
        pill_img = draw_pill(draw, lx, py, pill_w, pill_h, "", title, desc)
        base.paste(pill_img, (lx, py), pill_img)
        py += pill_h + 12
        
    # Right: Dual Phone Mockups
    # Phone 1 (Back, Audio Player)
    phone_audio, pw1, ph1, pad1 = make_phone_mockup('docs/screenshots/audio_player.png', target_height=410, shadow_opacity=110)
    base.paste(phone_audio, (560 - pad1, 48 - pad1), phone_audio)
    
    # Phone 2 (Front, Current Reader)
    phone_reader, pw2, ph2, pad2 = make_phone_mockup('docs/screenshots/current_reader.png', target_height=435, shadow_opacity=150)
    base.paste(phone_reader, (735 - pad2, 32 - pad2), phone_reader)
    
    out_path = 'release/v2.6.2/graphics/feature-graphic-option1-showcase.png'
    base.convert('RGB').save(out_path, quality=95)
    print('Generated Option 1:', out_path)

def generate_option2_hero():
    base = create_base_canvas()
    
    # Place large V mark on right side, matching original position
    v_mark = Image.open('release/v2.6.2/graphics/v_mark_highres.png').convert('RGBA')
    vw = int(v_mark.width * 0.85)
    vh = int(v_mark.height * 0.85)
    v_scaled = v_mark.resize((vw, vh), Image.Resampling.LANCZOS)
    
    # Ambient glow behind V mark
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([(620, 100), (980, 420)], fill=(130, 115, 255, 65))
    glow_blurred = glow.filter(ImageFilter.GaussianBlur(40))
    base = Image.alpha_composite(base, glow_blurred)
    
    # Paste V mark
    vx = 645
    vy = 120
    base.paste(v_scaled, (vx, vy), v_scaled)
    
    # Phone mockup nestled elegantly beside/over the V mark
    phone_reader, pw, ph, pad = make_phone_mockup('docs/screenshots/current_reader.png', target_height=425, shadow_opacity=140)
    base.paste(phone_reader, (520 - pad, 38 - pad), phone_reader)
    
    # Left Header: Icon + Extra BOLD Vern
    lx = 64
    ly = 48

    v_icon_small = v_mark.resize((int(v_mark.width * 0.16), int(v_mark.height * 0.16)), Image.Resampling.LANCZOS)
    base.paste(v_icon_small, (lx, ly + 6), v_icon_small)

    font_title_bolder = ImageFont.truetype('app/src/main/res/font/outfit.ttf', 52)
    draw = ImageDraw.Draw(base)
    vern_x = lx + v_icon_small.width + 16
    vern_y = ly
    draw.text((vern_x, vern_y), "Vern", fill=(255, 255, 255, 255), font=font_title_bolder, stroke_width=2, stroke_fill=(255, 255, 255, 255))
    
    # Tagline
    ty = ly + 68
    draw.text((lx, ty), "Read. Listen. Make it yours.", fill=(255, 246, 230, 255), font=font_sub)
    
    pills = [
        ("Universal Reader", "PDF, EPUB, DOCX & OCR"),
        ("Neural Narration", "Offline Realistic Voices"),
        ("Active-Recall Lab", "SM-2 Flashcards & Notes"),
        ("100% Private", "On-Device • No Accounts")
    ]
    
    py = ty + 46
    pill_w = 405
    pill_h = 42
    for title, desc in pills:
        pill_img = draw_pill(draw, lx, py, pill_w, pill_h, "", title, desc)
        base.paste(pill_img, (lx, py), pill_img)
        py += pill_h + 12
        
    out_path = 'release/v2.6.2/graphics/feature-graphic-option2-hero.png'
    base.convert('RGB').save(out_path, quality=95)
    print('Generated Option 2:', out_path)

if __name__ == '__main__':
    generate_option1_showcase()
    generate_option2_hero()
