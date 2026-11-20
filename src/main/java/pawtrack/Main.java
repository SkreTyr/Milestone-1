package pawtrack;

import pawtrack.controller.AuthController;
import pawtrack.dao.Database;
import pawtrack.dao.UserDao;
import pawtrack.ui.LoginPanel;
import pawtrack.ui.MainFrame;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;

/**
 * Application entry point. Shows the login panel first, then launches the
 * Swing GUI with role-based access on the Event Dispatch Thread.
 */
// [OOD – Abstraction] Entry point isolates the application bootstrap from UI and persistence internals.
public final class Main {

    // [OOD – Encapsulation] Private constructor hides instantiation; this class only exposes static behavior.
    private Main() {
    }
    

    public static void main(String[] args) {
        try {
            // [OOD – Abstraction] Delegates look-and-feel setup to the JVM while hiding platform-specific details.
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // [Complexity – O(1)] Constant-time fallback; failure is swallowed so startup continues.
            // fall back to the cross-platform look and feel
        }
        // [OOD – Abstraction] Centralizes visual theming so UI components depend on shared style, not per-widget values.
        // Pastel-blue scrollbar track/thumb (used by the Metal scrollbar UI).
        UIManager.put("ScrollBar.track", new java.awt.Color(0xAFD3F2)); // [O(1)] Constant-time UI key/value registration.
        UIManager.put("ScrollBar.thumb", new java.awt.Color(0x8FB8DA)); // [O(1)] Constant-time UI key/value registration.
        UIManager.put("ScrollBar.thumbDarkShadow", new java.awt.Color(0x8FB8DA)); // [O(1)] Constant-time UI key/value registration.
        // [Architecture – Event-Driven] Schedules GUI startup on the Swing Event Dispatch Thread (EDT).
        SwingUtilities.invokeLater(() -> showLogin(true)); // [Concurrency] Avoids blocking the EDT; UI work runs asynchronously.
    }
    
    

    private static void showLogin(boolean playIntro) {
        try {
            // [Complexity – O(1)] Reads a system property with constant-time lookup and a default.
            String dbPath = System.getProperty("petshelter.db", "petshelter.db");
            // [OOD – Dependency Inversion] Depends on Database abstraction, not a concrete connection string source.
            Database database = Database.openFile(dbPath); // [O(1)] Opens/creates the backing store once at bootstrap.
            // [OOD – Single Responsibility] UserDao owns only user persistence concerns.
            UserDao userDao = new UserDao(database);
            // [OOD – Dependency Injection] AuthController receives its collaborator instead of creating it internally.
            AuthController authController = new AuthController(userDao);

            // [OOD – Encapsulation] Login window creation and configuration are encapsulated in this factory-like method.
            JFrame loginFrame = new JFrame("Pet Shelter - Login");
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // [O(1)] Constant-time frame configuration.
            loginFrame.setSize(1080, 690); // [O(1)] Constant-time frame configuration.
            loginFrame.setMinimumSize(new Dimension(800, 560)); // [O(1)] Constant-time frame configuration.
            java.awt.Image icon = pawtrack.ui.Icons.appIcon(); // [OOD – Abstraction] Icon details hidden behind a utility facade.
            if (icon != null) {
                loginFrame.setIconImage(icon); // [O(1)] Constant-time conditional assignment.
            }
            loginFrame.setLocationRelativeTo(null); // [O(1)] Constant-time positioning (center screen).

            // [Data Structures – Array] One-element array acts as a mutable holder so the lambda can capture the panel.
            final pawtrack.ui.PawTrackStartupPanel[] startup = new pawtrack.ui.PawTrackStartupPanel[1];
            // [OOD – Event-Driven] Callback handed to LoginPanel; invoked when authentication succeeds.
            LoginPanel loginPanel = new LoginPanel(authController, user -> {
                if (startup[0] != null) startup[0].stop(); // [O(1)] Constant-time lifecycle call.
                loginFrame.dispose(); // [O(1)] Releases native resources for the login window.
                try {
                    // [OOD – Dependency Injection] MainFrame receives collaborators it needs; it does not build them.
                    MainFrame frame = new MainFrame(user, database, authController);
                    // [OOD – Event-Driven] Logout listener registered on the main frame.
                    frame.setOnLogout(() -> {
                        authController.logout(); // [O(1)] Constant-time session invalidation.
                        frame.dispose(); // [O(1)] Releases main window resources.
                        try {
                            database.close(); // [O(1)] Closes the database connection.
                        } catch (java.sql.SQLException closeFailure) {
                            // [Concurrency – Hazard Avoidance] Logged failure prevents logout from crashing the UI thread.
                            java.util.logging.Logger.getLogger(Main.class.getName())
                                    .log(java.util.logging.Level.WARNING, "Failed to close database on logout", closeFailure);
                        }
                        showLogin(false); // [Control Flow] Recursive re-entry to login flow after logout.
                    });
                    frame.setVisible(true); // [O(1)] Displays the main application window.
                } catch (Exception failure) {
                    // [Architecture – Error Boundary] Prevents partial UI state from leaking to the user.
                    JOptionPane.showMessageDialog(null,
                            "Failed to start the application:\n" + failure.getMessage(),
                            "Startup error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1); // [O(1)] Terminates the JVM on unrecoverable startup failure.
                }
            }, true);

            startup[0] = new pawtrack.ui.PawTrackStartupPanel(loginPanel, playIntro); // [O(1)] Assigns captured holder.
            loginFrame.setContentPane(startup[0]); // [O(1)] Swaps in the startup wrapper as the visible content.
            loginFrame.setVisible(true); // [O(1)] Shows the login frame.
            startup[0].start(); // [O(1)] Starts intro/animation lifecycle.
        } catch (Exception failure) {
            // [Architecture – Top-level Error Boundary] Any unhandled startup exception becomes a user-facing dialog.
            JOptionPane.showMessageDialog(null,
                    "Failed to start the application:\n" + failure.getMessage(),
                    "Startup error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // [O(1)] Fails fast rather than leaving a broken UI.
        }
    }
}
