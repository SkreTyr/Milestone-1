# PawTrack — File Cheat Sheet (Defense Study Guide)

One short line per file. Each line is written as the comment you'd put at the top of that file.

---

## Entry / Bootstrap
```java
// Main — Program entry point: sets the look-and-feel, opens the database, then shows the Login panel on the Swing thread.
```

## Controllers (actions & rules)
```java
// AuthController — Handles login, account creation, password change, logout, and role checks (staff vs adopter).
// AnimalController — Manages animal operations (add, update, delete, refresh); changes are staff-only.
// AdoptionController — Completes adoptions, changes animal status, and supports undo of status changes.
// WaitlistController — Manages the FIFO waitlist: register interest, withdraw, and poll the next adopter.
// ImportController — Runs the CSV import in the background and reports progress to the UI.
// ClassificationController — Handles species/breed classification-tree actions.
// AnalyticsController — Supplies analytics data (top breeds, average stay) to the UI.
```

## DAO (database read/write)
```java
// Database — Creates the SQLite tables and seeds default users (admin/admin123, user/user123) on first run.
// AnimalDao — Reads and writes animal rows (insert, update, delete, find, count).
// UserDao — Reads and writes user accounts and links them to adopter profiles.
// AdopterDao — Reads and writes adopter (customer) records.
// AdoptionRecordDao — Reads and writes completed adoption records.
// WaitlistDao — Reads and writes waitlist entries.
// ReservationDao — Reads and writes pet reservations (active/expired/completed).
// ClassificationDao — Reads and writes classification-tree nodes.
```

## Services (business logic)
```java
// PasswordHasher — Hashes and verifies passwords using PBKDF2 with a random salt.
// PasswordPolicy — Enforces password strength rules (length, letter, number, symbol).
// CsvImportService — Imports the historical CSV log inside one transaction; skips malformed rows.
// CsvRow — One parsed line of the CSV log (name, species, breed, age, dates, adopter info).
// ReservationService — Creates/cancels first-in-line pet reservations, valid for 72 hours.
// AdoptionTransactionService — Completes an adoption as one atomic database transaction.
// WaitlistService — In-memory FIFO queue of interested adopters per animal.
// StatusHistoryService — Undo stack (LIFO) of animal status changes.
// AnalyticsService — Calculates shelter analytics: top breeds/species and average stay length.
// AnalyticsReport — Holds the computed analytics results to display.
// ClassificationService — Wraps the classification tree and provides recursive search helpers.
// PetPhotoStore — Stores and validates pet photos (size/type checks, re-saved as PNG).
// PetImageCache — Caches pet images (uploaded photo first, then API sample).
// AdoptionApplicationService — Handles account-scoped adoption applications (submit/review).
// ApplicationReviewService — Staff review actions on adoption applications.
// ApplicationHistoryService — Tracks status history of adoption applications.
// AccountSettingsService — Persists account profile settings.
// AccountPreferencesService — Persists per-user display preferences.
// AdopterNotificationService — Builds notifications for adopters.
// NotificationReadService — Tracks which notifications a user has read.
// MyApplicationStatusService — Provides an adopter's own application statuses.
// ShelterSeeder — Seeds sample shelter data for demo/testing.
// AnimalCsvExportService — Exports animal data to CSV.
// PawTrackWiring — Composition root that wires controllers/services together.
```

## API (external image fetch)
```java
// HttpJson — Sends HTTP GET requests (with timeouts) and returns the response body from a website.
// PetImageService — Interface for fetching a pet image.
// PetImageServiceFactory — Chooses the cat or dog image service based on the animal's species.
// PetImageResult — Holds a fetched image result.
// DogCeoImageService — Fetches random dog images from the Dog CEO API.
// CatApiImageService — Fetches cat images from TheCatAPI.
```

## Models (data)
```java
// Animal — Abstract base class for all animals (id, name, breed, age, status, fee).
// Dog — Concrete dog type with its own adoption fee and care schedule.
// Cat — Concrete cat type with its own adoption fee and care schedule.
// Species — Enum of animal species (DOG, CAT).
// AnimalStatus — Enum of lifecycle states (Intake, Available, Pending, Adopted, Returned).
// Role — Enum of user roles (STAFF, ADOPTER).
// User — A login account (username, password hash/salt, role, linked adopter id).
// Adopter — A person who adopts pets.
// AdoptionRecord — One completed adoption (animal, adopter, date, fee).
// ClassificationNode — A node in the species/breed category tree (parent + children).
// StatusChange — A single status transition (for undo).
```

## UI (screens & panels)
```java
// LoginPanel — Animated login screen where users enter credentials (locks after 3 failed attempts).
// CreateAccountPanel — Screen for creating a new adopter account.
// MainFrame — The main window that holds all panels/tabs after login.
// PawTrackStartupPanel — Wrapper that shows the intro/splash before the login panel.
// PawTrackIntroScreen — Intro animation/splash screen.
// PawTrackDashboard — Landing dashboard with summary info and pet images.
// AnimalBrowsePanel — Browse animals in Gallery or Table view with filters.
// AnimalFormDialog — Dialog to add/edit an animal's details.
// AnimalTableModel — Table model that holds animal rows for the JTable.
// PetProfilePanel — Shows a selected pet's profile details.
// AdoptionApplicationsPanel — Staff reviews applications, or adopter views their own.
// AdoptionApplicationDialog — Form where an adopter submits an adoption application.
// ApplicationDetailsPanel — Shows the details of one adoption application.
// ApplicationHistoryPanel — Shows the status history of an application.
// AdopterNotificationDialog — Shows notifications to the adopter.
// WaitlistPanel — Shows the waitlist for animals.
// WaitlistActionsPanel — Buttons/actions for waitlist operations.
// AnalyticsPanel — Data-mining dashboard: metric selector, chart, and text summary.
// BarChartPanel — Draws a simple bar chart for analytics.
// AccountSettingsPanel — Account profile, security, and preference controls.
// MyRecordsPanel — Shows the signed-in adopter's own records.
// PawTrackImportPanel — UI for choosing and importing a CSV file.
// ImportWorker / ImageWorker — Background SwingWorkers for import and image loading.
// PawTrackActivityLogPanel — Shows a log of recent activity.
// Icons — Loads app/icon images.
// PawTrackTheme / PawTrackThemeManager — Shared colors and theme (light/dark).
// PawTrackSidebar / PawTrackHeader / PawTrackTabNavigator — Navigation chrome.
// RoundedUi / PremiumUi / PawTrackBackdrop — Reusable styling helpers.
```

---

## Most-likely "gotcha" corrections
- **Database** creates ALL tables + seeds default users on first run (not just "auth").
- **AdoptionController** = adoptions/status changes (NOT image fetching).
- **PetImageServiceFactory** DOES exist (in `api/`) — picks cat vs dog image service.
- **ClassificationNode** = organizes animals into a category tree (NOT "sorting").
- **PasswordHasher** = hashing (one-way), NOT encryption.
- **HttpJson** = fetches HTTP data; JSON parsing happens in the image services.