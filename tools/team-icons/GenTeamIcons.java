import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 10구단 런처 아이콘을 원본 SAJIK 배지에서 파생시킨다.
 *
 *   java GenTeamIcons.java [--all]
 *
 * 인자가 없으면 LT·OB·NC 세 장만 뽑는다 (원본 대조 / 가장 어두운 색 / 가장 긴 이름).
 *
 * 원본 배너는 #D00A26 단색이라 빨강을 팀 색으로 밀고, 배너 안쪽 흰 글자를
 * 지운 뒤 홈구장 이름을 다시 그린다. adaptive icon 안전영역용 16% 여백은
 * PNG 안에 직접 넣어 inset 래퍼가 필요 없게 한다.
 */
public class GenTeamIcons {

    static final String SRC = "tools/team-icons/src/sajik-badge.png";
    static final String OUT_DIR = "tools/team-icons/out";
    static final String FONT_PATH = "C:/Windows/Fonts/ariblk.ttf";

    /** 원본 배너의 단색 빨강. 밝기 기준값으로도 쓴다. */
    static final int SRC_RED = 0xD00A26;
    /** 배너가 차지하는 행. 이 밖의 흰색(아치 외곽선·홈플레이트)은 건드리지 않는다. */
    static final int BANNER_TOP = 186;
    static final int BANNER_BOTTOM = 304;
    /** adaptive icon 바깥 18%가 잘리므로 전경을 미리 줄여 둔다. */
    static final double INSET = 0.16;

    /**
     * @param minLum 배경 네이비(밝기 21)에 묻히는 팀 색을 끌어올릴 목표 밝기. 0이면 그대로 쓴다.
     *               키움 버건디만 낮게 잡는다. 롯데·KIA·SSG와 색상이 같은 계열이라
     *               밝기를 맞추면 배너가 구분되지 않는다.
     */
    record Team(String code, String stadium, int color, double minLum) {}

    static final Team[] TEAMS = {
        new Team("lt", "SAJIK",    0xD00F31,  0),
        new Team("ob", "JAMSIL",   0x131230, 80),
        new Team("lg", "JAMSIL",   0xC30452,  0),
        new Team("ss", "DAEGU",    0x0054A6,  0),
        new Team("hh", "DAEJEON",  0xFF6600,  0),
        new Team("kt", "SUWON",    0x333333, 95),
        new Team("ht", "GWANGJU",  0xEA0029,  0),
        new Team("nc", "CHANGWON", 0x315288,  0),
        new Team("sk", "INCHEON",  0xCE0E2D,  0),
        new Team("wo", "GOCHEOK",  0x570514, 52),
    };

    public static void main(String[] args) throws Exception {
        boolean all = args.length > 0 && args[0].equals("--all");
        BufferedImage src = ImageIO.read(new File(SRC));
        new File(OUT_DIR).mkdirs();

        boolean[][] glyphMask = glyphMask(src);
        int[] bbox = maskBounds(glyphMask);
        System.out.printf("SAJIK 글자 상자: x %d..%d (w=%d)  y %d..%d (h=%d)%n",
                bbox[0], bbox[2], bbox[2] - bbox[0] + 1, bbox[1], bbox[3], bbox[3] - bbox[1] + 1);

        Font base = Font.createFont(Font.TRUETYPE_FONT, new File(FONT_PATH));

        for (Team t : TEAMS) {
            if (!all && !(t.code.equals("lt") || t.code.equals("ob") || t.code.equals("nc"))) continue;
            int banner = liftForContrast(t.color, t.minLum);
            BufferedImage out = render(src, glyphMask, bbox, base, t, banner);
            File f = new File(OUT_DIR, "ic_launcher_art_" + t.code + ".png");
            ImageIO.write(out, "png", f);
            System.out.printf("  %-3s %-9s #%06X -> #%06X  %s%n",
                    t.code.toUpperCase(), t.stadium, t.color, banner, f.getPath());
        }
    }

    /** 배너 안쪽 흰 글자. 각 행의 빨강 스팬 안에 있는 것만 글자로 본다. */
    static boolean[][] glyphMask(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] mask = new boolean[h][w];
        for (int y = BANNER_TOP; y <= BANNER_BOTTOM && y < h; y++) {
            int first = -1, last = -1;
            for (int x = 0; x < w; x++) {
                if (isRed(img.getRGB(x, y))) {
                    if (first < 0) first = x;
                    last = x;
                }
            }
            if (first < 0) continue;
            for (int x = first + 1; x < last; x++) {
                int c = img.getRGB(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                if (r > 150 && g > 150 && b > 150) mask[y][x] = true;
            }
        }
        return dilate(mask, 3);
    }

