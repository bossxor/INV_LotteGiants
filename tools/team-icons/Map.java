import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 배지 구조를 눈으로 보려고 4픽셀마다 한 글자씩 찍는다. */
public class Map {

    public static void main(String[] args) throws Exception {
        String path = args.length > 0
                ? args[0]
                : "tools/team-icons/src/sajik-badge.png";
        int step = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        BufferedImage img = ImageIO.read(new File(path));
        int w = img.getWidth();
        int h = img.getHeight();

        System.out.println("legend  . navy   R red   W white   # dark/other");
        StringBuilder ruler = new StringBuilder("     ");
        for (int x = 0; x < w; x += step) {
            ruler.append((x % 100 == 0) ? '|' : (x % 40 == 0 ? '+' : ' '));
        }
        System.out.println(ruler);

        for (int y = 0; y < h; y += step) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%4d ", y));
            for (int x = 0; x < w; x += step) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                sb.append(classify(r, g, b));
            }
            System.out.println(sb);
        }
    }

    static char classify(int r, int g, int b) {
        if (r > 200 && g > 200 && b > 200) return 'W';
        if (r > 110 && r > g * 2 && r > b * 2) return 'R';
        int lum = (r + g + b) / 3;
        if (b > r && b > 40 && lum < 90) return '.';
        if (lum < 30) return '#';
        return '?';
    }
}
