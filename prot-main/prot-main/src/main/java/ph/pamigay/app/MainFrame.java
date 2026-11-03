package ph.pamigay.app;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class MainFrame extends JFrame {
    private Database db;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel headerTitle = new JPanel();
    private final JLabel pageTitle = Theme.label("Overview", 19, true, Theme.INK);
    private final JLabel pageSubtitle = Theme.label("A clear view of community assistance activity.", 12, false, Theme.MUTED);
    private final JLabel activityStatus = Theme.label("Ready", 11, false, Theme.MUTED);
    private final JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    private final Map<String, String> pageLabels = Map.of(
            "overview", "Overview", "beneficiaries", "Beneficiaries", "payouts", "Payouts",
            "claims", "Claims", "locations", "Locations", "crises", "Crisis records", "staff",
            "Staff", "announcements", "Announcements", "reports", "Reports");
    private String currentPage = "overview";
    private Refreshable currentRefresh;

    private interface Refreshable { void refresh(); }
    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }

    MainFrame(Database database) {
        super("Pa-MIGAY  -  Community Monetary Aid Assistance Management");
        this.db = database;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.max(620, Math.min(1350, screen.width - 40));
        int height = Math.max(400, Math.min(850, screen.height - 60));
        setMinimumSize(new Dimension(Math.min(1120, width), Math.min(700, height)));
        setSize(width, height);
        setLocationRelativeTo(null);
        setContentPane(build());
        navigate("overview");
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        main.add(buildTopbar(), BorderLayout.NORTH);
        body.setBackground(Theme.BG);
        main.add(body, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.BG);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(8, 24, 8, 24)));
        footer.add(activityStatus, BorderLayout.WEST);
        footer.add(Theme.label("Changes save to the connected MySQL database.", 11, false, Theme.MUTED), BorderLayout.EAST);
        main.add(footer, BorderLayout.SOUTH);
        root.add(main, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.WHITE);
        JPanel brandRow = new JPanel(new BorderLayout());
        brandRow.setBackground(Theme.WHITE);
        brandRow.setBorder(new EmptyBorder(11, 22, 10, 20));
        brandRow.add(Theme.label("Pa-MIGAY", 18, true, Theme.INK), BorderLayout.WEST);
        JButton settings = Theme.button("Database settings", false);
        settings.addActionListener(e -> changeDatabase());
        brandRow.add(settings, BorderLayout.EAST);
        header.add(brandRow, BorderLayout.NORTH);

        navigation.setBackground(Theme.WHITE);
        navigation.setBorder(new EmptyBorder(0, 18, 0, 18));
        addNav("Overview", "overview");
        addNav("Beneficiaries", "beneficiaries");
        addNav("Payouts", "payouts");
        addNav("Claims", "claims");
        addNav("Locations", "locations");
        addNav("Crisis records", "crises");
        addNav("Staff", "staff");
        addNav("Announcements", "announcements");
        addNav("Reports", "reports");
        JPanel centeredNav = new JPanel(new GridBagLayout());
        centeredNav.setBackground(Theme.WHITE);
        centeredNav.add(navigation);
        JScrollPane navScroll = new JScrollPane(centeredNav,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        navScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.LINE));
        navScroll.setPreferredSize(new Dimension(100, 46));
        navScroll.getViewport().setBackground(Theme.WHITE);
        navScroll.getHorizontalScrollBar().setUnitIncrement(18);
        header.add(navScroll, BorderLayout.CENTER);
        return header;
    }

    private void addNav(String title, String key) {
        JButton button = new JButton(title);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setPreferredSize(new Dimension(navWidth(title), 38));
        button.setFocusPainted(false);
        button.setFont(Theme.font(Font.PLAIN, 12));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE, 1, true),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
        button.setForeground(Theme.MUTED);
        button.setBackground(Theme.WHITE);
        button.setOpaque(true);
        button.addActionListener(e -> navigate(key));
        button.putClientProperty("navKey", key);
        navigation.add(button);
    }

    private int navWidth(String title) { return Math.max(78, title.length() * 8 + 24); }

    private JComponent buildTopbar() {
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setBackground(Theme.BG);
        top.setBorder(new EmptyBorder(20, 25, 8, 22));
        headerTitle.setOpaque(false);
        headerTitle.setLayout(new BoxLayout(headerTitle, BoxLayout.Y_AXIS));
        pageTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        pageSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerTitle.add(pageTitle); headerTitle.add(Box.createVerticalStrut(2)); headerTitle.add(pageSubtitle);
        top.add(headerTitle, BorderLayout.WEST);
        return top;
    }

    private void navigate(String key) {
        currentPage = key;
        for (Component component : navigation.getComponents()) {
            if (component instanceof JButton button) {
                boolean active = key.equals(button.getClientProperty("navKey"));
                button.setBackground(active ? new Color(180, 193, 199) : Theme.WHITE);
                button.setForeground(active ? Theme.INK : Theme.MUTED);
                button.setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 12));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(active ? new Color(110, 164, 212) : Theme.LINE, 1, true),
                        BorderFactory.createEmptyBorder(0, 10, 0, 10)));
            }
        }
        pageTitle.setText(pageLabels.getOrDefault(key, "Overview"));
        pageSubtitle.setText(switch (key) {
            case "overview" -> "A clear view of community assistance activity.";
            case "reports" -> "Read-only summaries for planning and accountability.";
            case "locations" -> "Keep districts and barangays consistent across records.";
            default -> "Find, review, and update " + pageLabels.getOrDefault(key, "records").toLowerCase(Locale.ROOT) + ".";
        });
        body.removeAll();
        JComponent page;
        switch (key) {
            case "overview" -> page = new DashboardPage();
            case "beneficiaries" -> page = entityPage(EntityType.BENEFICIARY);
            case "payouts" -> page = entityPage(EntityType.PAYOUT);
            case "claims" -> page = entityPage(EntityType.RECEIPT);
            case "locations" -> page = new LocationsPage();
            case "crises" -> page = entityPage(EntityType.CRISIS);
            case "staff" -> page = entityPage(EntityType.STAFF);
            case "announcements" -> page = entityPage(EntityType.ANNOUNCEMENT);
            case "reports" -> page = new ReportsPage();
            default -> page = new DashboardPage();
        }
        currentRefresh = page instanceof Refreshable r ? r : null;
        body.add(page, BorderLayout.CENTER);
        body.revalidate(); body.repaint();
        navigation.revalidate();
    }

    private EntityPage entityPage(EntityType type) { return new EntityPage(type); }

    private void changeDatabase() {
        Database next = ConnectDialog.open(this, DbConfig.load());
        if (next != null) { db = next; navigate(currentPage); }
    }

    private <T> void async(String workLabel, ThrowingSupplier<T> task, Consumer<T> success) {
        async(workLabel, task, success, null);
    }

    private <T> void async(String workLabel, ThrowingSupplier<T> task, Consumer<T> success, Consumer<Throwable> failure) {
        activityStatus.setText(workLabel + "…");
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.get(); }
            @Override protected void done() {
                try {
                    T value = get();
                    activityStatus.setText("Ready");
                    if (success != null) success.accept(value);
                } catch (Exception e) {
                    activityStatus.setText("Action failed");
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (failure != null) failure.accept(cause);
                    else {
                        String message = cause.getMessage();
                        if (message == null || message.isBlank()) message = ConnectDialog.errorMessage(cause);
                        JOptionPane.showMessageDialog(MainFrame.this, message, "Pa-MIGAY", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void openForm(EntityType type, Object id, Runnable afterSave) {
        async("Loading form", () -> {
            Map<String, Object> existing = id == null ? null : db.find(type, id);
            Map<String, List<Database.Option>> options = db.formOptions(type);
            return new Object[]{existing, options};
        }, result -> {
            @SuppressWarnings("unchecked") Map<String, Object> existing = (Map<String, Object>) result[0];
            @SuppressWarnings("unchecked") Map<String, List<Database.Option>> options = (Map<String, List<Database.Option>>) result[1];
            EntityDialog[] holder = new EntityDialog[1];
            EntityDialog dialog = new EntityDialog(this, type, existing, options,
                    values -> attemptSave(type, values, id, holder[0], afterSave));
            holder[0] = dialog;
            dialog.setVisible(true);
        });
    }

    private void attemptSave(EntityType type, Map<String, Object> values, Object id, EntityDialog dialog, Runnable afterSave) {
        if (dialog != null) dialog.setBusy(true);
        async("Checking record", () -> {
            db.validateUniqueKeys(type, values, id);
            return db.duplicateWarnings(type, values, id);
        }, warnings -> {
            if (!warnings.isEmpty()) {
                int choice = JOptionPane.showConfirmDialog(this,
                        String.join("\n", warnings) + "\n\nSave this record anyway?",
                        "Possible duplicate", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) { if (dialog != null) dialog.setBusy(false); return; }
            }
            async("Saving record", () -> { db.save(type, values, id); return true; }, ignored -> {
                if (dialog != null) dialog.dispose();
                JOptionPane.showMessageDialog(this, singular(type).substring(0, 1).toUpperCase(Locale.ROOT)
                        + singular(type).substring(1) + " saved successfully.",
                        "Saved", JOptionPane.INFORMATION_MESSAGE);
                afterSave.run();
            }, error -> { if (dialog != null) dialog.setBusy(false); showError(error); });
        }, error -> { if (dialog != null) dialog.setBusy(false); showError(error); });
    }

    private void showError(Throwable cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = ConnectDialog.errorMessage(cause);
        JOptionPane.showMessageDialog(this, message, "Pa-MIGAY", JOptionPane.ERROR_MESSAGE);
    }

    private final class EntityPage extends JPanel implements Refreshable {
        private final EntityType type;
        private final DefaultTableModel model = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        private final JTable table = styledTable(model);
        private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        private final JTextField search = new JTextField(22);
        private final JLabel count = Theme.label("", 11, false, Theme.MUTED);
        private final JButton edit = Theme.button("Edit selected", false);
        private final JButton remove;

        private String typeSafeDeleteLabel() { return type == EntityType.RECEIPT ? "Remove duplicate" : "Delete"; }

        EntityPage(EntityType type) {
            this(type, false);
        }

        EntityPage(EntityType type, boolean compact) {
            super(new BorderLayout(0, 12));
            this.type = type;
            this.remove = Theme.button(typeSafeDeleteLabel(), false);
            setBackground(Theme.BG);
            setBorder(compact ? new EmptyBorder(8, 24, 16, 24) : new EmptyBorder(22, 24, 16, 24));
            JPanel toolbar = new JPanel(new BorderLayout(10, 0));
            toolbar.setOpaque(false);
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); left.setOpaque(false);
            search.setToolTipText("Search in visible columns");
            search.setPreferredSize(new Dimension(230, 34));
            left.add(Theme.label("Search", 12, true, Theme.INK)); left.add(search);
            JButton refresh = Theme.button("Refresh", false);
            refresh.addActionListener(e -> refresh());
            JButton export = Theme.button("Export CSV", false);
            export.addActionListener(e -> exportTable(table, type.title()));
            left.add(refresh);
            if (type != EntityType.BENEFICIARY && type != EntityType.RECEIPT && type != EntityType.STAFF) left.add(export);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); right.setOpaque(false);
            right.add(edit); right.add(remove);
            JButton add = Theme.button("+  Add " + singular(type), true);
            right.add(add);
            toolbar.add(left, BorderLayout.WEST); toolbar.add(right, BorderLayout.EAST);
            add(toolbar, BorderLayout.NORTH);
            table.setRowSorter(sorter);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(e -> {
                boolean selected = table.getSelectedRow() >= 0;
                edit.setEnabled(selected); remove.setEnabled(selected);
            });
            edit.setEnabled(false); remove.setEnabled(false);
            edit.addActionListener(e -> editSelected());
            remove.addActionListener(e -> deleteSelected());
            add.addActionListener(e -> openForm(type, null, this::refresh));
            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) editSelected();
                }
            });
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
            scroll.getViewport().setBackground(Theme.WHITE);
            add(scroll, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false);
            bottom.add(count, BorderLayout.WEST);
            bottom.add(Theme.label("Double-click a row to edit it.", 11, false, Theme.MUTED), BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);
            search.getDocument().addDocumentListener(new DocumentListener() {
                private void update() {
                    String text = search.getText().trim();
                    sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                }
                public void insertUpdate(DocumentEvent e) { update(); }
                public void removeUpdate(DocumentEvent e) { update(); }
                public void changedUpdate(DocumentEvent e) { update(); }
            });
            refresh();
        }

        @Override public void refresh() {
            async("Loading " + type.title().toLowerCase(Locale.ROOT), () -> db.rows(type), data -> {
                model.setDataVector(data.rows().toArray(Object[][]::new), data.columns().toArray());
                table.setRowSorter(sorter);
                count.setText(data.rows().size() + (data.rows().size() == 1 ? " record" : " records"));
                edit.setEnabled(false); remove.setEnabled(false);
            });
        }

        private Object selectedId() {
            int view = table.getSelectedRow();
            return view < 0 ? null : table.getValueAt(view, 0);
        }

        private void editSelected() {
            Object id = selectedId(); if (id != null) openForm(type, id, this::refresh);
        }

        private void deleteSelected() {
            Object id = selectedId(); if (id == null) return;
            String note = type == EntityType.RECEIPT
                    ? "Remove this receipt only if it is an erroneous duplicate and your policy permits it. This cannot be undone."
                    : "The app will prevent removal while other records still depend on this record. This cannot be undone.";
            int confirm = JOptionPane.showConfirmDialog(this, note, type == EntityType.RECEIPT ? "Remove duplicate claim?" : "Delete record?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            async("Deleting record", () -> { db.delete(type, id); return true; }, ignored -> {
                JOptionPane.showMessageDialog(this, "Record deleted.", "Deleted", JOptionPane.INFORMATION_MESSAGE);
                refresh();
            });
        }
    }

    private final class LocationsPage extends JPanel {
        private final CardLayout cardLayout = new CardLayout();
        private final JPanel views = new JPanel(cardLayout);
        private final JButton barangays = locationTab("Barangays", true);
        private final JButton districts = locationTab("Districts", false);

        LocationsPage() {
            super(new BorderLayout(0, 6));
            setBackground(Theme.BG);
            JPanel switcher = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            switcher.setOpaque(false);
            switcher.setBorder(new EmptyBorder(12, 24, 0, 24));
            switcher.add(barangays);
            switcher.add(districts);
            views.setBackground(Theme.BG);
            views.add(new EntityPage(EntityType.BARANGAY, true), "barangays");
            views.add(new EntityPage(EntityType.DISTRICT, true), "districts");
            barangays.addActionListener(e -> show("barangays"));
            districts.addActionListener(e -> show("districts"));
            add(switcher, BorderLayout.NORTH);
            add(views, BorderLayout.CENTER);
        }

        private JButton locationTab(String label, boolean selected) {
            JButton button = new JButton(label);
            button.setFocusPainted(false);
            button.setOpaque(true);
            button.setBackground(selected ? new Color(241, 247, 253) : Theme.WHITE);
            button.setForeground(selected ? Theme.INK : Theme.MUTED);
            button.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 12));
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? new Color(110, 164, 212) : Theme.LINE, 1, true),
                    BorderFactory.createEmptyBorder(7, 14, 7, 14)));
            return button;
        }

        private void show(String name) {
            boolean isBarangay = name.equals("barangays");
            style(barangays, isBarangay);
            style(districts, !isBarangay);
            cardLayout.show(views, name);
        }

        private void style(JButton button, boolean selected) {
            button.setBackground(selected ? new Color(241, 247, 253) : Theme.WHITE);
            button.setForeground(selected ? Theme.INK : Theme.MUTED);
            button.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 12));
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? new Color(110, 164, 212) : Theme.LINE, 1, true),
                    BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        }
    }

    private final class DashboardPage extends JPanel implements Refreshable {
        private final JPanel metrics = new JPanel(new GridLayout(1, 4, 12, 12));
        private final DefaultTableModel recentModel = readOnlyModel();
        private final JTable recentTable = styledTable(recentModel);
        DashboardPage() {
            super(new BorderLayout(0, 16)); setBackground(Theme.BG); setBorder(new EmptyBorder(22, 24, 20, 24));
            JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
            JPanel title = new JPanel(); title.setOpaque(false); title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
            title.add(Theme.label("Your assistance activity", 23, true, Theme.INK));
            title.add(Box.createVerticalStrut(4));
            title.add(Theme.label("A simple snapshot of beneficiaries, distributions, and recorded claims.", 12, false, Theme.MUTED));
            top.add(title, BorderLayout.WEST);
            JButton quick = Theme.button("+  Schedule payout", true);
            quick.addActionListener(e -> openForm(EntityType.PAYOUT, null, this::refresh));
            top.add(quick, BorderLayout.EAST);
            JPanel inner = new JPanel(new BorderLayout(0, 18)); inner.setOpaque(false);
            metrics.setOpaque(false); inner.add(metrics, BorderLayout.NORTH);
            Theme.RoundedPanel recentPanel = new Theme.RoundedPanel(10, Theme.WHITE, Theme.LINE);
            recentPanel.setLayout(new BorderLayout(0, 10));
            recentPanel.setBorder(new EmptyBorder(15, 16, 12, 16));
            JPanel recentHeader = new JPanel(new BorderLayout()); recentHeader.setOpaque(false);
            recentHeader.add(Theme.label("Recent distributions", 15, true, Theme.INK), BorderLayout.WEST);
            JButton view = Theme.button("View all payouts", false); view.addActionListener(e -> navigate("payouts"));
            recentHeader.add(view, BorderLayout.EAST);
            recentPanel.add(recentHeader, BorderLayout.NORTH);
            recentTable.setFillsViewportHeight(true);
            JScrollPane scroll = new JScrollPane(recentTable); scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
            scroll.getViewport().setBackground(Theme.WHITE);
            recentPanel.add(scroll, BorderLayout.CENTER);
            inner.add(recentPanel, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); actions.setOpaque(false);
            JButton beneficiary = Theme.button("+  Add beneficiary", false); beneficiary.addActionListener(e -> openForm(EntityType.BENEFICIARY, null, this::refresh));
            JButton claim = Theme.button("+  Record claim", false); claim.addActionListener(e -> openForm(EntityType.RECEIPT, null, this::refresh));
            JButton reports = Theme.button("Open reports", false); reports.addActionListener(e -> navigate("reports"));
            actions.add(beneficiary); actions.add(claim); actions.add(reports);
            inner.add(actions, BorderLayout.SOUTH);
            JPanel content = new JPanel(new BorderLayout(0, 18));
            content.setOpaque(false);
            content.add(top, BorderLayout.NORTH);
            content.add(inner, BorderLayout.CENTER);
            JScrollPane contentScroll = new JScrollPane(content,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            contentScroll.setBorder(BorderFactory.createEmptyBorder());
            contentScroll.getViewport().setBackground(Theme.BG);
            contentScroll.getVerticalScrollBar().setUnitIncrement(18);
            add(contentScroll, BorderLayout.CENTER);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) { updateMetricColumns(); }
            });
            refresh();
        }

        private void updateMetricColumns() {
            int columns = getWidth() < 980 ? 2 : 4;
            GridLayout layout = (GridLayout) metrics.getLayout();
            if (layout.getColumns() != columns) {
                metrics.setLayout(new GridLayout(0, columns, 12, 12));
                metrics.revalidate();
            }
        }

        @Override public void refresh() {
            async("Refreshing overview", db::dashboard, data -> {
                metrics.removeAll();
                metrics.add(metric("Beneficiaries", String.format("%,d", data.beneficiaries()), "Registered people and households"));
                metrics.add(metric("Payouts", String.format("%,d", data.payouts()), data.upcoming() + " upcoming or active"));
                metrics.add(metric("Payout budget", php(data.totalAmount()), "Total recorded payout amounts"));
                double rate = data.receipts() == 0 ? 0 : (100.0 * data.claimed() / data.receipts());
                metrics.add(metric("Claims recorded", String.format("%,d", data.claimed()), String.format("%.0f%% of %d receipts marked claimed", rate, data.receipts())));
                metrics.revalidate(); metrics.repaint();
                recentModel.setDataVector(data.recentPayouts().toArray(Object[][]::new), new Object[]{"ID", "Date", "Location", "District", "Amount"});
            });
        }

        private JPanel metric(String label, String value, String note) {
            Theme.RoundedPanel card = new Theme.RoundedPanel(10, Theme.WHITE, Theme.LINE);
            card.setLayout(new BorderLayout(0, 8));
            card.setBorder(new EmptyBorder(15, 16, 15, 16));
            card.add(Theme.label(label.toUpperCase(Locale.ROOT), 10, true, Theme.MUTED), BorderLayout.NORTH);
            JPanel middle = new JPanel(new BorderLayout()); middle.setOpaque(false);
            middle.add(Theme.label(value, 24, true, Theme.INK), BorderLayout.CENTER);
            card.add(middle, BorderLayout.CENTER);
            card.add(Theme.label(note, 11, false, Theme.MUTED), BorderLayout.SOUTH);
            return card;
        }
    }

    private final class ReportsPage extends JPanel implements Refreshable {
        private final JComboBox<Database.ReportType> reportType = new JComboBox<>(Database.ReportType.values());
        private final JTextField from = new JTextField(10);
        private final JTextField to = new JTextField(10);
        private final DefaultTableModel model = readOnlyModel();
        private final JTable table = styledTable(model);
        private final JLabel status = Theme.label("Choose a report and run it to load results.", 11, false, Theme.MUTED);
        ReportsPage() {
            super(new BorderLayout(0, 12)); setBackground(Theme.BG); setBorder(new EmptyBorder(22, 24, 20, 24));
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); toolbar.setOpaque(false);
            toolbar.add(Theme.label("Report", 12, true, Theme.INK)); toolbar.add(reportType);
            toolbar.add(Theme.label("From", 12, true, Theme.INK)); from.setToolTipText("yyyy-MM-dd"); toolbar.add(from);
            toolbar.add(Theme.label("To", 12, true, Theme.INK)); to.setToolTipText("yyyy-MM-dd"); toolbar.add(to);
            JButton run = Theme.button("Run report", true); run.addActionListener(e -> loadReport()); toolbar.add(run);
            JButton export = Theme.button("Export CSV", false); export.addActionListener(e -> exportTable(table, "Pa-MIGAY report")); toolbar.add(export);
            JPanel head = new JPanel(new BorderLayout(0, 6)); head.setOpaque(false);
            head.add(toolbar, BorderLayout.NORTH);
            head.add(Theme.label("Date filters are optional. Beneficiary distribution is a current snapshot.", 11, false, Theme.MUTED), BorderLayout.SOUTH);
            add(head, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(table); scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE)); scroll.getViewport().setBackground(Theme.WHITE);
            add(scroll, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
            refresh();
        }
        @Override public void refresh() { loadReport(); }
        private void loadReport() {
            LocalDate start, end;
            try {
                start = from.getText().isBlank() ? null : LocalDate.parse(from.getText().trim());
                end = to.getText().isBlank() ? null : LocalDate.parse(to.getText().trim());
                if (start != null && end != null && end.isBefore(start)) throw new DateTimeParseException("range", "", 0);
            } catch (DateTimeParseException e) {
                JOptionPane.showMessageDialog(this, "Enter dates as yyyy-MM-dd. The end date must be on or after the start date.",
                        "Check date range", JOptionPane.WARNING_MESSAGE); return;
            }
            Database.ReportType selected = (Database.ReportType) reportType.getSelectedItem();
            async("Running report", () -> db.report(selected, start, end), data -> {
                model.setDataVector(data.rows().toArray(Object[][]::new), data.columns().toArray());
                status.setText(data.rows().size() + " result rows · read-only report");
            });
        }
    }

    private static JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(32); table.setShowVerticalLines(false); table.setShowHorizontalLines(true);
        table.setGridColor(Theme.LINE); table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(new Color(218, 239, 231)); table.setSelectionForeground(Theme.INK);
        table.setFillsViewportHeight(true); table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getTableHeader().setReorderingAllowed(false); table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        return table;
    }

    private static DefaultTableModel readOnlyModel() {
        return new DefaultTableModel() { @Override public boolean isCellEditable(int row, int column) { return false; } };
    }

    private void exportTable(JTable table, String title) {
        if (table.getColumnCount() == 0) { JOptionPane.showMessageDialog(this, "Run or refresh the list before exporting."); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export " + title + " as CSV");
        chooser.setSelectedFile(new java.io.File(title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
        final java.io.File target = file;
        try {
            StringBuilder csv = new StringBuilder("\uFEFF");
            for (int c = 0; c < table.getColumnCount(); c++) {
                if (c > 0) csv.append(','); csv.append(csvCell(table.getColumnName(c)));
            }
            csv.append("\r\n");
            for (int r = 0; r < table.getRowCount(); r++) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    if (c > 0) csv.append(',');
                    Object value = table.getValueAt(r, c);
                    csv.append(csvCell(value == null ? "" : value.toString()));
                }
                csv.append("\r\n");
            }
            Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Exported " + table.getRowCount() + " rows to:\n" + target,
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save the CSV file.\n" + e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csvCell(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String singular(EntityType type) { return switch (type) {
        case BENEFICIARY -> "beneficiary"; case DISTRICT -> "district"; case BARANGAY -> "barangay"; case CRISIS -> "crisis";
        case STAFF -> "staff member"; case PAYOUT -> "payout"; case RECEIPT -> "claim"; case ANNOUNCEMENT -> "announcement";
    }; }
    private static String php(java.math.BigDecimal amount) {
        return "₱" + java.text.NumberFormat.getNumberInstance(Locale.US).format(amount == null ? java.math.BigDecimal.ZERO : amount);
    }
}
