package pawtrack.gui;

import pawtrack.api.PetImageServiceFactory;
import pawtrack.db.AdopterDao;
import pawtrack.db.AdoptionRecordDao;
import pawtrack.db.AnimalDao;
import pawtrack.db.ClassificationDao;
import pawtrack.db.Database;
import pawtrack.db.WaitlistDao;
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
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
 * Main application window. Wires the domain services to interactive Swing
 * controls and runs long-running work (CSV import, API/image downloads) on
 * background threads.
 */
/**
 * Main application window (GUI). Connects to Main (entry point), Database, DAOs,
 * services, and workers. Contains toolbar, animal table, analytics, activity log.
 */
public final class MainFrame extends JFrame {

    private static final String ALL_CLASSIFICATIONS = "<All animals>";
    // Private helper for MainFrame GUI logic
    private static final Dimension IMAGE_SIZE = new Dimension(360, 360);

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
    private JTabbedPane tabs;
    private JPanel toolbar;
    private JPanel imagePanel;
    private boolean darkMode = false;
    // Private helper for MainFrame GUI logic
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    // Private helper for MainFrame GUI logic
    private final JLabel statusLabel = new JLabel("Ready");
    // Private helper for MainFrame GUI logic
    private final JComboBox<String> classificationFilter = new JComboBox<>();
    // Private helper for MainFrame GUI logic
    private final JLabel imageLabel = new JLabel();
    // Private helper for MainFrame GUI logic
    private final JLabel imageInfoLabel = new JLabel(" ");
    // Private helper for MainFrame GUI logic
    private final AnalyticsPanel analyticsPanel = new AnalyticsPanel();

    public MainFrame() throws SQLException {
        super("Pet Shelter & Adoption Tracker");
        String dbPath = System.getProperty("petshelter.db", "petshelter.db");
        this.database = Database.openFile(dbPath);
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

        log("Database ready: " + dbPath);
        log(String.format("Classification tree loaded: %d nodes, depth %d",
                classification.nodeCount(), classification.depth()));
    }

    // ------------------------------------------------------------------ UI

    // Private helper for MainFrame GUI logic
    private void buildUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        setMinimumSize(new Dimension(900, 600));

