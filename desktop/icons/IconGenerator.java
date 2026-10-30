// Generates the Gladix app icon without any external tooling.
//
//   java desktop/icons/IconGenerator.java
//
// Outputs (all committed, so the icon is reproducible without a rasteriser):
//   desktop/icons/icon.ico        multi-size icon for the Windows exe/msi + shortcut
//   desktop/icons/icon-256.png    same artwork, big
//   desktop/src/main/resources/icon.png   window icon baked into the jar
//
// The artwork is drawn from primitives at 4x and downsampled, which keeps the
// 16px entry readable: a violet rounded tile and one bold white note.

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;

public final class IconGenerator {

    /** Tile gradient, taken from the app theme (ui/Theme.kt). */
    private static final Color TOP = new Color(0x8F, 0x63, 0xFF);
    private static final Color BOTTOM = new Color(0x4C, 0x1F, 0xC4);

    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        File iconsDir = new File("desktop/icons");
        File resDir = new File("desktop/src/main/resources");
        iconsDir.mkdirs();
        resDir.mkdirs();

        BufferedImage big = render(256);
        ImageIO.write(big, "png", new File(iconsDir, "icon-256.png"));
        ImageIO.write(render(512), "png", new File(iconsDir, "icon-512.png"));
        ImageIO.write(big, "png", new File(resDir, "icon.png"));

        writeIco(new File(iconsDir, "icon.ico"));
        writePreview(new File(iconsDir, "icon-preview.png"));
        System.out.println("wrote icon.ico, icon-256.png, icon-512.png, icon-preview.png, resources/icon.png");
    }

    /** Small sizes on a light and a dark strip, so legibility can be checked. */
    private static void writePreview(File out) throws IOException {
        int[] sizes = {16, 24, 32, 48, 64, 128};
        int pad = 12;
        int width = pad;
        for (int s : sizes) width += s + pad;
        int height = (128 + pad) * 2 + pad;

        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF7, 0xF3, 0xFC));
        g.fillRect(0, 0, width, height);
        int x = pad;
        for (int s : sizes) {
            g.drawImage(render(s), x, pad, null);
            x += s + pad;
        }
        g.setColor(new Color(0x0F, 0x0D, 0x14));
        g.fillRect(0, 128 + pad * 2, width, 128 + pad);
        x = pad;
        for (int s : sizes) {
            g.drawImage(render(s), x, 128 + pad * 2 + pad, null);
            x += s + pad;
        }
        g.dispose();
        ImageIO.write(preview, "png", out);
    }

    /** Draws the artwork at [size] pixels (supersampled 4x, then averaged down). */
    private static BufferedImage render(int size) {
        int ss = size <= 128 ? 4 : 2;
        BufferedImage hi = draw(size * ss);
        if (ss == 1) return hi;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(hi, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static BufferedImage draw(int px) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(px, px);   // work in 0..1 unit space

        // Tile -----------------------------------------------------------------
        RoundRectangle2D tile = new RoundRectangle2D.Double(0.0, 0.0, 1.0, 1.0, 0.44, 0.44);
        g.setPaint(new GradientPaint(0f, 0f, TOP, 0f, 1f, BOTTOM));
        g.fill(tile);

        // Soft highlight, top-left, so the tile is not a flat rectangle.
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(0.22, 0.14), 0.85f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 74), new Color(255, 255, 255, 0)}));
        g.fill(tile);

        // Inner hairline keeps the edge crisp on light backgrounds.
        g.setPaint(new Color(255, 255, 255, 46));
        g.setStroke(new BasicStroke(0.016f));
        g.draw(new RoundRectangle2D.Double(0.016, 0.016, 0.968, 0.968, 0.42, 0.42));
        g.setStroke(new BasicStroke(1f));

        // Note -----------------------------------------------------------------
        g.setPaint(Color.WHITE);

        // Head: an ellipse tilted like a written note.
        Ellipse2D head = new Ellipse2D.Double(-0.152, -0.120, 0.304, 0.240);
        AffineTransform headAt = AffineTransform.getTranslateInstance(0.408, 0.655);
        headAt.rotate(-0.42);
        g.fill(headAt.createTransformedShape(head));

        // Stem.
        g.fill(new Rectangle2D.Double(0.516, 0.268, 0.098, 0.392));

        // Flag: thick at the stem, tapering to the right.
        Path2D flag = new Path2D.Double();
        flag.moveTo(0.588, 0.262);
        flag.curveTo(0.730, 0.320, 0.808, 0.420, 0.772, 0.572);
        flag.curveTo(0.762, 0.446, 0.706, 0.364, 0.588, 0.338);
        flag.closePath();
        g.fill(flag);

        g.dispose();
        return img;
    }

    // ---- ICO container ------------------------------------------------------

    /** Vista+ ICO file: PNG payloads, one entry per size. */
    private static void writeIco(File out) throws IOException {
        byte[][] payloads = new byte[ICO_SIZES.length][];
        for (int i = 0; i < ICO_SIZES.length; i++) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            ImageIO.write(render(ICO_SIZES[i]), "png", buf);
            payloads[i] = buf.toByteArray();
        }

        try (OutputStream os = new FileOutputStream(out)) {
            // ICONDIR
            le16(os, 0);                      // reserved
            le16(os, 1);                      // type: icon
            le16(os, ICO_SIZES.length);       // image count

            int offset = 6 + 16 * ICO_SIZES.length;
            for (int i = 0; i < ICO_SIZES.length; i++) {
                int size = ICO_SIZES[i];
                os.write(size >= 256 ? 0 : size);   // width  (0 means 256)
                os.write(size >= 256 ? 0 : size);   // height
                os.write(0);                        // palette colours
                os.write(0);                        // reserved
                le16(os, 1);                        // colour planes
                le16(os, 32);                       // bits per pixel
                le32(os, payloads[i].length);       // bytes in resource
                le32(os, offset);                   // offset of the payload
                offset += payloads[i].length;
            }
            for (byte[] payload : payloads) os.write(payload);
        }
    }

    private static void le16(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
    }

    private static void le32(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
        os.write((v >> 16) & 0xFF);
        os.write((v >> 24) & 0xFF);
    }
}