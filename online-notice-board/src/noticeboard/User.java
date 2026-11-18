package noticeboard;

/** A registered user. Role is either "ADMIN" or "USER". */
record User(String username, String salt, String hash, String role, long created) {
    boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