        add(buildToolbar(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.addTab("Animals", buildAnimalTab());
        tabs.addTab("Shelter Analytics", analyticsPanel);
        analyticsPanel.setBorder(BorderFactory.createLineBorder(new Color(0x2B5B8A), 1));
        JScrollPane activityScroll = new JScrollPane(logArea);
        activityScroll.setBorder(BorderFactory.createLineBorder(new Color(0x2B5B8A), 1));
        tabs.addTab("Activity Log", activityScroll);
        add(tabs, BorderLayout.CENTER);

        add(buildImagePanel(), BorderLayout.EAST);
        add(statusLabel, BorderLayout.SOUTH);

        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setPreferredSize(new Dimension(160, 22));
        progressBar.setMinimumSize(new Dimension(120, 22));

        analyticsPanel.setRefreshAction(this::refreshAnalytics);

        logArea.setEditable(false);
        animalTable.setFillsViewportHeight(true);
        animalTable.setAutoCreateRowSorter(true);
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
                } catch (SQLException ignored) {
                    // nothing useful to do while shutting down
                }
            }
        });

        setSize(1200, 780);
        setLocationRelativeTo(null);
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildToolbar() {
        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        toolbar.setBorder(BorderFactory.createTitledBorder("Controls"));

        toolbar.add(button("List / Refresh Animals", this::onRefreshAnimals));
        toolbar.add(button("Toggle Theme", this::onToggleTheme));
        toolbar.add(button("Filter by Classification", this::onFilterByClassification));
        toolbar.add(button("Import Intake History (CSV)", this::onImportCsv));
        toolbar.add(button("Record Interest", this::onRecordInterest));
        toolbar.add(button("Mark Adopted", this::onMarkAdopted));
        toolbar.add(button("Undo Last Status Change", this::onUndo));
        toolbar.add(button("Fetch Sample Pet Image", this::onFetchImage));
        classificationFilter.setPrototypeDisplayValue("Dog > Sporting Group > Retriever");
        classificationFilter.addActionListener(event -> {
            if (classificationFilter.getSelectedIndex() == 0) {
                refreshAnimals();
                statusLabel.setText("Showing all animals");
            } else {
                onFilterByClassification();
            }
        });
        toolbar.add(classificationFilter);
        toolbar.add(progressBar);
        // Wrap toolbar in a scroll pane to avoid components stacking awkwardly when width is small
        JScrollPane toolbarScroll = new JScrollPane(toolbar, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        toolbarScroll.setBorder(null);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        wrapper.add(toolbarScroll, BorderLayout.CENTER);
        return wrapper;
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildAnimalTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createLineBorder(new Color(0x2B5B8A), 1));
        panel.add(new JScrollPane(animalTable), BorderLayout.CENTER);
        return panel;
    }

    // Private helper for MainFrame GUI logic
    private JPanel buildImagePanel() {
        imagePanel = new JPanel();
        imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.Y_AXIS));
        imagePanel.setBorder(BorderFactory.createTitledBorder("Sample Pet Image (live API)"));
        imageLabel.setPreferredSize(IMAGE_SIZE);
        imageLabel.setMinimumSize(new Dimension(200, 200));
        imageLabel.setMaximumSize(new Dimension(400, 400));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setAlignmentX(CENTER_ALIGNMENT);
        imageLabel.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0xCCCCCC)));
        imageInfoLabel.setAlignmentX(CENTER_ALIGNMENT);
        imagePanel.add(Box.createVerticalStrut(6));
        imagePanel.add(imageLabel);
        imagePanel.add(Box.createVerticalStrut(6));
        imagePanel.add(imageInfoLabel);
        return imagePanel;
    }

    // Private helper for MainFrame GUI logic
    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(new Color(0x2B5B8A));
        button.setForeground(Color.BLACK);
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Color normal = new Color(0x2B5B8A);
        Color hover = new Color(0x1F4366);
        Color pressed = new Color(0x142D4A);
        button.setBackground(normal);
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { button.setBackground(hover); }
            public void mouseExited(MouseEvent e) { button.setBackground(normal); }
            public void mousePressed(MouseEvent e) { button.setBackground(pressed); }
            public void mouseReleased(MouseEvent e) { button.setBackground(button.contains(e.getPoint()) ? hover : normal); }
        });
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
    private void onToggleTheme() {
        darkMode = !darkMode;
        applyTheme();
    }

    // Private helper for MainFrame GUI logic
    private void applyTheme() {
        Color bg = darkMode ? new Color(0x4A4A4A) : Color.WHITE;
        Color fg = darkMode ? Color.WHITE : Color.BLACK;
        getContentPane().setBackground(bg);
        recolor(getContentPane(), bg, fg);
        logArea.setBackground(darkMode ? new Color(0x2D2D2D) : Color.WHITE);
        logArea.setForeground(fg);
        animalTable.setBackground(bg);
        animalTable.setForeground(fg);
        statusLabel.setForeground(fg);
        imageInfoLabel.setForeground(fg);
        animalTable.getTableHeader().setForeground(Color.BLACK);
        classificationFilter.setForeground(Color.BLACK);
        tabs.setForeground(Color.BLACK);
        analyticsPanel.setForeground(fg);
        setMetricLabelBlack(analyticsPanel);
        setCombosBlack(analyticsPanel);
        if (toolbar.getBorder() instanceof javax.swing.border.TitledBorder) {
            ((javax.swing.border.TitledBorder) toolbar.getBorder()).setTitleColor(darkMode ? Color.WHITE : Color.BLACK);
        }
        if (imagePanel != null && imagePanel.getBorder() instanceof javax.swing.border.TitledBorder) {
            ((javax.swing.border.TitledBorder) imagePanel.getBorder()).setTitleColor(darkMode ? Color.WHITE : Color.BLACK);
        }
        revalidate();
        repaint();
    }

    // Private helper for MainFrame GUI logic
private void setMetricLabelBlack(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof JLabel && "Metric:".equals(((JLabel) ch).getText())) {
                ch.setForeground(Color.BLACK);
            } else if (ch instanceof Container) {
                setMetricLabelBlack((Container) ch);
            }
        }
    }
private void setCombosBlack(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof JComboBox) {
                ch.setForeground(Color.BLACK);
            } else if (ch instanceof Container) {
                setCombosBlack((Container) ch);
            }
        }
    }
    private void recolor(Component c, Color bg, Color fg) {
        if (c instanceof JButton) return;
        c.setBackground(bg);
        c.setForeground(fg);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) recolor(child, bg, fg);
        }
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
