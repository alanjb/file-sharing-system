import java.io.*;
import java.net.*;
import java.util.Arrays;

public class Client {
    private static DataInputStream inFromServer = null;
    private static DataOutputStream outToServer = null;
    private static Socket clientSocket = null;
    private static boolean isRunning = false;

    public static void main(String[] args) throws IOException {
        String serverName = System.getenv("PA1_SERVER");

        if (serverName != null) {
            try {
                String[] vars = System.getenv("PA1_SERVER").split(":");
                String server = vars[0];
                Integer port = Integer.parseInt(vars[1]);

                init(server, port, args);
            } catch (Exception error) {
                System.out.println("ERROR: Cannot connect to Server" + error.getMessage());
            }
        } else {
            System.out.println("PA1_SERVER environment variable not set...");
        }
    }

    //Method is used to connect to server
    private static void init(String server, int port, String[] args) {
        try {
            clientSocket = new Socket(server, port);

            initStreams();

            runCommand(args);

        } catch (Exception error) {
            System.out.println("Error: there was an issue connecting to the server: " + error);
        }
    }

    private static void initStreams() throws IOException {
        inFromServer = new DataInputStream(clientSocket.getInputStream());
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
    }

    private static void runCommand(String[] args) throws IOException {
        String userCommand = args[0];

        try {
            if (userCommand.equalsIgnoreCase("send")) {

                System.out.println("Saving file to server...");
                send(args[1], args[2]);
            }
//            switch (userCommand) {
//                case "upload":
//                    System.out.println("Saving file to server...");
//                    uploadFileToServer(filePathOnClient, filePathOnServer);
//                    break;
//                case "download":
//                    // code block
//                    System.out.println("Starting download...");
////                    downloadFileFromServer(filePathOnServer, filePathOnClient);
//                    break;
//                case "dir":
//                    System.out.println("Starting retrieval of all file objects...");
//                    getDirectoryItems(filePathOnServer);
//                    break;
//                case "mkdir":
//                    // code block
//                    System.out.println("Creating directory server...");
//                    createDirectory(filePathOnServer);
//                    break;
//                case "removeDirectory":
//                    // code block
//                    break;
//                case "rmdir":
//                    System.out.println("Starting remove directory...");
//                    removeDirectory(filePathOnServer);
//                    // code block
//                    break;
//                case "shutdown":
//                    // code block
//                    break;
//                default:
//                    // code block
//            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    private static void send(String filePathOnClient, String filePathOnServer) throws IOException {
        String command = "upload";

        File file = new File(filePathOnClient);
        FileInputStream fis = new FileInputStream(filePathOnClient);
        long fileSize = file.length();
        byte[] buffer = new byte[(int) fileSize];

        outToServer.writeUTF(command);
        System.out.println("Sending command type to server: " + command);

        //send file name to server
        outToServer.writeUTF(file.getName());
        System.out.println("Sending file name: " + file.getName());

        //send path on server
        outToServer.writeUTF(filePathOnServer);

        try {

            if(inFromServer.readBoolean()) {
                System.out.println("RESUMING...starting skip");

                String filePosition = inFromServer.readUTF();

                System.out.println("filePosition: " + filePosition);

                long filePos = Long.parseLong(filePosition);

                fis.skip(filePos);

                System.out.println("filePosition: " + filePosition);

                System.out.println("file data skipped");
            }


            outToServer.writeLong(fileSize);

            while(fis.read(buffer) > 0){
                outToServer.write(buffer);
            }

            fis.close();

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void removeDirectory(String filePathOnServer){
        String command = "rmDir";
        try {
            System.out.println("Sending request to remove directory ...");

            outToServer.writeUTF(command);
            outToServer.writeUTF(filePathOnServer);

            if(inFromServer.readBoolean()){
                System.out.println("SUCCESS! The directory was removed at..." + filePathOnServer);
            } else {
                System.out.println("ERROR! The directory could not be removed at..." + filePathOnServer);
            }

        } catch(Exception e){
            e.printStackTrace();
            e.getMessage();
        } finally {

        }
    }

    private static void getDirectoryItems(String filePathOnServer) {
        String command = "dir";

        try {
            System.out.println("Retrieving directory items...");

            outToServer.writeUTF(command);
            outToServer.flush();
            outToServer.writeUTF(filePathOnServer);
            outToServer.flush();

            if(inFromServer.readBoolean()) {
                System.out.println(inFromServer.readUTF());
                System.out.println("Done on client...");
            } else {
                System.out.println("ERROR: This directory does not exist...");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            closeStreams();
        }
    }

    private static void createDirectory(String filePathOnServer) throws IOException {
        String command = "mkdir";

        //transfer file name to server
        try {
            System.out.println("Sending directory creation request to server...");

            outToServer.writeUTF(command);
            outToServer.writeUTF(filePathOnServer);

            if (inFromServer.readBoolean()) {
                System.out.println("Successfully created directory at: " + filePathOnServer);
            } else {
                System.out.println("ERROR: couldn't creat directory at: " + filePathOnServer);

                //reason for error from server here!!!

            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        } finally {
//                CloseAllStreams();
        }
    }


}