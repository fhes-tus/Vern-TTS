import os
from PIL import Image, ImageDraw, ImageFont
import numpy as np

font = ImageFont.truetype('app/src/main/res/font/atkinson_bold.ttf', 13)

def draw_clean_status_bar(img, bg_color, dark_mode=False):
    draw = ImageDraw.Draw(img)
    # Status bar area: y=0 to y=40
    draw.rectangle([(0, 0), (img.width, 40)], fill=bg_color)
    
    fg_color = (255, 255, 255, 230) if dark_mode else (35, 35, 35, 230)
    
    # 1. Time (Google Play standard 09:30)
    draw.text((18, 12), "09:30", fill=fg_color, font=font)
    
    # 2. Cellular Signal (4 ascending clean bars)
    sig_x = img.width - 92
    for i in range(4):
        bx = sig_x + i * 5
        bh = 4 + i * 3
        by = 26 - bh
        draw.rounded_rectangle([(bx, by), (bx + 3, 26)], radius=1, fill=fg_color)
        
    # 3. WiFi icon
    wifi_cx = img.width - 64
    draw.ellipse([(wifi_cx - 8, 14), (wifi_cx + 8, 30)], outline=fg_color, width=2)
    draw.ellipse([(wifi_cx - 4, 19), (wifi_cx + 4, 27)], outline=fg_color, width=2)
    draw.ellipse([(wifi_cx - 1, 24), (wifi_cx + 1, 26)], fill=fg_color)
    # clip bottom half of wifi
    draw.rectangle([(wifi_cx - 10, 22), (wifi_cx + 10, 35)], fill=bg_color)
    draw.ellipse([(wifi_cx - 2, 23), (wifi_cx + 2, 27)], fill=fg_color)
    
    # 4. Battery Icon (100% full)
    bat_x = img.width - 42
    draw.rounded_rectangle([(bat_x, 14), (bat_x + 22, 26)], radius=3, outline=fg_color, width=1)
    draw.rounded_rectangle([(bat_x + 2, 16), (bat_x + 19, 24)], radius=2, fill=fg_color)
    draw.rounded_rectangle([(bat_x + 22, 17), (bat_x + 24, 23)], radius=1, fill=fg_color)

def process_all_screenshots():
    src_dir = r'C:/Users/wwwfe/.gemini/antigravity/brain/c4f5c4f9-1de1-4b6a-af41-9c8357254aef/.user_uploaded'
    out_dir = r'release/v2.6.2/store-screenshots'
    art_dir = r'C:/Users/wwwfe/.gemini/antigravity/brain/c4f5c4f9-1de1-4b6a-af41-9c8357254aef'
    os.makedirs(out_dir, exist_ok=True)
    
    # Configuration for all 8 screenshots: (source_filename, output_name, bg_color, is_dark_status)
    configs = [
        ('media_1792462946255_09b44b53.png', '01_reading_page_curl.png', (250, 243, 236), False),
        ('media_1792462946177_479cf66a.png', '02_audio_player_tts.png', (248, 234, 229), False),
        ('media_1792463274494_fd612841.png', '03_home_dashboard.png', (250, 247, 242), False),
        ('media_1792462946105_22807641.png', '04_study_hub_flashcards.png', (250, 247, 242), False),
        ('media_1792463264000_63575451.png', '05_reading_insights.png', (100, 99, 97), True),
        ('media_1792462946179_455e303a.png', '06_notes_annotations.png', (250, 247, 242), False),
        ('media_179246038_ae7b904f.png', '07_classics_library.png', (250, 247, 242), False),
        ('media_1792463238660_a10b52dc.png', '08_pdf_original_view.png', (250, 250, 250), False)
    ]
    
    # Correction for classics filename if needed
    for i, c in enumerate(configs):
        if not os.path.exists(os.path.join(src_dir, c[0])):
            # Find closest match in user_uploaded
            for uf in os.listdir(src_dir):
                if c[0].split('_')[-1] in uf:
                    configs[i] = (uf, c[1], c[2], c[3])
                    break

    target_w, target_h = 472, 1024
    
    for src_file, out_name, bg_col, is_dark in configs:
        sp = os.path.join(src_dir, src_file)
        raw = Image.open(sp).convert('RGB')
        
        if out_name == '08_pdf_original_view.png':
            # PDF view was cropped without status bar: resize and pad top by 36px so it aligns with other screens
            scaled_h = target_h - 36
            raw_scaled = raw.resize((target_w, scaled_h), Image.Resampling.LANCZOS)
            canvas = Image.new('RGB', (target_w, target_h), bg_col)
            canvas.paste(raw_scaled, (0, 36))
            img = canvas
        else:
            if raw.size != (target_w, target_h):
                img = raw.resize((target_w, target_h), Image.Resampling.LANCZOS)
            else:
                img = raw.copy()
                
        # Draw the clean status bar over the top
        draw_clean_status_bar(img, bg_col, dark_mode=is_dark)
        
        # Save to release/v2.6.2/store-screenshots
        out_path = os.path.join(out_dir, out_name)
        img.save(out_path, quality=95)
        
        # Also copy to artifact dir for preview
        art_path = os.path.join(art_dir, out_name)
        img.save(art_path, quality=95)
        print(f"Cleaned {out_name}: saved to {out_path} and {art_path}")

if __name__ == '__main__':
    process_all_screenshots()
