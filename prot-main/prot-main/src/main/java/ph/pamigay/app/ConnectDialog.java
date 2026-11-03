package ph.pamigay.app;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

final class ConnectDialog extends JDialog {
    private final JTextField host = new JTextField(22);
    private final JTextField port = new JTextField(8);
    private final JTextField database = new JTextField(22);
    private final JTextField username = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final JTextArea status = new JTextArea("Connect to the MySQL database shown in your Workbench.", 3, 36);
    private Database connected;
    private boolean cancelled;

    private ConnectDialog(Window owner, DbConfig initial) {
        super(owner, "Connect to Pa-MIGAY", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setFont(Theme.font(Font.PLAIN, 12));
        status.setForeground(Theme.MUTED);
        host.setText(initial.host());
        port.setText(Integer.toString(initial.port()));
        database.setText(initial.database());
        username.setText(initial.username());
        setContentPane(build());
        pack();
        setLocationRelativeTo(owner);
    }

    static Database open(Window owner, DbConfig initial) {
        ConnectDialog dialog = new ConnectDialog(owner, initial);
        dialog.setVisible(true);
        return dialog.cancelled ? null : dialog.connected;
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(22, 24, 18, 24));
        root.setBackground(Theme.WHITE);
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(Theme.label("Connect your database", 21, true, Theme.INK));
        heading.add(Box.createVerticalStrut(6));
        heading.add(Theme.label("The app uses the existing pamigay_db tables and does not change the schema.", 12, false, Theme.MUTED));
        root.add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7, 0, 7, 9);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.weightx = 0;
        addField(fields, g, "MySQL host", host);
        addField(fields, g, "Port", port);
        addField(fields, g, "Database", database);
        addField(fields, g, "Username", username);
        addField(fields, g, "Password", password);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(fields, BorderLayout.CENTER);
        center.add(status, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JButton connect = Theme.button("Connect", true);
        JButton cancel = Theme.button("Cancel", false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel); actions.add(connect);
        root.add(actions, BorderLayout.SOUTH);
        connect.addActionListener(e -> connect(connect, cancel));
        cancel.addActionListener(e -> { cancelled = true; dispose(); });
        getRootPane().setDefaultButton(connect);
        return root;
    }

    private void addField(JPanel panel, GridBagConstraints g, String label, JComponent field) {
        JLabel name = Theme.label(label, 12, true, Theme.INK);
        g.gridy++; g.gridx = 0; g.weightx = 0.25; g.insets = new Insets(6, 0, 6, 12);
        panel.add(name, g);
        g.gridx = 1; g.weightx = 0.75; g.insets = new Insets(6, 0, 6, 0);
        panel.add(field, g);
    }

    private void connect(JButton connect, JButton cancel) {
        int portValue;
        try {
            portValue = Integer.parseInt(port.getText().trim());
            if (portValue < 1 || portValue > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            status.setText("Enter a valid port between 1 and 65535.");
            status.setForeground(new Color(174, 67, 57));
            return;
        }
        if (host.getText().isBlank() || database.getText().isBlank() || username.getText().isBlank()) {
            status.setText("Host, database, and username are required.");
            status.setForeground(new Color(174, 67, 57));
            return;
        }
        DbConfig config = new DbConfig(host.getText().trim(), portValue, database.getText().trim(),
                username.getText().trim(), new String(password.getPassword()));
        connect.setEnabled(false); cancel.setEnabled(false);
        status.setForeground(Theme.MUTED);
        status.setText("Connecting and checking the required tables…");
        new SwingWorker<Database, Void>() {
            @Override protected Database doInBackground() throws Exception {
                Database db = new Database(config);
                db.validateSchema();
                return db;
            }
            @Override protected void done() {
                try {
                    connected = get();
                    config.save();
                    cancelled = false;
                    dispose();
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    status.setForeground(new Color(174, 67, 57));
                    status.setText(errorMessage(cause));
                    connect.setEnabled(true); cancel.setEnabled(true);
                }
            }
        }.execute();
    }

    static String errorMessage(Throwable cause) {
        String message = cause instanceof SQLException ? cause.getMessage() : null;
        if (message != null && message.contains("does not match the Pa-MIGAY schema")) return message;
        if (message == null || message.isBlank()) message = cause.getMessage();
        return "Could not connect. Check that MySQL is running, the database exists, and this user can read and write it."
                + (message == null || message.isBlank() ? "" : "\n" + message);
    }
}
