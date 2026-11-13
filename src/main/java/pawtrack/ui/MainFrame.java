package pawtrack.ui;

import pawtrack.api.PetImageServiceFactory;
import pawtrack.dao.AdopterDao;
import pawtrack.dao.AdoptionRecordDao;
import pawtrack.dao.AnimalDao;
import pawtrack.dao.ClassificationDao;
import pawtrack.dao.Database;
import pawtrack.dao.WaitlistDao;
import pawtrack.model.Adopter;
import pawtrack.model.AdoptionRecord;
import pawtrack.model.Animal;
import pawtrack.model.AnimalStatus;
import pawtrack.model.ClassificationNode;
import pawtrack.model.StatusChange;
import pawtrack.service.AnalyticsReport;
import pawtrack.service.AnalyticsService;
import pawtrack.service.ClassificationService;
import pawtrack.service.CsvImportResult;
import pawtrack.service.CsvImportService;
import pawtrack.service.ShelterSeeder;
import pawtrack.service.StatusHistoryService;
import pawtrack.service.WaitlistService;


import pawtrack.model.Role;
import pawtrack.model.User;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import java.awt.CardLayout;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Main application window (GUI). Connects to Main (entry point), Database, DAOs,
 * services, and workers. Contains toolbar, animal table, analytics, activity log.
 */
public final class MainFrame extends JFrame {

    private static final String ALL_CLASSIFICATIONS = "<All animals>";
    // Private helper for MainFrame GUI logic
    private static final Dimension IMAGE_SIZE = new Dimension(360, 360);
    private static final Color PANEL_BLUE = new Color(0xAFD3F2);
    private static final Color TOOLBAR_BLUE = new Color(0x9FC5E8);
    private static final Color TAB_COLOR = new Color(0x1F4366);
    private static final Color TAB_SELECTED_COLOR = new Color(0x2B5B8A);
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(MainFrame.class.getName());

    private final Database database;
    private final AnimalDao animalDao;
    private final AdopterDao adopterDao;
    private final AdoptionRecordDao recordDao;
    private final ClassificationDao classificationDao;
    private final WaitlistDao waitlistDao;

    private ClassificationService classification;
    // Private helper for MainFrame GUI logic
    private final WaitlistService waitlist = new WaitlistService();
    // Private helper for MainFrame GUI logic
    private final StatusHistoryService history = new StatusHistoryService();
    // Private helper for MainFrame GUI logic
    private final AnalyticsService analytics = new AnalyticsService();

    // Private helper for MainFrame GUI logic
    private final AnimalTableModel tableModel = new AnimalTableModel(waitlist);
    // Private helper for MainFrame GUI logic
    private final JTable animalTable = new JTable(tableModel);
    // Private helper for MainFrame GUI logic
    private final JTextArea logArea = new JTextArea();
    private CardLayout cardLayout;
    private JPanel contentPanel;
    private final java.util.List<RoundedUi.RoundedButton> tabButtons = new java.util.ArrayList<>();
    private final java.util.List<String> tabNames = new java.util.ArrayList<>();
    private JPanel toolbar;
    private JPanel imagePanel;
    private boolean toolbarMinimized = false;
    private javax.swing.JDialog statusPopup;
    private final User user;
    private Runnable onLogout;
    // Private helper for MainFrame GUI logic
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    // Private helper for MainFrame GUI logic
    private final JLabel statusLabel = new JLabel("Ready");
    // Private helper for MainFrame GUI logic
    private final JComboBox<String> classificationFilter = new RoundedUi.RoundedComboBox(18);
    // Private helper for MainFrame GUI logic
    private final JLabel imageLabel = new JLabel();
    // Private helper for MainFrame GUI logic
    private final JLabel imageInfoLabel = new JLabel(" ");
    // Private helper for MainFrame GUI logic
    private final AnalyticsPanel analyticsPanel = new AnalyticsPanel();

    public MainFrame(User user, Database database) throws SQLException {
        super("Pet Shelter & Adoption Tracker");
        this.user = user;
        this.database = database;
        this.animalDao = new AnimalDao(database);
        this.adopterDao = new AdopterDao(database);
        this.recordDao = new AdoptionRecordDao(database);
        this.classificationDao = new ClassificationDao(database);
        this.waitlistDao = new WaitlistDao(database);

        this.classification = ClassificationService.loadOrSeed(classificationDao);
        ShelterSeeder.seedIfEmpty(database, classification);
        loadWaitlists();

        buildUi();
        populateClassificationFilter();
        refreshAnimals();
        refreshAnalytics();

        log("Database ready");
        log(String.format("Classification tree loaded: %d nodes, depth %d",
                classification.nodeCount(), classification.depth()));
    }

