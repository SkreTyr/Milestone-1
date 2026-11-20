package pawtrack.service;

import pawtrack.controller.AuthController;
import pawtrack.controller.UnauthorizedException;
import pawtrack.dao.Database;
import pawtrack.dao.ReservationDao;
import pawtrack.model.Role;
import pawtrack.model.User;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Persisted read receipts for account-owned reservation updates. */
// [OOD – Single Responsibility] This class owns only notification read-receipt persistence and lookup.
public final class NotificationReadService {
    // [OOD – Encapsulation] Immutable record bundles reservation notice fields into one value object.
    public record Notice(int reservationId, String animal, String status, boolean read) {}
    // [OOD – Dependency Inversion] Depends on abstractions Database/AuthController/ReservationDao, not concrete wiring.
    private final Database database;
    private final AuthController auth;
    private final ReservationDao reservations;
    // [OOD – Dependency Injection] Collaborators are injected rather than constructed internally.
    public NotificationReadService(Database database, AuthController auth, ReservationDao reservations) {
        // [Defensive Programming] Objects.requireNonNull fails fast if wiring is incorrect.
        this.database = Objects.requireNonNull(database);
        this.auth = Objects.requireNonNull(auth);
        this.reservations = Objects.requireNonNull(reservations);
    }
    // [OOD – Encapsulation] Centralizes authorization so every public method shares one access check.
    private User me() {
        User user = auth.getCurrentUser(); // [Complexity – O(1)] Constant-time session lookup.
        if (user == null || user.getRole() != Role.ADOPTER || user.getAdopterId() == null || user.getId() == null)
            throw new UnauthorizedException("Adopter login required"); // [Security] Prevents cross-account access.
        return user;
    }
    // [Complexity – O(n*m)] Outer loop over reservations (n); inner DB query per item (m).
    public List<Notice> myNotices() throws SQLException {
        User user = me();
        // [Data Structures – List] ArrayList provides O(1) amortized append and indexed access.
        List<Notice> output = new ArrayList<>();
        // [Database – Parametric Query] Prepared statement avoids SQL injection and is compiled/parsed once.
        String sql = "SELECT 1 FROM notification_read WHERE user_id=? AND reservation_id=? AND status=?";
        // [Complexity – O(n)] Iterates recent adopter reservations returned by the DAO.
        for (var e : reservations.recentForAdopter(user.getAdopterId(), 100)) {
            var r = e.reservation();
            // [Domain Logic] Computes the effective status from the reservation and current time.
            String status = AdopterNotificationService.status(r, LocalDateTime.now());
            // [Resource Management] try-with-resources guarantees PreparedStatement closure (O(1) cleanup).
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setInt(1,user.getId()); ps.setInt(2,r.id()); ps.setString(3,status); // [O(1)] Parameter binding.
                // [Resource Management] ResultSet is also closed automatically.
                try (ResultSet rs = ps.executeQuery()) {
                    // [Complexity – O(1)] Constant-time check of rs.next(); existence implies read=true.
                    output.add(new Notice(r.id(),e.animalName(),status,rs.next()));
                }
            }
        }
        return output;
    }
    // [OOD – Encapsulation] Record defines a stable application-notice DTO shape.
    public record ApplicationNotice(int applicationId,String animal,String status,boolean read,String updatedAt) {}
    // [Complexity – O(k)] Single SQL query returns all application notices in one database round trip.
    public List<ApplicationNotice> applicationNotices() throws SQLException {
        User user=me(); List<ApplicationNotice> output=new ArrayList<>();
        // [Resource Management] try-with-resources closes the PreparedStatement even on exception.
        try(PreparedStatement ps=database.connection().prepareStatement(
            // [Database – Join] INNER JOIN animal supplies name; LEFT JOIN keeps applications with no read row.
            "SELECT ap.id,a.name,ap.status,ap.updated_at, nr.user_id FROM adoption_application ap " +
            "JOIN animal a ON a.id=ap.animal_id LEFT JOIN application_notice_read nr ON nr.application_id=ap.id " +
            "AND nr.user_id=? AND nr.status=ap.status AND nr.updated_at=ap.updated_at " +
            "WHERE ap.adopter_id=? ORDER BY ap.updated_at DESC")) {
            ps.setInt(1,user.getId());ps.setInt(2,user.getAdopterId()); // [O(1)] Bind current user + adopter id.
            try(ResultSet rs=ps.executeQuery()){ // [Resource Management] Auto-closes the result set.
                // [Complexity – O(k)] Streams each row into a record; rs.next() drives iteration.
                while(rs.next())output.add(new ApplicationNotice(
                    rs.getInt(1),rs.getString(2),rs.getString(3),rs.getObject(5)!=null,rs.getString(4)));
            }
        }return output;
    }
    // [Complexity – O(n+m)] Counts unread reservation notices (n) plus unread application notices (m).
    public int unreadCount() throws SQLException {
        // [Data Structures – Stream] Stream pipeline filters unread notices and counts matches.
        return (int) myNotices().stream().filter(n -> !n.read()).count() +
            (int)applicationNotices().stream().filter(n -> !n.read()).count();
    }
    // [Security – Ownership Check] Verifies the notice belongs to the caller before marking it read.
    public void markRead(int reservationId, String status) throws SQLException {
        User user = me();
        // [Complexity – O(n)] Linear scan of the user's notices to confirm ownership.
        boolean owned = myNotices().stream().anyMatch(n -> n.reservationId() == reservationId && n.status().equals(status));
        if (!owned) throw new UnauthorizedException("Notification does not belong to this account");
        // [Resource Management] try-with-resources guarantees statement closure.
        try (PreparedStatement ps = database.connection().prepareStatement(
                // [Database – Idempotency] INSERT OR IGNORE makes repeated marking safe.
                "INSERT OR IGNORE INTO notification_read(user_id,reservation_id,status) VALUES(?,?,?)")) {
            ps.setInt(1,user.getId()); ps.setInt(2,reservationId); ps.setString(3,status); ps.executeUpdate(); // [O(1)] Single insert.
        }
    }
    // [Complexity – O(n*markRead)] Marks each unread reservation then each unread application notice.
    public void markAllRead() throws SQLException {
        // [Complexity – O(n²)] For each notice calls markRead, which re-scans myNotices (nested O(n)).
        for (Notice n : myNotices()) if (!n.read()) markRead(n.reservationId(), n.status());
        User user=me();
        for(ApplicationNotice n:applicationNotices()) if(!n.read()){
            // [Resource Management] Statement is opened per unread notice and closed automatically.
            try(PreparedStatement ps=database.connection().prepareStatement(
                "INSERT OR IGNORE INTO application_notice_read(user_id,application_id,status,updated_at) VALUES(?,?,?,?)")){
                ps.setInt(1,user.getId());ps.setInt(2,n.applicationId());ps.setString(3,n.status());ps.setString(4,n.updatedAt());ps.executeUpdate();
            }
        }
    }
}
