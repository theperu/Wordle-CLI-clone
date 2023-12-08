package com.peru.wordle.Client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.peru.wordle.Game;
import com.peru.wordle.Server.WordleRegistrationInterface;
import com.peru.wordle.User;
import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Il client per Wordle
 */
public class WordleClient extends RemoteObject implements WordleCallback {
    private int port; // Porta per la connessione al server

    private WordleRegistrationInterface wi; // Interfaccia per la registrazione del client per callback e sign in

    private SocketChannel socketChannel; // Socket per la comunicazione

    private SelectionKey key; // Chiave di lettura e scrittura per la comunicazione

    private String command; // Contiene la risposta del server

    private String address; // Indirizzo

    private ArrayList<Game> games; // Partite condivise

    private List<User> rankedUsers; // Ranking top 3 giocatori

    /**
     * Costruttore WordleClient
     * @param port La porta a cui connettersi
     * @param address L'indirizzo a cui connettersi
     * @throws RemoteException
     */
    public WordleClient (int port, String address) throws RemoteException {
        super();
        this.port = port;
        this.address = address;

        games = new ArrayList<>();
        try {
            // Apro config.json
            JsonElement fileElement = JsonParser.parseReader(new FileReader("src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "config.json"));
            JsonObject fileObject = fileElement.getAsJsonObject();

            InetAddress group = InetAddress.getByName(fileObject.get("multicastAddress").getAsString());
            int multicastPort = fileObject.get("multicastPort").getAsInt();
            int timeout = fileObject.get("timeout").getAsInt();
            NotificationsHandler nh = new NotificationsHandler(group, multicastPort, games, timeout); // Inizializzo il gestore delle notifiche
            Thread notificationHandler = new Thread(nh);
            notificationHandler.start();
            RankingUpdater ru = new RankingUpdater(this, port +1); // Inizializzo il gestore della classifica
            Thread rankingUpdater = new Thread(ru);
            rankingUpdater.start();
            Registry registry = LocateRegistry.getRegistry(port + 1);
            wi = (WordleRegistrationInterface) registry.lookup("WordleService");
        } catch (FileNotFoundException | UnknownHostException | NotBoundException | RemoteException e){
            e.printStackTrace();
        }
    }

    /**
     * Gestisce l'autenticazione di un utente al server
     * @param username Lo username con cui l'utente si è registrato
     * @param password La password associata all'utente
     * @return 0 se l'utente è entrato con successo, altrimenti ritorna un errore
     */
    public int authentication(String username, String password) {
        // Rimuovo i due punti da password e username in quanto potrebbero creare problemi di parsing della stringa al server
        if (username.contains(" ") || username.contains(":")) {
            username = username.replaceAll(" ", "");
            username = username.replaceAll(":", "");
        }
        if (password.contains(" ") || password.contains(":")) {
            password = password.replaceAll(" ", "");
            password = password.replaceAll(":", "");
        }

        try {
            int result = register(username, password); // Provo a registrare l'utente se non è già registrato
            if (result >= -1) {
                // Apro la connessione TCP verso il server
                socketChannel = SocketChannel.open();
                socketChannel.connect(new InetSocketAddress(address, port));
                socketChannel.configureBlocking(false);
                Selector selector = Selector.open();
                key = socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                // Preparo il messaggio da mandare al server
                String msg = "login:" + username + " " + password;
                ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                int n;
                do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);

                buffer = ByteBuffer.allocate(128);
                do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
                do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
                buffer.flip();
                String response = StandardCharsets.UTF_8.decode(buffer).toString();

                String command = response.split(":")[0]; // Contiene la prima parte della risposta del server
                String commandResult = response.split(":")[1]; // Contiene il risultato dell'operazione
                if (command.equals("answer")) {
                    // Controllo se il login è andato a buon fine o ho riscotrato un errore
                    if (commandResult.equals("loginSuccessful")) {
                        User user = null;
                        Gson gson = new Gson();
                        buffer = ByteBuffer.allocate(256);
                        do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
                        do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
                        buffer.flip();
                        response = StandardCharsets.UTF_8.decode(buffer).toString();
                        user = gson.fromJson(response, User.class); // Prendo i dati dell'utente appena loggato
                        if (user != null) {
                            System.out.println("User info: " + user.getUsername());
                        }
                        msg = "login:OK"; // Invio un messaggio di conferma
                        buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                        do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);
                        return 0;
                    } else if (commandResult.compareTo("loginError1") == 0) {
                        return -1;
                    } else if (commandResult.compareTo("loginError2") == 0) {
                        return -2;
                    } else if (commandResult.compareTo("loginError3") == 0) {
                        return -3;
                    } else return -4;
                } else {
                    System.out.println("Unexpected error, recived " + command + "insted of answer:");
                    return -5;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -5;
    }

    /**
     * Effettua il logout di uno user
     * @param username Lo user che deve effetture il logout
     * @return 0 se il logout viene completato con successo, -1 se nessuno user risulta loggato al momento, -2 se lo user che sta cercando di fare logout non è quello attualmente loggato, -3 se c'è stato un errore inaspettato
     */
    public int logoutUser(String username) {
        // Preparo e invio la richiesta di logout
        String msg = "logout:" + username;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            int n;
            do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);

            buffer = ByteBuffer.allocate(128);
            do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
            do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
            buffer.flip();
            String response = StandardCharsets.UTF_8.decode(buffer).toString();
            // Faccio il parsing della risposta
            String command = response.split(":")[0];
            String commandResult = response.split(":")[1];
            if (command.equals("answer")) {
                // Controllo se il logout è andato a buon fine
                if (commandResult.equals("logoutSuccessful")) {
                    return 0;
                } else if (commandResult.equals("noUserLogged")) {
                    return -1;
                } else if (commandResult.equals("wrongUser")) {
                    return -2;
                }
            }
            return -3;
        } catch (IOException e) {
            e.printStackTrace();
            return -3;
        }
    }

    /**
     * Registra un nuovo utente su Wordle
     * @param name Lo username con il quale lo user vuole registrarsi
     * @param password La password con il quale lo user vuole registrarsi
     * @return 0 se la registrazione è andata a buon fine, -1 se l'utente è già registrato, -2 se la password non è valida, -3 se c'è un errore inaspettato
     */
    public int register(String name, String password) {
        int n;
        try {
            n = wi.register(name, password); // Effettuo la registrazione e salvo il risultato in n
            if (n == 0) System.out.println("Congrats " + name + "! You are now registered");
            else if (n == -2) System.out.println("Ops! It looks like you submitted an invalid or empty password");
            return n;
        } catch (RemoteException e) {
            e.printStackTrace();
            return -3;
        }
    }

    /**
     * Mostra le informazioni relative all'utente
     */
    public void showMeStatistics() {
        // Preparo e invio la richiesta per le statistiche relative all'utente
        String msg = "showUserStatistics: ";
        try {
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            int n;
            do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);

            buffer = ByteBuffer.allocate(128);
            do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
            do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
            buffer.flip();
            String response = StandardCharsets.UTF_8.decode(buffer).toString();

            // Faccio il parsing della risposta
            String command = response.split(":")[0];
            String commandResult = response.split(":")[1]; // Contiene tutte le info dell'utente
            if (command.equals("answer")) {
                System.out.println("This are your statistics:\n");
                System.out.println("Total games played: " + commandResult.split(" ")[0]);
                System.out.println("Average guesses needed to win a game: " + commandResult.split(" ")[1]);
                System.out.println("Total victories: " + commandResult.split(" ")[2]);
                System.out.println("Current win streak: " + commandResult.split(" ")[3]);
                System.out.println("Best win streak: " + commandResult.split(" ")[4]);
                return;
            }
            System.out.println("Unexpected response from server: " + response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gestisce una partita a Wordle di un utente
     */
    public void playWordle() {
        // Preparo e invio la richiesta per iniziare una nuova partita
        String msg = "playGame: ";
        try {
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            int n;
            do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);

            buffer = ByteBuffer.allocate(128);
            do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
            do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
            buffer.flip();
            String response = StandardCharsets.UTF_8.decode(buffer).toString();

            // Faccio il parsing della risposta
            String command = response.split(":")[0];
            String commandResult = response.split(":")[1];
            if (command.equals("answer")) {
                // Controllo che l'utente non abbia già giocato con la parola corrente
                if (commandResult.equals("abort")) {
                    System.out.println("You already played the current word, you'll have to wait a bit.");
                } else {
                    String currentWord = commandResult.split("\\.")[0];
                    String currentTranslatedWord = commandResult.split("\\.")[1];
                    Scanner scanner = new Scanner(System.in);
                    // Faccio partire i tentativi dell'utente
                    for (int i = 0; i < 5; i++) {
                        String word = null;
                        do{
                            System.out.print("Entry #" + (i+1) + ": ");
                            word = scanner.nextLine();
                        } while (word.length() != 10);
                        boolean victory = this.sendWord(word); // Invio la parola al server e salvo il risultato
                        if (victory) {
                            System.out.println("That's correct! You won after " + (i + 1) + " guesses");
                            System.out.println("The translation for the word " + currentWord + " is: " + currentTranslatedWord);
                            return;
                        }
                    }
                    System.out.println("Sorry you don't have anymore guesses, you'll do better next time :)");
                    System.out.println("The word was " + currentWord + " which translated in italian means: " + currentTranslatedWord);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stampa tutte le partite condivise
     */
    public void showMeSharing() {
        for (Game game : games) {
            game.displayGame();
        }
    }

    /**
     * Condivide l'ultima partita giocata
     */
    public void shareGame() {
        // Preparo e invio la richiesta di condivisione di una partita
        String msg = "shareGame: ";
        try {
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            int n;
            do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);

            buffer = ByteBuffer.allocate(128);
            do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
            do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
            buffer.flip();
            String response = StandardCharsets.UTF_8.decode(buffer).toString();

            // Faccio il parsing della risposta
            String command = response.split(":")[0];
            String commandResult = response.split(":")[1];
            if (command.equals("answer")) {
                switch (commandResult) {
                    case "gameShared":
                        System.out.println("Game shared successfully!");
                        return;
                    case "unableToShare":
                        System.out.println("There is no game to share");
                        System.out.println("You didn't complete a game since your login");
                        return;
                    default:
                        System.out.println("Unexpected response from server: " + response);
                }
            }
            System.out.println("Unexpected response from server: " + response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Invia al server una tentativo d'indovinare la parola
     * @param word La parola che l'utente vuole mandare
     * @return true se l'utente ha indovinato la parola, false se non ha indovinato
     */
    public boolean sendWord(String word) {
        // Preparo e invio la parola
        String msg = "wordle:" + word;
        try {
            System.out.println("Sending " + msg);
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            int n;
            do { n = ((SocketChannel) key.channel()).write(buffer); } while (n > 0);

            buffer = ByteBuffer.allocate(128);
            do { buffer.clear(); n = ((SocketChannel) key.channel()).read(buffer); } while (n == 0);
            do { n = ((SocketChannel) key.channel()).read(buffer); } while (n > 0);
            buffer.flip();
            String response = StandardCharsets.UTF_8.decode(buffer).toString();

            // Faccio il parsing della risposta
            System.out.println("Response: " + response);
            String command = response.split(":")[0];
            String commandResult = response.split(":")[1];
            if (command.equals("answer")) {
                if (commandResult.equals("victory")) return true;
                System.out.println("Sorry you didn't get this one right");
                System.out.println("Result: " + commandResult); // Mostro i suggerimenti per provare a indovinare nuovamente
                return false;
            } else {
                System.out.println("Error while trying to retrive the answer.");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Aggiorna la classifica dei top 3 user
     * @param rankedUsers La classifica con i nuovi user
     * @throws RemoteException
     */
    public void updateRanking(List<User> rankedUsers) throws RemoteException {
        this.rankedUsers = rankedUsers;
    }

    /**
     * Mostra chi sono i top 3 user e il loro punteggio
     */
    public void showRanking() {
        System.out.println("Here are the top 3 Wordle palyers:");
        for(int i = 0; i < rankedUsers.size(); i++) {
            User user = rankedUsers.get(i);
            System.out.println(i + ". " + user.getUsername() + " Points: " + (user.getVictories() * user.getGuessDistribution()));
        }
    }
}
