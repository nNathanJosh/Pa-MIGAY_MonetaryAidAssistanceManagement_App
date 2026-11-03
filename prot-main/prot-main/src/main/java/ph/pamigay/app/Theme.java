package ph.pamigay.app;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

final class Theme {
    static final Color NAV = new Color(19, 34, 43);
    static final Color NAV_HOVER = new Color(34, 57, 65);
    static final Color MINT = new Color(126, 193, 171);
    static final Color INK = new Color(28, 43, 52);
    static final Color MUTED = new Color(106, 122, 129);
    static final Color BG = new Color(245, 247, 247);
    static final Color LINE = new Color(223, 230, 230);
    static final Color WHITE = Color.WHITE;
    private static String fontFamily = "Segoe UI";

    private Theme() {}

    static void apply() {
        loadBundledFont();
        FontUIResource font = new FontUIResource(fontFamily, Font.PLAIN, 13);
        UIManager.put("defaultFont", font);
        UIManager.put("Label.font", font);
        UIManager.put("Button.font", new FontUIResource(fontFamily, Font.BOLD, 13));
        UIManager.put("TextField.font", font);
        UIManager.put("TextArea.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("Table.font", font);
        UIManager.put("TableHeader.font", new FontUIResource(fontFamily, Font.BOLD, 12));
        UIManager.put("OptionPane.messageFont", font);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", WHITE);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("TextField.background", WHITE);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextArea.background", WHITE);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("ComboBox.background", WHITE);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("Table.background", WHITE);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.selectionBackground", new Color(218, 239, 231));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("TableHeader.background", new Color(239, 243, 243));
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
    }

    static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    static Font font(int style, int size) { return new Font(fontFamily, style, size); }

    private static void loadBundledFont() {
        try (InputStream fontFile = Theme.class.getResourceAsStream("/fonts/InterVariable.ttf")) {
            if (fontFile == null) return;
            Font inter = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(inter);
            fontFamily = inter.getFamily(Locale.ROOT);
        } catch (FontFormatException | IOException e) {
            System.err.println("Could not load the bundled Inter font: " + e.getMessage());
        }
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? MINT : LINE, 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        button.setBackground(primary ? MINT : WHITE);
        button.setForeground(primary ? NAV : INK);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static final class RoundedPanel extends JPanel {
        private final int radius;
        private final Color outline;

        RoundedPanel(int radius, Color background, Color outline) {
            this.radius = radius;
            this.outline = outline;
            setBackground(background);
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, radius, radius));
            if (outline != null) {
                g.setColor(outline);
                g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, radius, radius));
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
