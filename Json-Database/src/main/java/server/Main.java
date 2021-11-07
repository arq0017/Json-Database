package server;
import com.google.gson.*;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {
    private static JsonObject database;
    private static int port = 8080;
    private static ReadWriteLock lock = new ReentrantReadWriteLock();
    private static Lock readLock = lock.readLock();
    private static Lock writeLock = lock.writeLock();
    private static ServerSocket serverSocket;
    private static String filePath = "src/main/java/server/data/db.json";
    // testing locally
    // private static String test_fileLocation = System.getProperty("user.dir") + "/JSON Database/task/src/server/data/db.json";

    public static void main(String[] args) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started!");

        // cache pools -> n size
        ExecutorService executor = Executors.newCachedThreadPool();
        while (true) {
            // accepting client
            Socket socket = serverSocket.accept();
            // executor queue
            executor.submit(() -> {
                try {
                    process(socket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }


    public static void process (Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        // data in bytecode -> data in jsonObject : only one thread can read during parallelism
        readLock.lock();
        FileReader fileReader = new FileReader(filePath);
        // :::::: database can be null :::::: added dummy data from throwing exceptions
        database = JsonParser.parseReader(fileReader).getAsJsonObject();
        fileReader.close();
        readLock.unlock();


        // to convert java objects into json representation
        Gson gson = new GsonBuilder().create();

        JsonObject response = new JsonObject();
        // client request - string
        String clientQuery = input.readUTF();
        JsonObject query = JsonParser.parseString(clientQuery).getAsJsonObject();
        switch (query.get("type").getAsString()) {
            case "exit":
                response.addProperty("response","OK");
                output.writeUTF(gson.toJson(response));
                serverSocket.close();
                System.exit(0);
            case "get":
                response = get(query);
                break;
            case "delete":
                response = delete(query);
                break;
            case "set":
                response = set(query);
                break;
        }
        // from Json object to json string
        output.writeUTF(gson.toJson(response));
        socket.close();
    }

        // getting the data from db.json (local storage)
    private static JsonObject get(JsonObject query) {
        JsonObject response = new JsonObject();
        JsonElement value = query.get("key");
        if (value.isJsonPrimitive() && database.has(value.getAsString())) {
            response.addProperty("response", "OK");
            response.add("value", value);
        } else if (value.isJsonArray()) {
            response.addProperty("response", "OK");
            response.add("value",findElement(value.getAsJsonArray(), false));
        } else {
            response.addProperty("response", "ERROR");
            response.addProperty("reason", "No such key");
        }
        return response;
    }

    // json -> setting the client request to file
    private static JsonObject set(JsonObject query) {
        JsonObject response = new JsonObject();
        JsonElement key = query.get("key");
        JsonElement value = query.get("value");
        if (key.isJsonPrimitive()) {
            database.add(key.getAsString(), value);
            response.addProperty("response", "OK");
        } else if (key.isJsonArray()) {
            // getting keys in json array
            JsonArray keys = key.getAsJsonArray();
            // get the json object until last element
            String toAdd = keys.remove(keys.size() - 1).getAsString();
            // replace the last element in object
            findElement(keys, true).getAsJsonObject().add(toAdd, value);
            response.addProperty("response", "OK");
        } else {
            response.addProperty("response", "ERROR");
            response.addProperty("reason", "No such key");
        }
        // updating database : global
        writeToDatabase();
        return response;
    }

    // for printing the json in file in prettier json format
    public static String prettyPrint(Object obj) {
        return new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(obj);
    }

    // update database
    private static void writeToDatabase() {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(prettyPrint(database));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // delete json object
    private static JsonObject delete(JsonObject query) throws IOException {
        JsonObject response = new JsonObject();
        JsonElement value = query.get("key");
        System.out.println(value);
        if (value.isJsonPrimitive() && database.has(value.getAsString())) {
            database.remove(value.getAsString());
            response.addProperty("response","OK");
        } else if (value.isJsonArray()){
            JsonArray keys = value.getAsJsonArray();
            String toRemove = keys.remove(keys.size() - 1).getAsString();
            findElement(keys, false).getAsJsonObject().remove(toRemove);
            response.addProperty("response","OK");
        } else {
            response.addProperty("response", "ERROR");
            response.addProperty("reason", "No such key");
        }
        //
        writeToDatabase();
        return response;
    }

    // finding the json object || creating if not present
    private static   JsonElement findElement(JsonArray keys, boolean createIfAbsent) {
        JsonElement tmp = database;
        if (createIfAbsent) {
            for (JsonElement key: keys) {
                if (!tmp.getAsJsonObject().has(key.getAsString())) {
                    tmp.getAsJsonObject().add(key.getAsString(), new JsonObject());
                }
                tmp = tmp.getAsJsonObject().get(key.getAsString());
            }
        } else {
            for (JsonElement key: keys) {
                if (!key.isJsonPrimitive() || !tmp.getAsJsonObject().has(key.getAsString())) {
                    throw new IllegalArgumentException();
                }
                tmp = tmp.getAsJsonObject().get(key.getAsString());
            }
        }
        return tmp;
    }



}
