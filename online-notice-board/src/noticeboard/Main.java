package noticeboard;

import java.net.BindException;
import java.nio.file.Path;

/** Starts the Online Notice Board. Usage: java -cp bin noticeboard.Main [port] */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) port = Integer.parseInt(envPort.trim());
        if (args.length > 0) port = Integer.parseInt(args[0].trim());

        Store store = new Store(Path.of("data"));
        if (store.userCount() == 0) {
            firstRun(store);
        }

        try {
            new Server(store, port).start();
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use. Try another one, e.g.: java -cp bin noticeboard.Main 9090");
            System.exit(1);
        }
        System.out.println("Online Notice Board is running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    /** Creates the admin account (and a few sample notices) the very first time the app runs. */
    private static void firstRun(Store store) {
        String password = System.getenv("ADMIN_PASSWORD");
        boolean customPassword = password != null && !password.isBlank();
        if (!customPassword) password = "admin123";

        long now = System.currentTimeMillis();
        String salt = Util.salt();
        store.addUser(new User("admin", salt, Util.hash(password, salt), "ADMIN", now));
        System.out.println(customPassword
                ? "First run: created admin account 'admin' with the password from ADMIN_PASSWORD."
                : "First run: created admin account 'admin' with password 'admin123'.");

        if (store.noticeCount() == 0) {
            store.addNotice(new Notice(Util.newId(), "Welcome to the Online Notice Board",
                    "This board is for all announcements.\n\nRegister or log in to post your own notices. "
                            + "Admins can pin important notices to the top of the board.",
                    "General", "Normal", "admin", now, 0, true));
            store.addNotice(new Notice(Util.newId(), "Mid-semester exam timetable released",
                    "The timetable for the mid-semester examinations has been published.\n"
                            + "Please check your exam dates and report to the hall 15 minutes early.",
                    "Exams", "Important", "admin", now - 3_600_000L, now + 14 * 86_400_000L, false));
            store.addNotice(new Notice(Util.newId(), "Annual Sports Day registrations are open",
                    "Register with your class representative before the end of this week.\n"
                            + "Events: 100m, relay, long jump, shot put and tug of war.",
                    "Sports", "Normal", "admin", now - 7_200_000L, 0, false));
        }
    }
}
