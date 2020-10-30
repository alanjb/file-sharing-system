import java.io.*;
import java.net.*;

public class Client {
    private static DataInputStream inFromServer = null;
    private static DataOutputStream outToServer = null;
    private static Socket clientSocket = null;

    public static void main(String[] args) throws IOException {
        String serverName = System.getenv("PA1_SERVER");

        if (serverName != null) {
            try {
                String[] vars = System.getenv("PA1_SERVER").split(":");
                String server = vars[0];
                int port = Integer.parseInt(vars[1]);

                init(server, port, args);
            } catch (Exception error) {
                System.out.println("ERROR: Cannot connect to Server" + error.getMessage());
            }
        } else {
            System.out.println("PA1_SERVER environment variable not set...");
        }
    }

    private static void init(String server, int port, String[] args) {
        try {
            clientSocket = new Socket(server, port);

            initStreams();

            runCommand(args);

        } catch (Exception error) {
            System.out.println("503 Service Unavailable: there was an issue connecting to the server: " + error);
        }
    }

    private static String getExecutionPath(){
        String executionPath = null;

        try {
            executionPath = System.getProperty("user.dir");
            System.out.print("Executing at => " + executionPath.replace("\\", "/"));
        } catch(Exception e){
            System.out.println("Exception caught ="+e.getMessage());
        }

        return executionPath;
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
                    System.out.println("UPLOAD SELECTED: Sending file to server...");
                    upload(args[1], args[2]);
                }

                case "download" -> {
                    System.out.println("DOWNLOAD: Calling server to retrieve file...");
                    receive(args[1], args[2]);
                }

                case "dir" -> {
                    System.out.println("List: Calling server to retrieve directory items...");
                    dir(args[1]);
                }

                case "mkdir" -> {
                    System.out.println("Create directory: Calling server to remove file...");
                    mkdir(args[1]);
                }

                case "rmdir" -> {
                    System.out.println("Remove directory: Calling server to remove file...");
                    rmdir(args[1]);
                }

                case "rm" -> {
                    System.out.println("Remove file: Calling server to remove file...");
                    removeFile(args[1]);
                }

                case "shutdown" -> {
                    System.out.println("Shutting down server.");
                    shutdown();
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

    private static void shutdown() throws IOException {
        String command = "shutdown";
        outToServer.writeUTF(command);
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
                System.err.println("404 ERROR: File does not exist on server. Please try again.");
            } else {
                System.out.println(filePathOnServer + " has been removed.");
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void upload(String filePathOnClient, String filePathOnServer) throws IOException {
        String command = "upload";
        File file = new File(filePathOnClient);
        long fileSize = file.length();
        String fileName = file.getName();

        try {
            try {
                //send command to server
                outToServer.writeUTF(command);
                System.out.println("Sending command type to server: " + command);

                //send file name to server
                outToServer.writeUTF(fileName);
                System.out.println("Sending file name: " + file.getName());

                //send client name to server
                String clientName = getExecutionPath();
                outToServer.writeUTF(clientName);
                System.out.println("Sending client's name to keep track in case of crash" + file.getName());

                //send path on server
                outToServer.writeUTF(filePathOnServer);
                System.out.println("Sending file path on server: " + filePathOnServer);

                //send file size to server
                outToServer.writeLong(fileSize);
                System.out.println("Sending file size: " + fileSize);
            } catch(IOException e){
                System.out.println("There was an issue sending upload data to server");
                e.printStackTrace();
            }

            RandomAccessFile raf = new RandomAccessFile(file, "rw");

            if(inFromServer.readBoolean()){
                System.out.println("Resuming upload for file: " + fileName);
                long unfinishedFileSizeOnServer = inFromServer.readLong();
                raf.seek(unfinishedFileSizeOnServer);
            }

            int read = 0;
            int filePosition = 0;
            int remaining = Math.toIntExact(fileSize);
            byte[] buffer = new byte[1024];

            while((read = raf.read(buffer, 0, Math.min(buffer.length, remaining))) > 0){
                filePosition += read;
                remaining -= read;
                System.out.print(
                        "\r Uploading file..."
                        + (int)((double)(filePosition)/fileSize * 100)
                        + "%");
                outToServer.write(buffer);
            }

            if(filePosition == fileSize){
                System.out.println("\n File Upload Complete");
            } else {
                System.out.println("There was an interruption when uploading file. Please retry to complete.");
            }

            raf.close();

        } catch(Exception e){
            e.printStackTrace();
        } finally {
            outToServer.close();
            inFromServer.close();
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
                System.err.println("404 ERROR: File does not exist on server. Please try again.");
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void rmdir(String filePathOnServer){
        String command = "rmDir";
        try {
            System.out.println("Sending request to remove directory ...");

            outToServer.writeUTF(command);
            outToServer.writeUTF(filePathOnServer);

            if(inFromServer.readBoolean()){
                System.out.println("SUCCESS! The directory was removed at..." + filePathOnServer);
            } else {
                int errorCode = inFromServer.readInt();

                if(errorCode == 1){
                    System.err.println("404 ERROR: Directory contains items so it cannot be removed. Please try again");
                } else if(errorCode == 2){
                    System.err.println("404 ERROR: Directory does not exist on server. Please try again.");
                }
            }
        } catch(Exception e){
            System.err.println("404 ERROR: There was an error trying to remove the directory.");
            e.printStackTrace();
        } finally {

        }
    }

    private static void dir(String filePathOnServer) {
        String command = "dir";

        try {
            System.out.println("Retrieving directory items...");

            outToServer.writeUTF(command);

            outToServer.writeUTF(filePathOnServer);

            if(inFromServer.readBoolean()) {
                System.out.println("Directory items in " + filePathOnServer + ": \n");
                System.out.println(inFromServer.readUTF());
            } else {
                System.out.println("404 ERROR: directory does not exist. Please try again.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    private static void mkdir(String filePathOnServer) throws IOException {
        String command = "mkdir";

        //transfer file name to server
        try {
            System.out.println("Sending directory creation request to server...");

            outToServer.writeUTF(command);
            outToServer.writeUTF(filePathOnServer);

            if (inFromServer.readBoolean()) {
                System.out.println("Successfully created directory at: " + filePathOnServer);
            } else {

                int errorCode = inFromServer.readInt();

                if(errorCode == 1){
                    System.out.println("400 ERROR: Couldn't create directory at: " + filePathOnServer + " because it already exists. Please try again.");
                } else {
                    System.out.println("400 ERROR: Couldn't create directory at: " + filePathOnServer + ". Please try again.");
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        } finally {
//                CloseAllStreams();
        }
    }
}