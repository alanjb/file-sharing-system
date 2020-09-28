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
        long fileSize = file.length();
        byte[] buffer = new byte[(int) fileSize];

        FileInputStream fis = new FileInputStream(file);

//        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

        //send command type
        outToServer.writeUTF(command);
        System.out.println("Sending command type to server: " + command);

        //send file name to server
        outToServer.writeUTF(file.getName());
        System.out.println("Sending file name: " + file.getName());

        try {
            //Server will send back if this file needs to finish uploading
            if(inFromServer.readBoolean()){
                //this means that an uploaded started but never finished so get filePosition
                String filePosition = inFromServer.readUTF();

                //convert from String to integer
                int filePos = Integer.parseInt(filePosition);

                //Advance the stream to the desired location in the file
                fis.skip(filePos);

            }

            //send file path on server to server
            outToServer.writeUTF(filePathOnServer);

            //send file size to server
            outToServer.writeLong(fileSize);

            int read = 0;
//            int totalRead = 0;
            long remaining = fileSize;
            while (fis.read(buffer) > 0) {
//                totalRead += read;
                remaining -= read;

                System.out.println(remaining + " bytes left to read" + "/" + fileSize + " total bytes");
                outToServer.write(buffer);
            }

            fis.close();
            outToServer.close();

            System.out.println("Finished sending file...");

        } catch (Exception e) {
            e.getMessage();
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