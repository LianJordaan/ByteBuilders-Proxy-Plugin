package io.github.lianjordaan.bytebuildersproxyplugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EnvLoader {

    public static Properties loadEnv() {
        Properties properties = new Properties();
        try (InputStream input = EnvLoader.class.getClassLoader().getResourceAsStream(".env")) {
            if (input == null) {
                throw new IOException("Unable to find .env file");
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return properties;
    }
}
