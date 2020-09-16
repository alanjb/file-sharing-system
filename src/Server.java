import java.io.*;
import java.net.*;

public class Server {
    private static ServerSocket serverSocket = null;
    private static boolean isRunning = false;

    public static void main(String[] args) throws IOException {
        //checking if error entered 'start' to run the system and the port number (port can be any number really)
        boolean errorAtStart = args.length != 2;

        System.out.println("*** Starting File System ***");

        try {
            //user starting server must input two arguments
            if (errorAtStart) {
                //print error if wrong number of inputs from user when starting server
                System.out.println("ERROR: You must enter two arguments - 'start' and a port number");
            } else if (args[0].equalsIgnoreCase("start")) {
                String serverPortNumber = args[1];

                //initialize the server socket to start listening and accepting requests from clients
                init(serverPortNumber);

                //run the server socket
                run();
            }
        } catch(Exception e) {
            System.out.println("ERROR...");
            e.getMessage();
        }
    }

    //method to set up the server socket with the provided port number that the user provided
    private static void init(String serverPort) {
        try {
            //parse from String to Integer
            int port = Integer.parseInt(serverPort);

            //create new server socket object, pass in port number
            serverSocket = new ServerSocket(port);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void run() throws IOException {
        //program is now running
        isRunning = true;

        System.out.println("The server is listening on port " + serverSocket.getLocalPort() + "...");

        //Server is running always. This is done using this while(true)
        while(isRunning){

            //new socket creation here
            Socket clientSocket = null;

            try {
                //this socket will communicate with the client socket
                clientSocket = serverSocket.accept();

                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                System.out.println("A new client is connected : " + clientSocket);

                Thread thread = new ClientServiceThread(clientSocket, dis, dos);

                System.out.println("Assigned new thread for this client. Starting thread...");

                thread.start();

            } catch (Exception e) {
                clientSocket.close();
                e.printStackTrace();
            }
        }
    }
}
