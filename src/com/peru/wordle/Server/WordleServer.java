package com.peru.wordle.Server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.peru.wordle.Client.WordleCallback;
import com.peru.wordle.User;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.Collections;

public class WordleServer extends RemoteServer implements WordleRegistrationInterface {
    private List<WordleCallback> clients = new ArrayList<WordleCallback>(); // Client registrati per la callback
    private HashMap<String, User> users; // Utenti registrati
    private int port; // Porta di ascolto
    private boolean on; // Flag status del server
    private int rmiPort; // Porta per RMI
    private static String currentWord; // Parola giocabile al momento
    private static String currentTranslatedWord; // Traduzione della parola in gioco
    private List<User> rankedUsers; // Ranking dei top 3 utenti

    /**
     * Costruttore del server
     * @throws RemoteException
     */
    public WordleServer() throws RemoteException {
        super();
        ServerSocketChannel serverSocketChannel; //Socket di ascolto
        SocketChannel socketChannel = null; //socket per gestione del client
        ThreadPoolExecutor tp = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        try {
            String currentDirectory = System.getProperty("user.dir");
            System.out.println("Current Directory: " + currentDirectory);

            JsonElement fileElement = JsonParser.parseReader(new FileReader("src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "config.json"));
            JsonObject fileObject = fileElement.getAsJsonObject();
            //estraggo il dati di configurazione dal file Json
            port = fileObject.get("server_port").getAsInt();
            rmiPort = port + 1;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        on = true;

        try {
            startServer();

            // Apertura delle connessioni
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            serverSocketChannel.configureBlocking(false);

            // Ascolto e smistamento
            while (on) {
                socketChannel = serverSocketChannel.accept();
                if (socketChannel != null) tp.execute(new WordleServerTask(this, socketChannel));
                else Thread.sleep(1000);
            }

            // Spegnimento
            tp.shutdown();
            try {
                tp.awaitTermination(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            serverSocketChannel.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            on = false;
        }
    }


    /**
     * Registra un nuovo utente
     * @param username Il nome dell'utente
     * @param password La password dell'utente
     * @return 0 se la registrazione è andata a buon fine, -1 se l'utente è già registrato, -2 se la password non è valida, -3 se c'è un errore inaspettato
     * @throws RemoteException
     */
    public synchronized int register(String username, String password) throws RemoteException {
        System.out.println("Trying to register: " + username);
        // Controllo se è già registrato
        if(users.containsKey(username.toLowerCase())) {
            System.out.println("User " + username + " is already registered");
            return -1;
        } else if (password.equals("")) {
            return -2;            
        } else {
            // Registro l'utente
            User newUser = new User(username, password);
            users.put(username, newUser);
            saveServer();
            return 0;
        }
    }

    /**
     * Ritorna lo user a partire dal suo username
     * @param username Lo username dell'utente che si vuole cercare
     * @return Un'istanza dello user cercato
     */
    public User lookupUser(String username) {
        return users.get(username);
    }

    /**
     * Effettua il login di un utente
     * @param username Lo username dell'utente che vuole fare il login
     * @param password La password associata a quello username
     * @return 0 se il login è avvenuto con successo, -1 se l'utente non è registrato, -2 se la password è sbagliata
     */
    public synchronized int login(String username, String password) {
        if(users.containsKey(username.toLowerCase())) {
            User user = users.get(username.toLowerCase());
            if(user.getPassword().equals(password)) {
                return 0;
            } else return -2; //Password Sbagliata
        } else return -1; //Utente non registrato
    }

    /**
     * Inizializza il server
     * @throws RemoteException
     */
    private void startServer() throws RemoteException {
        try {
            // Inizializzazione RMI
            WordleRegistrationInterface stub = (WordleRegistrationInterface) UnicastRemoteObject.exportObject(this, 39000);
            LocateRegistry.createRegistry(rmiPort);
            Registry r = LocateRegistry.getRegistry(rmiPort);
            r.bind("WordleService", stub);
        } catch (AlreadyBoundException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Registrazioni aperte sulla porta " + rmiPort);

        // Inizializzazione di WordPicker
        WordPicker wp = new WordPicker();
        ScheduledExecutorService scheduledSwService = Executors.newSingleThreadScheduledExecutor();    //creo un SingleThreadScheduledExecutor per estrarre la Secret Word casual periodicamente
        scheduledSwService.scheduleAtFixedRate(wp,0L,1L,TimeUnit.MINUTES);

        // Caricamento degli utenti da file
        try {
            String path = "src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "users";
            FileInputStream usersFile = new FileInputStream(path);
            String usersData = "";
            byte[] buffer = new byte[512];
            ByteBuffer byteBuffer;
            int n = 0;
            while (n > -1) {
                n = usersFile.read(buffer);
                byteBuffer = ByteBuffer.wrap(buffer);
                usersData = usersData.concat(StandardCharsets.UTF_8.decode(byteBuffer).toString());
            }
            usersFile.close();

            Gson gson = new Gson();
            JsonReader jsonReader = new JsonReader(new StringReader(usersData));
            jsonReader.setLenient(true); //Using relaxed rules
            Type type = new TypeToken<HashMap<String, User>>(){}.getType();
            users = gson.fromJson(jsonReader, type);
        } catch (IOException e) {
            users = new HashMap<String, User>();
        }

        // Aggiorno il ranking
        checkRanking();
    }

    /**
     * Salva un file
     * @param file Il file da salvare
     * @param data I dati da salvare
     * @return 0 se il salvataggio è stato completato, -1 se il file non è stato trovato, -2 in caso di IOException
     */
    private int saveFile(String file, String data) {
        try {
            FileOutputStream fOut = new FileOutputStream(new File(file));
            fOut.write(data.getBytes(StandardCharsets.UTF_8));
            return 0;
        } catch (FileNotFoundException e) {
            return -1;
        } catch (IOException e) {
            return -2;
        }
    }

    /**
     * Salva su file i dati riguardo agli utenti
     * @return 0 Se il salvataggio avviene con successo
     */
    public int saveServer() {
        if(users != null) {
            Gson gson = new Gson();
            String path = "src" + File.separator + "com" + File.separator + "peru" + File.separator + "wordle" + File.separator + "resources" + File.separator + "users";
            int n = saveFile(path, gson.toJson(users));
            if(n == -1) return -1;
            if(n == -2) return -2;
            return 0;
        } else return -3;
    }

    /**
     * Aggiorna i dati riguardo ad un utente dato il risultato di una partita
     * @param user L'utente che deve essere aggiornato
     * @param victory true se ha vinto, false se ha perso
     */
    public void updateUserData(User user, boolean victory) {
        System.out.println("User info: " + user.getUsername() + ", " + user.getLastStreak() + " " + user.getVictories());
        users.put(user.getUsername(), user);
        saveServer();

        // In caso di vittoria controllo anche il ranking
        if (victory) {
            if (rankedUsers.contains(user)) {
                Collections.sort(rankedUsers);
                updateRanking();
            } else {
                if (checkRanking()) updateRanking();
            }
        }
    }

    /**
     * @return La parola in gioco al momento
     */
    public static String getCurrentWord() { return currentWord; }

    /**
     * Aggiorna la parola in gioco
     * @param newWord La nuova parola che si vuole usare
     */
    public static void setCurrentWord(String newWord) {
        currentWord = newWord;
        System.out.println("A new word was set: " + getCurrentWord());
    }

    /**
     * Aggiorna la traduzione della parola in corso
     * @param translatedWord La traduzione che si vuole utilizzare
     */
    public static void setCurrentTranslatedWord(String translatedWord) {
        currentTranslatedWord = translatedWord;
        System.out.println("New translation available for the word " + currentWord + ": " + currentTranslatedWord);
    }

    /**
     * @return La traduzione della parola in gioco al momento
     */
    public static String getCurrentTranslatedWord() { return currentTranslatedWord; }

    /**
     * Registra un nuovo client per le ranking callback
     * @param clientInterface Il client da registrare
     * @throws RemoteException
     */
    public void registerForCallback(WordleCallback clientInterface) throws RemoteException {
        if(!clients.contains(clientInterface)) {
            clients.add(clientInterface);
            System.out.println("New client registered");
            updateRanking();
        }
    }

    /**
     * Notifica tutti i client registrati con il nuovo ranking
     */
    public synchronized void updateRanking() {
        for (WordleCallback callback : clients) {
            try {
                callback.updateRanking(rankedUsers);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Controlla se ci sono cambiamenti nel ranking
     * @return true se il ranking è cambiato, false altrimenti
     */
    public boolean checkRanking() {
        List<User> userList = new ArrayList<>(users.values());

        Collections.sort(userList);

        // Prendo i primi tre della lista
        List<User> topThreeUsers = userList.subList(0, Math.min(3, userList.size()));

        for(int i = 0; i < topThreeUsers.size(); i++) {
            User user = topThreeUsers.get(i);
            System.out.println(i + ". " + user.getUsername() + " Points: " + (user.getVictories() * user.getGuessDistribution()));
        }

        // Controllo se il ranking è cambiato
        if (topThreeUsers.equals(rankedUsers)) {
            return false;
        }
        rankedUsers = new ArrayList<>(topThreeUsers);
        System.out.println("New ranking available!");
        return true;
    }
}