    /** Sets the callback to run when the user logs out. */
    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    // ------------------------------------------------------------------ UI

    // Private helper for MainFrame GUI logic
    private void buildUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        setMinimumSize(new Dimension(900, 600));
        java.awt.Image icon = Icons.appIcon();
        if (icon != null) {
            setIconImage(icon);
        }
        getContentPane().setBackground(PANEL_BLUE);

        add(buildToolbar(), BorderLayout.WEST);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(PANEL_BLUE);
        addTabButton("Animals", buildAnimalTab());
        analyticsPanel.setBackground(PANEL_BLUE);
        analyticsPanel.setOpaque(true);
        if (user.getRole() == Role.STAFF) {
            addTabButton("Shelter Analytics", analyticsPanel);
        }
        if (user.getRole() == Role.STAFF) {
            logArea.setBackground(PANEL_BLUE);
            logArea.setOpaque(true);
            JScrollPane activityScroll = new JScrollPane(logArea);
            activityScroll.getViewport().setBackground(PANEL_BLUE);
            styleScrollBar(activityScroll.getVerticalScrollBar());
            addTabButton("Activity Log", activityScroll);
        }
        add(contentPanel, BorderLayout.CENTER);
        add(buildTabBar(), BorderLayout.NORTH);
        if (!tabNames.isEmpty()) {
            selectTab(tabNames.get(0));
        }

        add(buildImagePanel(), BorderLayout.EAST);
        add(statusLabel, BorderLayout.SOUTH);

        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setPreferredSize(new Dimension(160, 22));
        progressBar.setMinimumSize(new Dimension(120, 22));

        analyticsPanel.setRefreshAction(this::refreshAnalytics);

