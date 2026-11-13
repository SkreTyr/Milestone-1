package pawtrack;

import pawtrack.controller.AdoptionController;
import pawtrack.controller.AnimalController;
import pawtrack.controller.AuthController;
import pawtrack.dao.AdopterDao;
import pawtrack.dao.AdoptionRecordDao;
import pawtrack.dao.AnimalDao;
import pawtrack.dao.ClassificationDao;
import pawtrack.dao.Database;
import pawtrack.dao.UserDao;
import pawtrack.dao.WaitlistDao;
import pawtrack.service.AnalyticsService;
import pawtrack.service.ClassificationService;
import pawtrack.service.StatusHistoryService;
import pawtrack.service.WaitlistService;
import pawtrack.ui.LoginPanel;
import pawtrack.ui.MainFrame;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application entry point. Shows the login panel first, then launches the
 * Swing GUI with role-based access on the Event Dispatch Thread.
 */
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the cross-platform look and feel
        }
        // Pastel-blue scrollbar track/thumb (used by the Metal scrollbar UI).
        UIManager.put("ScrollBar.track", new java.awt.Color(0xAFD3F2));
        UIManager.put("ScrollBar.thumb", new java.awt.Color(0x8FB8DA));
        UIManager.put("ScrollBar.thumbDarkShadow", new java.awt.Color(0x8FB8DA));
        SwingUtilities.invokeLater(Main::showLogin);
    }

    private static void showLogin() {
        try {
            String dbPath = System.getProperty("petshelter.db", "petshelter.db");
            Database database = Database.openFile(dbPath);
            UserDao userDao = new UserDao(database);
            AuthController authController = new AuthController(userDao);

            AnimalDao animalDao = new AnimalDao(database);
            AdopterDao adopterDao = new AdopterDao(database);
            AdoptionRecordDao recordDao = new AdoptionRecordDao(database);
            WaitlistDao waitlistDao = new WaitlistDao(database);
            ClassificationDao classificationDao = new ClassificationDao(database);

            ClassificationService classification = ClassificationService.loadOrSeed(classificationDao);
            WaitlistService waitlist = new WaitlistService();
            StatusHistoryService history = new StatusHistoryService();
            AnalyticsService analytics = new AnalyticsService();

            AnimalController animalController = new AnimalController(animalDao);
            AdoptionController adoptionController = new AdoptionController(database, animalDao, adopterDao, recordDao, waitlistDao, history, waitlist, authController);

            JFrame loginFrame = new JFrame("Pet Shelter - Login");
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setSize(460, 520);
            loginFrame.setMinimumSize(new Dimension(420, 500));
            java.awt.Image icon = pawtrack.ui.Icons.appIcon();
            if (icon != null) {
                loginFrame.setIconImage(icon);
            }
            loginFrame.setLocationRelativeTo(null);

            LoginPanel loginPanel = new LoginPanel(authController, user -> {
                loginFrame.dispose();
                try {
                	MainFrame frame = new MainFrame(user, database, authController, animalController, adoptionController);
                    frame.setOnLogout(() -> {
                        frame.dispose();
                        showLogin();
                    });
                    frame.setVisible(true);
                } catch (Exception failure) {
                    LOGGER.log(Level.SEVERE, "Failed to start application window", failure);
                    JOptionPane.showMessageDialog(null,
                            "Failed to start the application:\n" + failure.getMessage(),
                            "Startup error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            });

            loginFrame.add(loginPanel);
            loginFrame.setVisible(true);
        } catch (SQLException failure) {
            LOGGER.log(Level.SEVERE, "Database connection failed", failure);
            JOptionPane.showMessageDialog(null,
                    "Failed to open database:\n" + failure.getMessage(),
                    "Startup error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}