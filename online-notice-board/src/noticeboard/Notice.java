package noticeboard;

/** One notice on the board. Immutable - use the with... methods to get a changed copy. */
record Notice(String id, String title, String content, String category, String priority,
              String author, long created, long expires, boolean pinned) {

    /** expires == 0 means the notice never expires. */
    boolean expired(long now) {
        return expires > 0 && now > expires;
    }

    Notice withPinned(boolean value) {
        return new Notice(id, title, content, category, priority, author, created, expires, value);
    }

    Notice withEdit(String newTitle, String newContent, String newCategory, String newPriority, long newExpires) {
        return new Notice(id, newTitle, newContent, newCategory, newPriority, author, created, newExpires, pinned);
    }
}
