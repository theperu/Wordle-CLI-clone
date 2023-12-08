package com.peru.wordle.Server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.peru.wordle.Game;
import com.peru.wordle.User;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Task che gestisce l'interazione tra il server e un client
 */
public class WordleServerTask implements Runnable {
    private WordleServer server; // L'istanza del server
    private SocketChannel socketChannel; // Socket per la comunicazione
    private User user; // User loggato
    private SelectionKey key; // Chiave per la comunicazione
    private boolean isPlaying; // Flag per segnare se uno user sta giocando o meno
    private boolean online; // Flag per segnare se il task è online
    private String lastPlayedWord = ""; // Ultima parola giocata dallo user
    private Game latestGame = null; // L'ultima partita giocata
    private InetAddress group; // Gruppo per le notifiche delle partite condivise
    private int multicastPort; // Porta multicast

    /**
     * Costruttore WordleServerTask
     * @param s Istanza del Server
     * @param socketChannel Socket utilizzata
     */
    public WordleServerTask(WordleServer s, SocketChannel socketChannel) {
        server = s;
        this.socketChannel = socketChannel;
        isPlaying = false;
        this.online = true;

        try {
            // Apro il file di config per prendere l'indirizzo del gruppo e la porta multicast
            JsonElement fileElement = JsonParser.parseReader(new FileReader("src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "config.json"));
            JsonObject fileObject = fileElement.getAsJsonObject();
            this.group = InetAddress.getByName(fileObject.get("multicastAddress").getAsString());
            this.multicastPort = fileObject.get("multicastPort").getAsInt();
        } catch (UnknownHostException | FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(128);
        String msg;
        try {
            socketChannel.configureBlocking(false);
            Selector selector = Selector.open();
            key = socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            // Gestisco le richieste del client
            do {
                // Controllo se è in corso una partita
                if (!isPlaying) {
                    int n;
                    do {
                        Thread.sleep(100);
                        byteBuffer.clear();
                        n = ((SocketChannel) key.channel()).read(byteBuffer);
                    } while (n == 0 && !isPlaying);

                    if (n == -1) online = false; //Disconnessione
                    else if (!isPlaying) {
                        do {
                            n = ((SocketChannel) key.channel()).read(byteBuffer);
                        } while (n > 0);
                        byteBuffer.flip();

                        String response = StandardCharsets.UTF_8.decode(byteBuffer).toString();
                        System.out.println("Got the response: " + response);

                        // Faccio il parsing della risposta
                        String command = response.split(":")[0];
                        String commandInfo = response.split(":")[1];
                        switch (command) {
                            case "login" -> {
                                if (this.user == null) {
                                    String username = commandInfo.split(" ")[0];
                                    String password = commandInfo.split(" ")[1];
                                    // Provo a effettuare il login
                                    int loginSuccessful = server.login(username, password);
                                    switch (loginSuccessful) {
                                        case 0 -> {
                                            this.user = server.lookupUser(username);
                                            msg = "answer:loginSuccessful";
                                            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                            do {
                                                n = ((SocketChannel) key.channel()).write(buffer);
                                            } while (n > 0);
                                            System.out.println("User " + user.getUsername() + " si è loggato con successo");
                                            // Invio lo user appena loggato
                                            Gson gson = new Gson();
                                            msg = gson.toJson(server.lookupUser(user.getUsername()));
                                            buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                            do {
                                                n = ((SocketChannel) key.channel()).write(buffer);
                                            } while (n > 0);
                                            buffer = ByteBuffer.allocate(256);
                                            do {
                                                try {
                                                    Thread.sleep(100);
                                                } catch (InterruptedException ignored) {
                                                }
                                                buffer.clear();
                                                n = ((SocketChannel) key.channel()).read(buffer);
                                            } while (n == 0);
                                            do {
                                                n = ((SocketChannel) key.channel()).read(buffer);
                                            } while (n > 0);
                                            buffer.flip();
                                            response = StandardCharsets.UTF_8.decode(buffer).toString();
                                            System.out.println(response);
                                        }

                                        // Gestisco gli errori
                                        case -1 -> {
                                            msg = "answer:loginError1";
                                            ByteBuffer buff1 = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                            do {
                                                n = ((SocketChannel) key.channel()).write(buff1);
                                            } while (n > 0);
                                            online = false;
                                        }
                                        case -2 -> {
                                            msg = "answer:loginError2";
                                            ByteBuffer buff2 = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                            do {
                                                n = ((SocketChannel) key.channel()).write(buff2);
                                            } while (n > 0);
                                            online = false;
                                        }
                                        case -3 -> {
                                            msg = "answer:loginError3";
                                            ByteBuffer buff3 = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                            do {
                                                n = ((SocketChannel) key.channel()).write(buff3);
                                            } while (n > 0);
                                            online = false;
                                        }
                                        default -> {
                                            msg = "answer:loginError";
                                            ByteBuffer buff = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                            do {
                                                n = ((SocketChannel) key.channel()).write(buff);
                                            } while (n > 0);
                                            online = false;
                                        }
                                    }
                                } else {
                                    msg = "answer:otherUserLoggedError";
                                    ByteBuffer buff = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                    do {
                                        n = ((SocketChannel) key.channel()).write(buff);
                                    } while (n > 0);
                                }
                            }

                            // Gestisco una nuova partita
                            case "playGame" -> {
                                String word = WordleServer.getCurrentWord();
                                String translatedWord = WordleServer.getCurrentTranslatedWord();
                                boolean won = false;
                                // Controllo se l'utente ha già giocato con la parola attuale
                                if (lastPlayedWord.equals(word)) {
                                    sendMessage("answer:abort");
                                } else {
                                    latestGame = new Game(user);
                                    lastPlayedWord = word;
                                    sendMessage("answer:" + word + "." + translatedWord);
                                    System.out.println("New game started by " + user.getUsername());
                                    for (int i = 0; i < 5; i++) {
                                        System.out.println("Starting entry #" + i+1);
                                        do {
                                            try {
                                                Thread.sleep(100);
                                            } catch (InterruptedException ignored) {
                                            }
                                            byteBuffer.clear();
                                            n = ((SocketChannel) key.channel()).read(byteBuffer);
                                        } while (n == 0);
                                        do {
                                            n = ((SocketChannel) key.channel()).read(byteBuffer);
                                        } while (n > 0);
                                        byteBuffer.flip();
                                        response = StandardCharsets.UTF_8.decode(byteBuffer).toString();
                                        // Faccio il parsing della risposta
                                        String submittedWord = response.split(":")[1];
                                        System.out.println("Entry #" + i+1 + ": " + submittedWord);
                                        StringBuilder result = new StringBuilder();
                                        // Controllo se ha indovinato
                                        if (submittedWord.equals(word)) {
                                            System.out.println("User " + user.getUsername() + " guessed correctly!");
                                            sendMessage("answer:victory");
                                            won = true;
                                            latestGame.addEntry("++++++++++");
                                            user.updateStreak(won, i + 1);
                                            i = 5;
                                        } else {
                                            // Controllo i caratteri per vedere le differenze
                                            for (int j = 0; j < 10; j++) {
                                                if (word.charAt(j) == submittedWord.charAt(j)) {
                                                    result.append(submittedWord.charAt(j)).append("+");
                                                } else if (word.contains("" + submittedWord.charAt(j))) {
                                                    result.append(submittedWord.charAt(j)).append("*");
                                                } else {
                                                    result.append(submittedWord.charAt(j)).append("-");
                                                }
                                            }
                                            // Aggiungo il tentativo rimuovendo tutti caratteri
                                            latestGame.addEntry(result.toString().replaceAll("[a-zA-Z0-9]", ""));
                                            sendMessage("answer:" + result);
                                        }
                                    }
                                    latestGame.displayGame();
                                    if (!won) user.updateStreak(won, 5);
                                }
                                server.updateUserData(user, won);
                            }

                            // Stampo le info riguardo all'utente
                            case "showUserStatistics" -> sendMessage("answer:"
                                    + user.getTotalGames() + " "
                                    + user.getGuessDistribution() + " "
                                    + user.getVictories() + " "
                                    + user.getLastStreak() + " "
                                    + user.getBestStreak()
                            );

                            // Condivido l'ultima partita
                            case "shareGame" -> {
                                int result = shareGame(latestGame);
                                if (result == 0) {
                                    sendMessage("answer:gameShared");
                                } else {
                                    sendMessage("answer:unableToShare");
                                }
                            }

                            // Effettua il logout
                            case "logout" -> {
                                if (this.user != null) {
                                    if (commandInfo.equals(this.user.getUsername())) {
                                        sendMessage("answer:logoutSuccessful");
                                        System.out.println("User " + user.getUsername() + " has logged out.");
                                        this.user = null;
                                    } else {
                                        sendMessage("answer:wrongUser");
                                    }
                                } else {
                                    msg = "answer:noUserLogged";
                                    ByteBuffer buff = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                                    do {
                                        n = ((SocketChannel) key.channel()).write(buff);
                                    } while (n > 0);
                                }
                            }
                        }
                    }
                }
            } while (online);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Invia un messaggio al client
     * @param msg Il messaggio da inviare
     */
    private void sendMessage(String msg) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            int n;
            do {
                n = ((SocketChannel) key.channel()).write(byteBuffer);
            } while (n > 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Condivide una partita
     * @param game La partita da condividere
     * @return 0 se la partita è stata condivisa, -1 altrimenti
     */
    public int shareGame(Game game) {
        if(game != null) {
            try {
                DatagramSocket datagramSocket = new DatagramSocket();
                Gson gson = new Gson();
                // Converto la partita in una stringa
                String gameAsMessage = gson.toJson(game);
                DatagramPacket packet = new DatagramPacket(gameAsMessage.getBytes(), gameAsMessage.getBytes().length, group, multicastPort);
                // Invio la partita agli altri
                datagramSocket.send(packet);
                System.out.println("Game sent!");
                return 0;
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        } else {
            return -1;
        }
    }

}
