import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 팀 아이콘 생성기를 쓰기 전에 원본 배지의 구조를 확인한다.
 * 배너가 직사각형인지 사다리꼴인지, 빨강이 어디에 얼마나 쓰였는지,
 * SAJIK 글자가 배너 안 어디에 앉아 있는지를 좌표로 뽑는다.
 */
public class Probe {

    public static void main(String[] args) throws Exception {
        String path = args.length > 0
                ? args[0]
                : "tools/team-icons/src/sajik-badge.png";
        BufferedImage img = ImageIO.read(new File(path));
        int w = img.getWidth();
        int h = img.getHeight();
        System.out.println("size: " + w + " x " + h + "  type=" + img.getType());

        Map<Integer, Integer> hist = new HashMap<>();
        int transparent = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24);
                if (a < 8) {
                    transparent++;
                    continue;
                }
                hist.merge(argb & 0xFFFFFF, 1, Integer::sum);
            }
        }
        System.out.println("fully transparent px: " + transparent);

        List<Map.Entry<Integer, Integer>> top = new ArrayList<>(hist.entrySet());
        top.sort(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed());
        System.out.println("\n-- top colors --");
        for (int i = 0; i < Math.min(14, top.size()); i++) {
            Map.Entry<Integer, Integer> e = top.get(i);
            System.out.printf("  #%06X  %6d%n", e.getKey(), e.getValue());
        }

        // 빨강: R이 G/B보다 확실히 크고 충분히 진한 것
        // 흰색: 세 채널 모두 높은 것
        int[] redRowFirst = new int[h];
        int[] redRowLast = new int[h];
        int[] redRowCount = new int[h];
        int[] whiteRowFirst = new int[h];
        int[] whiteRowLast = new int[h];
        int[] whiteRowCount = new int[h];
        for (int y = 0; y < h; y++) {
            redRowFirst[y] = -1;
            whiteRowFirst[y] = -1;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 8) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (isRed(r, g, b)) {
                    if (redRowFirst[y] < 0) redRowFirst[y] = x;
                    redRowLast[y] = x;
                    redRowCount[y]++;
                }
                if (isWhite(r, g, b)) {
                    if (whiteRowFirst[y] < 0) whiteRowFirst[y] = x;
                    whiteRowLast[y] = x;
                    whiteRowCount[y]++;
                }
            }
        }

        System.out.println("\n-- red rows (y: firstX..lastX  count) --");
        for (int y = 0; y < h; y++) {
            if (redRowCount[y] > 0) {
                System.out.printf("  %3d: %3d..%3d  %4d%n", y, redRowFirst[y], redRowLast[y], redRowCount[y]);
            }
        }

        // 배너는 빨강이 가장 넓게 연속으로 깔린 구간이다. 그 안의 흰 글자를 따로 본다.
        int bannerTop = -1, bannerBottom = -1, widest = 0;
        for (int y = 0; y < h; y++) {
            if (redRowCount[y] > widest) widest = redRowCount[y];
        }
        for (int y = 0; y < h; y++) {
            if (redRowCount[y] >= widest * 0.5) {
                if (bannerTop < 0) bannerTop = y;
                bannerBottom = y;
            }
        }
        System.out.println("\nbanner rows (>=50% of widest red row): y " + bannerTop + ".." + bannerBottom
                + "  widest=" + widest);

        System.out.println("\n-- white rows inside banner band --");
        for (int y = Math.max(0, bannerTop - 4); y <= Math.min(h - 1, bannerBottom + 4); y++) {
            System.out.printf("  %3d: %3d..%3d  %4d%n", y, whiteRowFirst[y], whiteRowLast[y], whiteRowCount[y]);
        }

        // 배너 각 행의 빨강 좌우 끝 + 그 행에서 빨강 사이에 낀 흰 글자 범위
        System.out.println("\n-- banner row detail (redSpan | whiteSpan within redSpan) --");
        for (int y = bannerTop; y <= bannerBottom; y++) {
            int rf = redRowFirst[y], rl = redRowLast[y];
            int wf = -1, wl = -1, wc = 0;
            for (int x = rf; x <= rl && rf >= 0; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 8) continue;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (isWhite(r, g, b)) {
                    if (wf < 0) wf = x;
                    wl = x;
                    wc++;
                }
            }
            System.out.printf("  %3d: red %3d..%3d (%3d wide) | white %3d..%3d (%3d px)%n",
                    y, rf, rl, rl - rf + 1, wf, wl, wc);
        }

        // 가장자리 여백: 불투명 픽셀의 bounding box
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) >= 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        System.out.println("\nopaque bbox: x " + minX + ".." + maxX + "  y " + minY + ".." + maxY);
    }

    static boolean isRed(int r, int g, int b) {
        return r > 110 && r > g * 2 && r > b * 2;
    }

    static boolean isWhite(int r, int g, int b) {
        return r > 200 && g > 200 && b > 200;
    }
}
