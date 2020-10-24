import java.io.*;
import java.net.*;
import java.util.logging.Handler;

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
            System.out.println("503 Service Unavailable: there was an issue connecting to the server: " + error);
        }
    }

    private static void initStreams() throws IOException {
        inFromServer = new DataInputStream(clientSocket.getInputStream());
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
    }

    private static void runCommand(String[] args) throws IOException {
        String userCommand = args[0];

        try {
            switch (userCommand) {
                case "upload" -> {
                    System.out.println("UPLOAD: Sending file to server...");
                    send(args[1], args[2]);
                }
                case "download" -> {
                    System.out.println("DOWNLOAD: Calling server to retrieve file...");
                    receive(args[1], args[2]);
                }

                case "dir" -> {
                    System.out.println("List: Calling server to retrieve directory items...");
                    dir(args[1]);
                }

                case "rm" -> {
                    System.out.println("REMOVE: Calling server to remove file...");
                    removeFile(args[1]);
                }

                default -> System.out.println("Please enter a valid command");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    private static void removeFile(String filePathOnServer) throws IOException, FileNotFoundException {
        String command = "rm";

        //send command to server
        outToServer.writeUTF(command);

        //send file path to server
        outToServer.writeUTF(filePathOnServer);

        boolean fileExists = inFromServer.readBoolean();

        try {
            //if file exists on server
            if (!fileExists) {
                System.err.println("404 ERROR: File does not exist on server.");
            } else {
                System.out.println("File removed.");
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void send(String filePathOnClient, String filePathOnServer) throws IOException {
        String command = "upload";
        File file = new File(filePathOnClient);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        long fileSize = file.length();
        byte[] buffer = new byte[1024];

        //send command to server
        outToServer.writeUTF(command);
        System.out.println("Sending command type to server: " + command);

        //send file name to server
        outToServer.writeUTF(file.getName());
        System.out.println("Sending file name: " + file.getName());

        //send path on server
        outToServer.writeUTF(filePathOnServer);

        try {
//                System.out.println("RESUMING...starting skip");
//
//                String filePosition = inFromServer.readUTF();
//
//                System.out.println("filePosition: " + filePosition);
//
//                long fileSizeServer = Long.parseLong(filePosition);
//
//                raf.seek(fileSizeServer);
//
//                System.out.println("file data skipped");

            //send file size to server
            outToServer.writeLong(fileSize);

//            raf.seek(100352);

            while(raf.read(buffer) > 0){
                outToServer.write(buffer);
            }

            raf.close();

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void receive(String filePathOnServer, String filePathOnClient) throws IOException {
        String command = "download";

        //send command to server
        outToServer.writeUTF(command);

        //send file path to server
        outToServer.writeUTF(filePathOnServer);

        try {
            //if file exists on server
            if(inFromServer.readBoolean()){
                System.out.println("File exists on server...");

                //get file size from server
                long fileSize = inFromServer.readLong();

                //get file name from server
                String fileName = inFromServer.readUTF();


                FileOutputStream fos = new FileOutputStream(filePathOnClient + File.separator + fileName);
                byte[] buffer = new byte[1024];
                int read = 0;
                int filePosition = 0;
                int remaining = Math.toIntExact(fileSize);

                System.out.println("Starting download for " + fileName + "..." + "\n");

                while((read = inFromServer.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                    filePosition += read;
                    remaining -= read;
                    System.out.println("read " + filePosition + " bytes / " + fileSize + " total bytes");
                    fos.write(buffer, 0, read);
                }

                fos.close();

                if(filePosition == fileSize){
                    System.out.println("Finished download for " + fileName + "...");
                } else {
                    System.out.println("There was an error downloading " + fileName + ". Please try again.");
                }

            } else {
                System.err.println("404 ERROR: File does not exist on server.");
            }
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
                System.err.println("404 ERROR: Directory does not exist on server.");
            }

        } catch(Exception e){
            e.printStackTrace();
            e.getMessage();
        } finally {

        }
    }

    private static void dir(String filePathOnServer) {
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