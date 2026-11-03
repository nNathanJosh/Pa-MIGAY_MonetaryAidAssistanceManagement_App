package ph.pamigay.app;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

final class EntityDialog extends JDialog {
    private final EntityType type;
    private final Map<String, Object> existing;
    private final Map<String, List<Database.Option>> options;
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final JButton save = Theme.button("Save changes", true);
    private final JButton cancel = Theme.button("Cancel", false);

    EntityDialog(Window owner, EntityType type, Map<String, Object> existing,
                 Map<String, List<Database.Option>> options, Consumer<Map<String, Object>> onSave) {
        super(owner, (existing == null ? "Add " : "Edit ") + type.title(), ModalityType.APPLICATION_MODAL);
        this.type = type;
        this.existing = existing == null ? Map.of() : existing;
        this.options = options;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(590, 420));
        setContentPane(build());
        save.addActionListener(e -> {
            Map<String, Object> values = collect();
            if (values != null) onSave.accept(values);
        });
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    void setBusy(boolean busy) {
        save.setEnabled(!busy);
        cancel.setEnabled(!busy);
        save.setText(busy ? "Saving…" : "Save changes");
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(20, 22, 16, 22));
        root.setBackground(Theme.WHITE);
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(Theme.label((existing.isEmpty() ? "New " : "Update ") + type.title().toLowerCase(Locale.ROOT), 20, true, Theme.INK));
        heading.add(Box.createVerticalStrut(5));
        heading.add(Theme.label("Required fields are marked with *.", 12, false, Theme.MUTED));
        root.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int[] row = {0};
        switch (type) {
            case BENEFICIARY -> {
                text(form, row, "first_name", "First name *", "");
                text(form, row, "last_name", "Last name *", "");
                text(form, row, "contact_number", "Contact number *", "");
                choice(form, row, "category", "Category *", false, List.of("Senior Citizen", "Person with Disability", "Student", "Low-income Household"));
                choice(form, row, "barangay_id", "Barangay *", false, List.of());
            }
            case DISTRICT -> text(form, row, "district_name", "District name *", "");
            case BARANGAY -> {
                text(form, row, "barangay_name", "Barangay name *", "");
                choice(form, row, "district_id", "District *", false, List.of());
            }
            case CRISIS -> {
                text(form, row, "crisis_type", "Crisis type *", "e.g. Residential fire");
                text(form, row, "date_occurred", "Date occurred *", LocalDate.now().toString());
                hint(form, row, "Use yyyy-MM-dd, for example 2026-10-30.");
            }
            case STAFF -> {
                text(form, row, "first_name", "First name *", "");
                text(form, row, "last_name", "Last name *", "");
                choice(form, row, "role", "Role *", false, List.of("Administrator", "Program Coordinator", "Payout Clerk", "Field Officer"));
                text(form, row, "contact_info", "Contact information *", "Phone or email");
            }
            case PAYOUT -> {
                text(form, row, "payout_date", "Distribution date *", LocalDate.now().toString());
                text(form, row, "amount", "Budget amount *", "0.00");
                text(form, row, "distribution_location", "Distribution location *", "");
                text(form, row, "target_group", "Target group *", "");
                choice(form, row, "district_id", "District *", false, List.of());
                choice(form, row, "crisis_id", "Related crisis *", false, List.of());
                choice(form, row, "staff_id", "Responsible staff *", false, List.of());
                hint(form, row, "Use yyyy-MM-dd for the distribution date. Amounts must be greater than zero.");
            }
            case RECEIPT -> {
                choice(form, row, "payout_id", "Payout *", false, List.of());
                choice(form, row, "beneficiary_id", "Beneficiary *", false, List.of());
                text(form, row, "date_issued", "Date and time *", LocalDateTime.now().withSecond(0).withNano(0)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                text(form, row, "amount_received", "Amount received *", "0.00");
                choice(form, row, "status", "Status *", false, List.of("Claimed", "Pending", "Unclaimed"));
                hint(form, row, "Use yyyy-MM-dd HH:mm, for example 2026-10-30 14:30.");
            }
            case ANNOUNCEMENT -> {
                area(form, row, "content", "Announcement text *", 4);
                text(form, row, "date_posted", "Post date and time *", LocalDateTime.now().withSecond(0).withNano(0)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                choice(form, row, "platform", "Platform *", false, List.of("Facebook Page", "Website", "SMS", "Community notice"));
                choice(form, row, "payout_id", "Related payout *", false, List.of());
                hint(form, row, "Use yyyy-MM-dd HH:mm for the post date and time.");
            }
        }
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.WHITE);
        root.add(scroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel); actions.add(save);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private void text(JPanel form, int[] row, String key, String label, String placeholder) {
        JTextField field = new JTextField(28);
        Object value = existing.get(key);
        field.setText(value == null ? defaultValue(key) : value.toString());
        field.setToolTipText(label.replace(" *", "") + (placeholder.isBlank() ? "" : " · " + placeholder));
        fields.put(key, field);
        addField(form, row, label, field);
    }

    private String defaultValue(String key) {
        return switch (key) {
            case "payout_date", "date_occurred" -> LocalDate.now().toString();
            case "date_issued", "date_posted" -> LocalDateTime.now().withSecond(0).withNano(0)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            default -> "";
        };
    }

    private void area(JPanel form, int[] row, String key, String label, int lines) {
        JTextArea area = new JTextArea(lines, 34);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        Object value = existing.get(key);
        if (value != null) area.setText(value.toString());
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(420, lines * 24));
        fields.put(key, area);
        addField(form, row, label, scroll);
    }

