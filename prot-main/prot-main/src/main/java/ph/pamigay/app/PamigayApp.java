package ph.pamigay.app;

import javax.swing.*;
import java.awt.*;

public final class PamigayApp {
    private PamigayApp() {}

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Theme.apply();
                Database database = ConnectDialog.open(null, DbConfig.load());
                if (database == null) return;
                new MainFrame(database).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Pa-MIGAY could not start.\n\n" + e.getMessage(),
                        "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
