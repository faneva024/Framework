package models;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUpload {
    private String filename;
    private byte[] bytes;

    public FileUpload() {}

    public FileUpload(String filename, byte[] bytes) {
        this.filename = filename;
        this.bytes = bytes;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Méthode utilitaire pour sauvegarder facilement le fichier sur le disque
     */
    public void saveTo(String directoryPath) throws IOException {
        File dir = new File(directoryPath);
        if (!dir.exists()) {
            dir.mkdirs(); // Crée le répertoire s'il n'existe pas
        }
        File targetFile = new File(dir, this.filename);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(this.bytes);
        }
    }
}