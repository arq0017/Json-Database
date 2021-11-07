package client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    private static String fileLocation = "src/main/java/client/data/";
    private static final String address = "127.0.0.1";
    private static int port = 8080;

    @Parameter( names = "-t")
    private static String type;

    @Parameter( names = "-k")
    private static String key;

    @Parameter( names = "-v")
    private static String value;

    @Parameter( names = "-in")
    private static String filePath;


    public static void main(String[] args) throws IOException {
        Main mainObject = new Main();
        // deserialization : CL args -> J-commander objects
        JCommander.newBuilder().addObject(mainObject).build().parse(args);

        try (
                Socket socket = new Socket(address, port);
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        ) {
            System.out.println("Client started!");
            String message;
            JsonObject query = new JsonObject();
            if (filePath != null) {
                message = Files.readString(Path.of(fileLocation + filePath));
            } else {
                // JsonObject is unordered collection of name/value pairs
                if (type != null) {
                    query.add("type", new JsonPrimitive(type));
                }
                if (key != null) {
                    query.add("key", new JsonPrimitive(key));
                }
                if (value != null) {
                    query.add("value", new JsonPrimitive(value));
                }
                Gson gson = new Gson();
                message = gson.toJson(query);
            }
            // sending request to server
            outputStream.writeUTF(message);
            System.out.println("Sent: " + message);
            // receiving response from established server
            System.out.println("Received: " + inputStream.readUTF());

        }
    }
}