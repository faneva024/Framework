package models;

import jakarta.servlet.http.HttpSession;

public class Session {
    private HttpSession session;

    public Session(HttpSession session) {
        this.session = session;
    }

    public Object get(String key) {
        return session.getAttribute(key);
    }

    public void add(String key, Object value) {
        session.setAttribute(key, value);
    }

    public void remove(String key) {
        session.removeAttribute(key);
    }

    public void invalidate() {
        session.invalidate();
    }
}