    /** 글자 경계의 반투명 픽셀까지 지우려고 마스크를 넓힌다. */
    static boolean[][] dilate(boolean[][] m, int rad) {
        int h = m.length, w = m[0].length;
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                for (int dy = -rad; dy <= rad; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -rad; dx <= rad; dx++) {
                        int xx = x + dx;
                        if (xx >= 0 && xx < w) out[yy][xx] = true;
                    }
                }
            }
        }
        return out;
    }

    static int[] maskBounds(boolean[][] m) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m[0].length; x++) {
                if (!m[y][x]) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        // dilate로 넓힌 만큼 되돌려 실제 글자 상자를 돌려준다.
        return new int[] { minX + 3, minY + 3, maxX - 3, maxY - 3 };
    }

    static BufferedImage render(
            BufferedImage src, boolean[][] glyph, int[] box, Font baseFont, Team t, int banner) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage badge = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        double srcLum = lum(SRC_RED);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getRGB(x, y);
                if (glyph[y][x]) {
                    badge.setRGB(x, y, 0xFF000000 | banner);
                } else if (isRedish(c)) {
                    badge.setRGB(x, y, 0xFF000000 | scaleTo(banner, lum(c) / srcLum));
                } else {
                    badge.setRGB(x, y, c);
                }
            }
        }

        Graphics2D g = badge.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        g.fill(fitText(g, baseFont, t.stadium, box));
        g.dispose();

        return withInset(badge);
    }

    /** 글자를 원본 SAJIK 상자에 맞춘다. 긴 이름은 높이를 지키고 가로만 좁힌다. */
    static Shape fitText(Graphics2D g, Font baseFont, String text, int[] box) {
        double boxW = box[2] - box[0] + 1;
        double boxH = box[3] - box[1] + 1;
        Font f = baseFont.deriveFont(Font.PLAIN, 100f);
        GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        Shape outline = gv.getOutline();
        Rectangle2D b = outline.getBounds2D();

        double sy = boxH / b.getHeight();
        double sx = sy;
        if (b.getWidth() * sx > boxW) sx = boxW / b.getWidth();

        AffineTransform at = new AffineTransform();
        at.translate(
                box[0] + (boxW - b.getWidth() * sx) / 2.0,
                box[1] + (boxH - b.getHeight() * sy) / 2.0);
        at.scale(sx, sy);
        at.translate(-b.getX(), -b.getY());
        return at.createTransformedShape(outline);
    }

    /**
     * 배지를 16% 줄여 가운데 놓는다. 바깥 링은 투명으로 남겨
     * adaptive icon 배경(@color/ic_launcher_background)이 그대로 비치게 한다.
     */
    static BufferedImage withInset(BufferedImage badge) {
        int w = badge.getWidth(), h = badge.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int pad = (int) Math.round(w * INSET);
        g.drawImage(badge, pad, pad, w - 2 * pad, h - 2 * pad, null);
        g.dispose();
        return out;
    }

    /**
     * 원본 빨강과 같은 밝기까지 팀 색을 끌어올려 네이비 배경에서 배너가 보이게 한다.
     * 흰색을 섞으면 두산 네이비가 회보라로 바래므로, 색상·채도는 두고 명도만 올린다.
     */
    static int liftForContrast(int rgb, double target) {
        if (target <= 0 || lum(rgb) >= target) return rgb;
        float[] hsb = Color.RGBtoHSB(
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        float lo = hsb[2], hi = 1f;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) / 2f;
            if (lum(Color.HSBtoRGB(hsb[0], hsb[1], mid) & 0xFFFFFF) < target) lo = mid;
            else hi = mid;
        }
        return Color.HSBtoRGB(hsb[0], hsb[1], hi) & 0xFFFFFF;
    }

    static int scaleTo(int rgb, double factor) {
        int r = clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = clamp((int) Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    static double lum(int rgb) {
        return 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static boolean isRed(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return r > 110 && r > g * 2 && r > b * 2;
    }

    /** 글자·배경과 섞인 가장자리까지 포함한 넓은 판정. 네이비는 r이 낮아 걸리지 않는다. */
    static boolean isRedish(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return r > 60 && r > g + 30 && r > b + 25;
    }
}
