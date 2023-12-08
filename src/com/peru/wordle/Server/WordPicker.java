package com.peru.wordle.Server;

import com.google.gson.JsonObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/**
 * Gestore della parola in gioco
 */
public class WordPicker implements Runnable {
    public static Random random;
    private static ArrayList<String> words = new ArrayList<>(); // Lista delle parole


    public WordPicker() {
        random = new Random();
        loadWords();
    }

    public void run() {
        String word = words.get(random.nextInt(words.size())); // Pesco una parola
        WordleServer.setCurrentWord(word); // Aggiorno la parola
        String translatedWord = getTranslatedWord(word); // Ottengo la traduzione
        WordleServer.setCurrentTranslatedWord(translatedWord); // Aggiorno la traduzione
    }

    /**
     * Carica la lista delle parole dal file
     */
    public void loadWords() {
        String path = "src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "words.txt";
        try {
            FileReader fileReader = new FileReader(path);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String word = bufferedReader.readLine();
            while (word != null) {
                words.add(word);
                word = bufferedReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ottiene la traduzione in italiano di una parola
     * @param text La parola che si vuole tradurre
     * @return La traduzione della parola
     */
    public static String getTranslatedWord(String text) {
        try {
            String urlStr = "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&langpair=en|it";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(); // Apro la connessione

            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            String output = br.readLine();

            conn.disconnect(); // Chiudo la connessione

            // Faccio il parsing della risposta
            JsonParser parser = new JsonParser();
            JsonObject json = parser.parse(output).getAsJsonObject();

            return json.getAsJsonObject("responseData").get("translatedText").getAsString().toLowerCase();
        } catch (IOException e) {
            System.out.println("Something went wrong");
            e.printStackTrace();
            return "Missing translation";
        }
    }
}