    private void choice(JPanel form, int[] row, String key, String label, boolean editable, List<String> fallback) {
        JComboBox<Object> combo = new JComboBox<>();
        List<Database.Option> items = options.getOrDefault(key, List.of());
        for (Database.Option item : items) combo.addItem(item);
        if (items.isEmpty()) for (String item : fallback) combo.addItem(item);
        if (items.isEmpty() && !editable && fallback.isEmpty()) {
            combo.addItem("No related records available — create one first");
            combo.setEnabled(false);
        }
        combo.setEditable(editable);
        Object current = existing.get(key);
        if (current != null) {
            boolean selected = false;
            for (Database.Option item : items) if (Objects.equals(item.value(), current)) {
                combo.setSelectedItem(item); selected = true; break;
            }
            if (!selected) combo.setSelectedItem(current.toString());
        } else if (!items.isEmpty()) combo.setSelectedIndex(0);
        fields.put(key, combo);
        addField(form, row, label, combo);
    }

    private void hint(JPanel form, int[] row, String message) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 1; g.gridy = row[0]++; g.gridwidth = 2; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 10, 0); g.anchor = GridBagConstraints.WEST;
        form.add(Theme.label(message, 11, false, Theme.MUTED), g);
    }

    private void addField(JPanel form, int[] row, String label, JComponent field) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row[0]++; g.insets = new Insets(7, 0, 7, 10); g.anchor = GridBagConstraints.NORTHWEST;
        g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        form.add(Theme.label(label, 12, true, Theme.INK), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, g);
    }

    private Map<String, Object> collect() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (var entry : fields.entrySet()) {
            JComponent component = entry.getValue();
            Object value;
            if (component instanceof JTextField field) value = field.getText().trim();
            else if (component instanceof JTextArea area) value = area.getText().trim();
            else if (component instanceof JComboBox<?> combo) {
                boolean relatedId = entry.getKey().endsWith("_id");
                if (relatedId && !(combo.getSelectedItem() instanceof Database.Option)) {
                    JOptionPane.showMessageDialog(this,
                            "There are no related records available. Create the required record first, then return here.",
                            "Related record required", JOptionPane.WARNING_MESSAGE);
                    return null;
                }
                value = combo.getSelectedItem();
                if (value instanceof Database.Option option) value = option;
                else if (value != null) value = value.toString().trim();
            } else value = null;
            if (value == null || value.toString().isBlank()) {
                JOptionPane.showMessageDialog(this, "Complete the required field: " + entry.getKey().replace('_', ' '),
                        "Required field", JOptionPane.WARNING_MESSAGE);
                component.requestFocusInWindow();
                return null;
            }
            values.put(entry.getKey(), value);
        }
        try {
            if (type == EntityType.CRISIS) LocalDate.parse(values.get("date_occurred").toString());
            if (type == EntityType.PAYOUT) {
                LocalDate.parse(values.get("payout_date").toString());
                requirePositive(values.get("amount"), "Budget amount");
            }
            if (type == EntityType.RECEIPT) {
                LocalDateTime.parse(values.get("date_issued").toString().replace(' ', 'T'));
                requirePositive(values.get("amount_received"), "Amount received");
            }
            if (type == EntityType.ANNOUNCEMENT)
                LocalDateTime.parse(values.get("date_posted").toString().replace(' ', 'T'));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Check the date or amount format. Dates use yyyy-MM-dd; date and time use yyyy-MM-dd HH:mm; amounts must be greater than zero.",
                    "Check the values", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return values;
    }

    private static void requirePositive(Object value, String label) {
        if (new BigDecimal(value.toString()).signum() <= 0) throw new IllegalArgumentException(label + " must be greater than zero.");
    }
}
