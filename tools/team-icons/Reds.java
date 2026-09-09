import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

/** 배너 빨강이 단색인지 그라데이션인지, SAJIK 글자 상자가 정확히 어디인지 본다. */
public class Reds {

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File(
                "tools/team-icons/src/sajik-badge.png"));
        int w = img.getWidth(), h = img.getHeight();

        System.out.println("-- row median red (banner rows) --");
        for (int y = 185; y <= 305; y += 5) {
            List<Integer> rs = new ArrayList<>(), gs = new ArrayList<>(), bs = new ArrayList<>();
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (isRed(r, g, b)) { rs.add(r); gs.add(g); bs.add(b); }
            }
            if (rs.isEmpty()) continue;
            Collections.sort(rs); Collections.sort(gs); Collections.sort(bs);
            int n = rs.size();
            System.out.printf("  y=%3d n=%3d  median #%02X%02X%02X   min #%02X%02X%02X  max #%02X%02X%02X%n",
                    y, n, rs.get(n / 2), gs.get(n / 2), bs.get(n / 2),
                    rs.get(0), gs.get(0), bs.get(0),
                    rs.get(n - 1), gs.get(n - 1), bs.get(n - 1));
        }

        // 글자 상자: 빨강 스팬 안쪽의 흰 픽셀만
        int[] redFirst = new int[h], redLast = new int[h];
        for (int y = 0; y < h; y++) {
            redFirst[y] = -1; redLast[y] = -1;
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (isRed(r, g, b)) { if (redFirst[y] < 0) redFirst[y] = x; redLast[y] = x; }
            }
        }
        int tminX = w, tmaxX = -1, tminY = h, tmaxY = -1, count = 0;
        for (int y = 0; y < h; y++) {
            if (redFirst[y] < 0) continue;
            for (int x = redFirst[y] + 1; x < redLast[y]; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (r > 200 && g > 200 && b > 200) {
                    count++;
                    if (x < tminX) tminX = x;
                    if (x > tmaxX) tmaxX = x;
                    if (y < tminY) tminY = y;
                    if (y > tmaxY) tmaxY = y;
                }
            }
        }
        System.out.printf("%nSAJIK glyph bbox: x %d..%d (w=%d)  y %d..%d (h=%d)  px=%d%n",
                tminX, tmaxX, tmaxX - tminX + 1, tminY, tmaxY, tmaxY - tminY + 1, count);
        System.out.printf("banner red span at text rows: y=%d %d..%d   y=%d %d..%d%n",
                tminY, redFirst[tminY], redLast[tminY], tmaxY, redFirst[tmaxY], redLast[tmaxY]);
        System.out.printf("text center x=%.1f   banner center x at y=250: %.1f%n",
                (tminX + tmaxX) / 2.0, (redFirst[250] + redLast[250]) / 2.0);
    }

    static boolean isRed(int r, int g, int b) {
        return r > 110 && r > g * 2 && r > b * 2;
    }
}