        logArea.setEditable(false);
        animalTable.setFillsViewportHeight(true);
        animalTable.setBackground(PANEL_BLUE);
        animalTable.setGridColor(new Color(0x8FB8DA));
        animalTable.getTableHeader().setOpaque(true);
        animalTable.getTableHeader().setBackground(new Color(0x2B5B8A));
        animalTable.getTableHeader().setForeground(Color.WHITE);
        animalTable.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                javax.swing.JLabel c = (javax.swing.JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                c.setOpaque(true);
                c.setBackground(new Color(0x2B5B8A));
                c.setForeground(Color.WHITE);
                return c;
            }
        });
        animalTable.setAutoCreateRowSorter(true);
        configureStatusFilter();
        animalTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelectedAnimalInfo();
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                try {
                    database.close();
                } catch (SQLException failure) {
                    // nothing useful to do while shutting down
                    MainFrame.LOGGER.log(java.util.logging.Level.WARNING, "Failed to close database", failure);
                }
            }
        });

        setSize(1200, 780);
        setLocationRelativeTo(null);
    }

    // Private helper for MainFrame GUI logic
    private void addTabButton(String title, java.awt.Component content) {
        RoundedUi.RoundedButton btn = new RoundedUi.RoundedButton(title, 18, TAB_COLOR);
        btn.setPreferredSize(new Dimension(170, 40));
        btn.setMaximumSize(new Dimension(170, 40));
        btn.addActionListener(e -> selectTab(title));
        contentPanel.add(content, title);
        tabButtons.add(btn);
        tabNames.add(title);
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildTabBar() {
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 8));
        bar.setOpaque(false);
        for (RoundedUi.RoundedButton b : tabButtons) {
            bar.add(b);
        }
        return bar;
    }

    // Private helper for MainFrame GUI logic
    private void selectTab(String title) {
        cardLayout.show(contentPanel, title);
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).setBaseColor(tabNames.get(i).equals(title) ? TAB_SELECTED_COLOR : TAB_COLOR);
        }
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildToolbar() {
        toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        toolbar.setAlignmentX(Component.CENTER_ALIGNMENT);
        toolbar.setBackground(TOOLBAR_BLUE);
        toolbar.setOpaque(true);
        toolbar.setBorder(BorderFactory.createTitledBorder(
                new RoundedUi.RoundedBorder(22, new Color(0x8FB8DA), 1), "Controls"));

        JButton toggle = new RoundedUi.RoundedButton("◄", 18, new Color(0x5B8AB0));
        toggle.setPreferredSize(new Dimension(40, 40));
        toggle.setMinimumSize(new Dimension(40, 40));
        toggle.setMaximumSize(new Dimension(40, 40));
        toggle.setAlignmentX(Component.RIGHT_ALIGNMENT);
        toggle.addActionListener(event -> {
            toolbarMinimized = !toolbarMinimized;
            toggle.setText(toolbarMinimized ? "►" : "◄");
            applyToolbarMinimize();
        });
        toolbar.add(toggle);
        toolbar.add(Box.createVerticalStrut(8));

        JButton logoutButton = new RoundedUi.RoundedButton("Logout", 18, new Color(0x8A2B2B));
        logoutButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutButton.setPreferredSize(new Dimension(240, 40));
        logoutButton.setMinimumSize(new Dimension(240, 40));
        logoutButton.setMaximumSize(new Dimension(240, 40));
        logoutButton.addActionListener(event -> {
            if (onLogout != null) {
                onLogout.run();
            }
        });
        toolbar.add(logoutButton);
        toolbar.add(Box.createVerticalStrut(16));

        toolbar.add(button("List / Refresh Animals", this::onRefreshAnimals));
        toolbar.add(Box.createVerticalStrut(16));
        toolbar.add(button("Filter by Classification", this::onFilterByClassification));
        toolbar.add(Box.createVerticalStrut(16));
        if (user.getRole() == Role.STAFF) {
            toolbar.add(button("Import Intake History (CSV)", this::onImportCsv));
            toolbar.add(Box.createVerticalStrut(16));
        }
        toolbar.add(button("Record Interest", this::onRecordInterest));
        toolbar.add(Box.createVerticalStrut(16));
        if (user.getRole() == Role.STAFF) {
            toolbar.add(button("Mark Adopted", this::onMarkAdopted));
            toolbar.add(Box.createVerticalStrut(16));
            toolbar.add(button("Undo Last Status Change", this::onUndo));
            toolbar.add(Box.createVerticalStrut(16));
        }
        toolbar.add(button("Fetch Sample Pet Image", this::onFetchImage));
        toolbar.add(Box.createVerticalStrut(16));
        classificationFilter.setPrototypeDisplayValue("Dog > Sporting Group > Retriever");
        classificationFilter.setAlignmentX(Component.CENTER_ALIGNMENT);
        classificationFilter.setPreferredSize(new Dimension(240, 40));
        classificationFilter.setMinimumSize(new Dimension(240, 40));
        classificationFilter.setMaximumSize(new Dimension(240, 40));
        classificationFilter.setBackground(new Color(0x2B5B8A));
        classificationFilter.setForeground(Color.BLACK);
        classificationFilter.setOpaque(true);
        classificationFilter.setComponentOrientation(java.awt.ComponentOrientation.LEFT_TO_RIGHT);
        classificationFilter.setBorder(new RoundedUi.RoundedBorder(18, new Color(0x2B5B8A), 1));
        classificationFilter.setRenderer(new javax.swing.DefaultListCellRenderer() {
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                javax.swing.JLabel c = (javax.swing.JLabel) super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                c.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                if (index < 0) {
                    // Closed (selected) state: transparent so the blue fill shows.
                    c.setOpaque(false);
                    c.setForeground(Color.BLACK);
                } else if (isSelected) {
                    // Hovered / selected item: vivid blue text on a light background.
                    c.setOpaque(true);
                    c.setBackground(Color.WHITE);
                    c.setForeground(new Color(0x0D6EFD));
                } else {
                    c.setOpaque(true);
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });
        classificationFilter.addActionListener(event -> {
            if (classificationFilter.getSelectedIndex() == 0) {
                refreshAnimals();
                statusLabel.setText("Showing all animals");
            } else {
                onFilterByClassification();
            }
        });
        toolbar.add(classificationFilter);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        toolbar.add(progressBar);
        return toolbar;
    }

    // Private helper for MainFrame GUI logic
    private void applyToolbarMinimize() {
        int fullW = 240;
        int miniW = (int) (fullW * 0.20);
        Component[] comps = toolbar.getComponents();
        for (Component c : comps) {
            if (c instanceof JButton && !((JButton) c).getText().equals("◄") && !((JButton) c).getText().equals("►")) {
                JButton b = (JButton) c;
                if (toolbarMinimized) {
                    b.setText(shorten(b.getText()));
                    b.setPreferredSize(new Dimension(miniW, 30));
                    b.setMinimumSize(new Dimension(miniW, 30));
                    b.setMaximumSize(new Dimension(miniW, 30));
                } else {
                    b.setText(restoreText(b.getText()));
                    b.setPreferredSize(new Dimension(fullW, 40));
                    b.setMinimumSize(new Dimension(fullW, 40));
                    b.setMaximumSize(new Dimension(fullW, 40));
                }
                b.revalidate();
            } else if (c instanceof JComboBox) {
                JComboBox<?> cb = (JComboBox<?>) c;
                if (toolbarMinimized) {
                    cb.setPreferredSize(new Dimension(miniW, 30));
                    cb.setMinimumSize(new Dimension(miniW, 30));
                    cb.setMaximumSize(new Dimension(miniW, 30));
                } else {
                    cb.setPreferredSize(new Dimension(fullW, 40));
                    cb.setMinimumSize(new Dimension(fullW, 40));
                    cb.setMaximumSize(new Dimension(fullW, 40));
                }
                cb.revalidate();
            } else if (c instanceof JButton) {
                JButton tb = (JButton) c;
                if (tb.getText().equals("◄") || tb.getText().equals("►")) {
                    tb.setAlignmentX(toolbarMinimized ? Component.CENTER_ALIGNMENT : Component.RIGHT_ALIGNMENT);
                    tb.revalidate();
                }
            } else if (c instanceof JProgressBar) {
                JProgressBar pb = (JProgressBar) c;
                if (toolbarMinimized) {
                    pb.setPreferredSize(new Dimension(miniW, 20));
                    pb.setMinimumSize(new Dimension(miniW, 20));
                    pb.setMaximumSize(new Dimension(miniW, 20));
                } else {
                    pb.setPreferredSize(new Dimension(160, 22));
                    pb.setMinimumSize(new Dimension(120, 22));
                    pb.setMaximumSize(new Dimension(160, 22));
                }
                pb.revalidate();
            }
        }
        toolbar.revalidate();
        toolbar.repaint();
    }

    private String shorten(String text) {
        if (text == null) {
            return text;
        }
        if (text.startsWith("List")) {
            return "List...";
        }
        if (text.startsWith("Filter")) {
            return "Filter...";
        }
        if (text.startsWith("Import")) {
            return "Import...";
        }
        if (text.startsWith("Record")) {
            return "Record...";
        }
        if (text.startsWith("Mark")) {
            return "Mark...";
        }
        if (text.startsWith("Undo")) {
            return "Undo...";
        }
        if (text.startsWith("Fetch")) {
            return "Fetch...";
        }
        if (text.contains("All")) {
            return "<all...";
        }
        return text.length() > 6 ? text.substring(0, 6) + "..." : text;
    }

    private String restoreText(String text) {
        if (text.equals("List...")) {
            return "List / Refresh Animals";
        }
        if (text.equals("Filter...")) {
            return "Filter by Classification";
        }
        if (text.equals("Import...")) {
            return "Import Intake History (CSV)";
        }
        if (text.equals("Record...")) {
            return "Record Interest";
        }
        if (text.equals("Mark...")) {
            return "Mark Adopted";
        }
        if (text.equals("Undo...")) {
            return "Undo Last Status Change";
        }
        if (text.equals("Fetch...")) {
            return "Fetch Sample Pet Image";
        }
        if (text.equals("<all...")) {
            return "<All animals>";
        }
        return text;
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildAnimalTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL_BLUE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0x8FB8DA)),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        JScrollPane scroll = new JScrollPane(animalTable);
        scroll.setBorder(BorderFactory.createLineBorder(PANEL_BLUE));
        scroll.getViewport().setBackground(PANEL_BLUE);
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // Private helper for MainFrame GUI logic
    private static void styleScrollBar(javax.swing.JScrollBar bar) {
        bar.setUI(new javax.swing.plaf.metal.MetalScrollBarUI());
        bar.setBackground(PANEL_BLUE);
        for (java.awt.Component comp : bar.getComponents()) {
            if (comp instanceof javax.swing.JButton button) {
                button.setBorder(BorderFactory.createEmptyBorder());
                button.setFocusPainted(false);
                button.setContentAreaFilled(true);
                button.setOpaque(true);
                button.setBackground(PANEL_BLUE);
            }
        }
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildImagePanel() {
        imagePanel = new JPanel();
        imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.Y_AXIS));
        imagePanel.setBackground(PANEL_BLUE);
        imagePanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        imageLabel.setPreferredSize(IMAGE_SIZE);
        imageLabel.setMinimumSize(new Dimension(200, 200));
        imageLabel.setMaximumSize(new Dimension(400, 400));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setAlignmentX(CENTER_ALIGNMENT);
        imageLabel.setBorder(new RoundedUi.RoundedBorder(20, new java.awt.Color(0x8FB8DA), 1));
        imageInfoLabel.setAlignmentX(CENTER_ALIGNMENT);
        imagePanel.add(Box.createVerticalStrut(6));
        imagePanel.add(imageLabel);
        imagePanel.add(Box.createVerticalStrut(6));
        imagePanel.add(imageInfoLabel);

        java.awt.Image logo = LoginPanel.loadLogo();
        if (logo != null) {
            LoginPanel.LogoLabel logoLabel = new LoginPanel.LogoLabel(logo);
            logoLabel.setAlignmentX(CENTER_ALIGNMENT);
            int logoWidth = 180;
            int logoHeight = (int) (logoWidth * logo.getHeight(null) / (double) logo.getWidth(null));
            logoLabel.setPreferredSize(new Dimension(logoWidth, logoHeight));
            logoLabel.setMaximumSize(new Dimension(logoWidth, logoHeight));
            imagePanel.add(Box.createVerticalStrut(10));
            imagePanel.add(logoLabel);
        }

        return imagePanel;
    }

    // Private helper for MainFrame GUI logic
    private JButton button(String text, Runnable action) {
        RoundedUi.RoundedButton button = new RoundedUi.RoundedButton(text, 18, new Color(0x2B5B8A));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setPreferredSize(new Dimension(240, 40));
        button.setMinimumSize(new Dimension(240, 40));
        button.setMaximumSize(new Dimension(240, 40));
        button.addActionListener(event -> action.run());
        return button;
    }

    // ------------------------------------------------------------- actions

    // Private helper for MainFrame GUI logic
    private void onRefreshAnimals() {
        classificationFilter.setSelectedIndex(0);
        refreshAnimals();
        statusLabel.setText("Loaded " + tableModel.getRowCount() + " animal(s)");
    }

    // Private helper for MainFrame GUI logic
    private void onFilterByClassification() {
        Object selected = classificationFilter.getSelectedItem();
        if (selected == null || ALL_CLASSIFICATIONS.equals(selected)) {
            refreshAnimals();
            statusLabel.setText("Showing all animals");
            return;
        }
        Optional<ClassificationNode> node = classification.findByPath(selected.toString());
        if (node.isEmpty()) {
            warn("Classification not found: " + selected);
            return;
        }
        Set<Integer> ids = new HashSet<>();
        for (ClassificationNode descendant : node.get().flatten()) {
            ids.add(descendant.getId());
        }
        try {
            List<Animal> matches = new ArrayList<>();
            for (Animal animal : animalDao.findAll()) {
                if (animal.getClassificationId() != null && ids.contains(animal.getClassificationId())) {
                    matches.add(animal);
                }
            }
            tableModel.setAnimals(matches);
            statusLabel.setText(String.format("Recursive search under '%s': %d animal(s) across %d classification node(s)",
                    node.get().getName(), matches.size(), ids.size()));
            log("Recursive filter: " + node.get().path());
        } catch (SQLException failure) {
            error("Filter failed", failure);
        }
    }

    // Private helper for MainFrame GUI logic
    private void onImportCsv() {
        JFileChooser chooser = new JFileChooser(new File("data"));
        chooser.setDialogTitle("Select historical intake/adoption CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        Path sample = Paths.get("data", "intake_history.csv");
        if (Files.exists(sample)) {
            chooser.setSelectedFile(sample.toFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final Path path = chooser.getSelectedFile().toPath();
        progressBar.setValue(0);
        progressBar.setString("Importing...");
        statusLabel.setText("Importing " + path.getFileName() + " on a background thread...");

        final ImportWorker worker = new ImportWorker(new CsvImportService(database, classification), path);
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                int value = (int) event.getNewValue();
                progressBar.setValue(value);
                progressBar.setString(value + "%");
            } else if ("state".equals(event.getPropertyName())
                    && event.getNewValue() == SwingWorker.StateValue.DONE) {
                try {
                    CsvImportResult result = worker.get();
                    progressBar.setString("Done");
                    statusLabel.setText(result.summary());
                    log("Import finished: " + result.summary());
                    refreshAnimals();
                    refreshAnalytics();
                } catch (Exception failure) {
                    progressBar.setString("Failed");
                    error("Import failed", failure);
                }
            }
        });
        worker.execute();
    }

    // Private helper for MainFrame GUI logic
    private void onRecordInterest() {
        Animal animal = selectedAnimal();
        if (animal == null) {
            info("Select an animal in the table first.");
            return;
        }
        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            warn(animal.getName() + " has already been adopted.");
            return;
        }
        Adopter adopter = promptForAdopter("Record Adoption Interest");
        if (adopter == null) {
            return;
        }
        try {
            Adopter persisted = adopterDao.findOrCreate(adopter);
            int position = waitlist.addInterest(animal.getId(), persisted);
            waitlistDao.insert(animal.getId(), persisted.getId(), LocalDateTime.now(), position);
            if (animal.getStatus() != AnimalStatus.PENDING) {
                AnimalStatus previous = animal.getStatus();
                animal.setStatus(AnimalStatus.PENDING);
                animalDao.updateStatus(animal.getId(), AnimalStatus.PENDING);
                history.record(animal.getId(), previous, AnimalStatus.PENDING);
            }
            refreshAnimals();
            refreshAnalytics();
            log(String.format("Interest recorded: %s is #%d in the waitlist for %s",
                    persisted.getName(), position, animal.getName()));
            statusLabel.setText("Interest recorded for " + animal.getName());
        } catch (SQLException | IllegalArgumentException failure) {
            error("Could not record interest", failure);
        }
    }

    // Private helper for MainFrame GUI logic
    private void configureStatusFilter() {
        javax.swing.table.JTableHeader header = animalTable.getTableHeader();
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int column = header.columnAtPoint(e.getPoint());
                if (column == 6) {
                    showStatusFilterMenu(header, e.getX(), e.getY());
                }
            }
        });
        javax.swing.table.TableColumn statusColumn = animalTable.getColumnModel().getColumn(6);
        statusColumn.setHeaderRenderer(new StatusHeaderRenderer());
    }

    /** Header cell renderer for the Status column that shows a down-arrow icon. */
    private static final class StatusHeaderRenderer extends javax.swing.table.DefaultTableCellRenderer {
        StatusHeaderRenderer() {
            setOpaque(true);
            setBackground(new Color(0x2B5B8A));
            setForeground(Color.WHITE);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText(value + " \u25BC");
            return this;
        }
    }

    // Private helper for MainFrame GUI logic
    private void showStatusFilterMenu(java.awt.Component invoker, int x, int y) {
        // Close any previously opened status panel first (prevents stacking).
        if (statusPopup != null) {
            statusPopup.setVisible(false);
            statusPopup.dispose();
            statusPopup = null;
        }

        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(invoker);
        javax.swing.JDialog dialog = new javax.swing.JDialog(owner, java.awt.Dialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        ((javax.swing.JComponent) dialog.getContentPane()).setOpaque(false);

        StatusPopupPanel panel = new StatusPopupPanel();
        final javax.swing.JDialog d = dialog;
        Runnable close = () -> {
            if (statusPopup == d) {
                statusPopup = null;
            }
        };
        panel.addOption("All", () -> applyStatusFilter(null), dialog, close);
        for (AnimalStatus status : AnimalStatus.values()) {
            panel.addOption(status.label(), () -> applyStatusFilter(status.label()), dialog, close);
        }
        dialog.getContentPane().add(panel);
        dialog.pack();
        java.awt.Point location = invoker.getLocationOnScreen();
        dialog.setLocation(location.x + x, location.y + y);

        final javax.swing.JDialog current = dialog;
        dialog.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
            }

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                current.setVisible(false);
                current.dispose();
                if (statusPopup == current) {
                    statusPopup = null;
                }
            }
        });

        statusPopup = dialog;
        dialog.setVisible(true);
    }

    /** Rounded blue drop-down panel with a soft shadow (custom-painted). */
    private static final class StatusPopupPanel extends JPanel {
        StatusPopupPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        }

        void addOption(String text, Runnable action, javax.swing.JDialog dialog, Runnable onClose) {
            JLabel label = new JLabel(text);
            label.setForeground(Color.WHITE);
            label.setOpaque(false);
            label.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            label.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    label.setForeground(new Color(0xBFD7F2));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    label.setForeground(Color.WHITE);
                }

                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    action.run();
                    dialog.setVisible(false);
                    dialog.dispose();
                    onClose.run();
                }
            });
            add(label);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int s = 3;
            int w = getWidth() - s;
            int h = getHeight() - s;
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRoundRect(s, s, w, h, 22, 22);
            g2.setColor(new Color(0x2B5B8A));
            g2.fillRoundRect(0, 0, w, h, 22, 22);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Private helper for MainFrame GUI logic
    private void applyStatusFilter(String label) {
        javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter =
                (javax.swing.table.TableRowSorter<javax.swing.table.TableModel>) animalTable.getRowSorter();
        if (label == null) {
            sorter.setRowFilter(null);
            statusLabel.setText("Showing all animals");
        } else {
            sorter.setRowFilter(javax.swing.RowFilter.regexFilter(java.util.regex.Pattern.quote(label), 6));
            statusLabel.setText("Filtered by status: " + label);
        }
    }

    // Private helper for MainFrame GUI logic
    private void onMarkAdopted() {
        Animal animal = selectedAnimal();
        if (animal == null) {
            info("Select an animal in the table first.");
            return;
        }
        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            warn(animal.getName() + " has already been adopted.");
            return;
        }
        Adopter adopter = promptForAdopter("Complete Adoption");
        if (adopter == null) {
            return;
        }
        try {
            Adopter persisted = adopterDao.findOrCreate(adopter);
            AdoptionRecord record = new AdoptionRecord(animal.getId(), persisted.getId(), LocalDate.now(),
                    animal.adoptionFee());
            recordDao.insert(record);
            AnimalStatus previous = animal.getStatus();
            animal.setStatus(AnimalStatus.ADOPTED);
            animalDao.updateStatus(animal.getId(), AnimalStatus.ADOPTED);
            history.record(animal.getId(), previous, AnimalStatus.ADOPTED);

            Optional<Adopter> nextInLine = waitlist.pollNext(animal.getId());
            waitlistDao.deleteByAnimal(animal.getId());

            refreshAnimals();
            refreshAnalytics();
            String message = String.format("Adopted %s to %s for $%.2f", animal.getName(), persisted.getName(),
                    animal.adoptionFee());
            if (nextInLine.isPresent()) {
                message += " (next on waitlist: " + nextInLine.get().getName() + ")";
            }
            log(message);
            statusLabel.setText(message);
        } catch (SQLException | IllegalArgumentException failure) {
            error("Could not complete adoption", failure);
        }
    }

    // Private helper for MainFrame GUI logic
    private void onUndo() {
        Optional<StatusChange> undone = history.undo();
        if (undone.isEmpty()) {
            info("There is no status change to undo.");
            return;
        }
        StatusChange change = undone.get();
        try {
            animalDao.updateStatus(change.animalId(), change.from());
            refreshAnimals();
            log("Undid status change -> " + change);
            statusLabel.setText("Reverted " + change);
        } catch (SQLException failure) {
            error("Undo failed", failure);
        }
    }

    // Private helper for MainFrame GUI logic
    private void onFetchImage() {
        Animal animal = selectedAnimal();
        if (animal == null) {
            info("Select an animal to fetch a matching sample image.");
            return;
        }
        var service = PetImageServiceFactory.forSpecies(animal.species());
        statusLabel.setText("Fetching image from " + service.providerName() + "...");
        imageLabel.setIcon(null);
        imageLabel.setText("Loading...");
        imageInfoLabel.setText(" ");

        String breed = animal.getBreed();
        ImageWorker worker = new ImageWorker(service, breed, (int) IMAGE_SIZE.getWidth());
        worker.addPropertyChangeListener(event -> {
            if ("state".equals(event.getPropertyName())
                    && event.getNewValue() == SwingWorker.StateValue.DONE) {
                try {
                    ImageWorker.Result result = worker.get();
                    imageLabel.setText("");
                    imageLabel.setIcon(result.icon());
                    imageInfoLabel.setText(String.format("%s | %s", result.info().provider(),
                            result.info().breed() == null || result.info().breed().isBlank()
                                    ? "random" : result.info().breed()));
                    log("Fetched image from " + result.info().provider() + ": " + result.info().imageUrl());
                    statusLabel.setText("Image loaded for " + animal.getName());
                } catch (Exception failure) {
                    imageLabel.setIcon(null);
                    imageLabel.setText("Image unavailable");
                    imageInfoLabel.setText(" ");
                    log("Image fetch failed (offline?): " + failure.getMessage());
                    statusLabel.setText("Image fetch failed - check your connection");
                }
            }
        });
        worker.execute();
    }

    // ------------------------------------------------------------- helpers

    // Private helper for MainFrame GUI logic
    private Animal selectedAnimal() {
        int viewRow = animalTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.getAnimalAt(animalTable.convertRowIndexToModel(viewRow));
    }

    // Private helper for MainFrame GUI logic
    private Adopter promptForAdopter(String title) {
        JTextField nameField = new JTextField(18);
        JTextField emailField = new JTextField(18);
        JTextField phoneField = new JTextField(18);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Full name:"));
        panel.add(nameField);
        panel.add(new JLabel("Email:"));
        panel.add(emailField);
        panel.add(new JLabel("Phone (optional):"));
        panel.add(phoneField);

        int result = JOptionPane.showConfirmDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            return new Adopter(nameField.getText(), emailField.getText(), phoneField.getText());
        } catch (IllegalArgumentException invalid) {
            warn(invalid.getMessage());
            return null;
        }
    }

    // Private helper for MainFrame GUI logic
    private void refreshAnimals() {
        try {
            tableModel.setAnimals(animalDao.findAll());
        } catch (SQLException failure) {
            error("Could not load animals", failure);
        }
    }

    // Private helper for MainFrame GUI logic
    private void refreshAnalytics() {
        try {
            List<Animal> animals = animalDao.findAll();
            List<AdoptionRecord> records = recordDao.findAll();
            AnalyticsReport report = analytics.analyze(animals, records);
            Map<Integer, List<Animal>> grouped = AnalyticsService.groupByClassification(animals);
            Map<String, Long> classCounts = analytics.adoptionCountByClassification(classification.root(), grouped);
            analyticsPanel.showReport(report, classCounts, waitlist.totalWaiters());
        } catch (SQLException failure) {
            log("Analytics failed: " + failure.getMessage());
        }
    }

    // Private helper for MainFrame GUI logic
    private void populateClassificationFilter() {
        classificationFilter.addItem(ALL_CLASSIFICATIONS);
        for (String path : classification.allPaths()) {
            classificationFilter.addItem(path);
        }
    }

    // Private helper for MainFrame GUI logic
    private void loadWaitlists() {
        try {
            for (WaitlistDao.Entry entry : waitlistDao.loadAll()) {
                adopterDao.findById(entry.adopterId())
                        .ifPresent(adopter -> waitlist.addInterest(entry.animalId(), adopter));
            }
            log("Restored " + waitlist.totalWaiters() + " waitlist entr(ies) from the database");
        } catch (SQLException failure) {
            log("Could not restore waitlists: " + failure.getMessage());
        }
    }

    // Private helper for MainFrame GUI logic
    private void updateSelectedAnimalInfo() {
        Animal animal = selectedAnimal();
        if (animal == null) {
            statusLabel.setText("Ready");
            return;
        }
        statusLabel.setText(String.format("Selected: %s | fee $%.2f | care: %s",
                animal, animal.adoptionFee(), animal.careSchedule()));
    }

    // Private helper for MainFrame GUI logic
    private void log(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // Private helper for MainFrame GUI logic
    private void info(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    // Private helper for MainFrame GUI logic
    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    // Private helper for MainFrame GUI logic
    private void error(String title, Exception failure) {
        log(title + ": " + failure.getMessage());
        JOptionPane.showMessageDialog(this, title + "\n" + failure.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
    }
}
