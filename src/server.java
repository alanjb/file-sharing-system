import java.io.*;
import java.net.*;

public class server {
    private static ServerSocket serverSocket = null;

    public static void main(String[] args) throws IOException {
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
            e.printStackTrace();
        }
    }

    private static void init(String serverPort) {
        try {
            //parse from String to Integer
            int port = Integer.parseInt(serverPort);

            //create new server socket object, pass in port number 8000
            serverSocket = new ServerSocket(port);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void run() throws IOException {
        //program is now running
        boolean isRunning = true;

        System.out.println("The server is listening on PORT " + serverSocket.getLocalPort() + "...");

        while(isRunning){

            //new socket creation here
            Socket clientSocket = null;

            try {
                //this socket will communicate with the client socket
                clientSocket = serverSocket.accept();

                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                Thread thread = new clientServiceThread(clientSocket, dis, dos);

                thread.start();

            } catch (Exception e) {
                assert clientSocket != null;
                clientSocket.close();
                e.printStackTrace();
            }
        }
    }
}