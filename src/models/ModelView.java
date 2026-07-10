package models;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private String url;
    private HashMap<String, Object> data;

    public ModelView(String url) {
        this.url = url;
        this.data = new HashMap<>();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public HashMap<String, Object> getData() {
        return data;
    }

    // La méthode pour ajouter des attributs
    public void addAttribute(String key, Object value) {
        this.data.put(key, value);
    }
}