import java.io.*;
import java.net.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;

public class ClientServiceThread extends Thread {
    final DataInputStream dis;
    final DataOutputStream dos;
    final Socket clientSocket;

    public ClientServiceThread(Socket clientSocket, DataInputStream inFromClient, DataOutputStream outFromClient) {
        this.dis = inFromClient;
        this.dos = outFromClient;
        this.clientSocket = clientSocket;
    }

    //each thread will have its own run()
    @Override
    public void run() {
        String command;
            try {
                System.out.println("Starting command selection: ");

                command = this.dis.readUTF();

                System.out.println("Command Selected: " + command);

                if(command.equalsIgnoreCase("mkdir")){
                    System.out.println("CREATING DIRECTORY ON SERVER...");
                    String filePath = this.dis.readUTF();
                    System.out.println("FILE PATH SELECTED: " + filePath);
                    createDirectory(filePath);
                } else if(command.equalsIgnoreCase("dir")){
                    System.out.println("RETRIEVING DIRECTORY ON SERVER!");
                    String existingFilePathOnServer = this.dis.readUTF();
                    System.out.println("FILE PATH ON SERVER SELECTED: " + existingFilePathOnServer);
                    listDirectoryItems(existingFilePathOnServer);
                } else if(command.equalsIgnoreCase("rmdir")){
                    System.out.println("REMOVING DIRECTORY ON SERVER!");
                    String existingFilePathOnServer = this.dis.readUTF();
                    System.out.println("FILE PATH ON SERVER TO DELETE SELECTED: " + existingFilePathOnServer);
                    removeDirectory(existingFilePathOnServer);
                } else if(command.equalsIgnoreCase("upload")){
                    System.out.println("UPLOADING FILE TO SERVER");

                    String fileName = this.dis.readUTF();
                    String serverPath = this.dis.readUTF();
                    Long fileSize = this.dis.readLong();

                    saveFile(fileName, serverPath, fileSize);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        try {
            this.dos.close();
            this.dis.close();
        } catch(IOException e) {
            e.getMessage();
        }
    }

    private void saveFile(String fileName, String filePath, Long fileSize) throws IOException {
        try{
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            FileOutputStream fos = new FileOutputStream(filePath + File.separator + fileName);
            byte[] buffer = new byte[Math.toIntExact(fileSize)];

            int read = 0;
            int totalRead = 0;
            long remaining = fileSize;
            while((read = dis.read(buffer, 0, Math.toIntExact(Math.min(buffer.length, remaining)))) > 0) {
                totalRead += read;
                remaining -= read;
                System.out.println("read " + totalRead + " bytes.");
                fos.write(buffer, 0, read);
            }

            fos.close();
            dis.close();

        } catch(Exception e){
            e.getMessage();
        }
}

    private void removeDirectory(String existingFilePathOnServer) throws IOException {
        try{
            File file = new File(existingFilePathOnServer);
            // check if directory is empty
            if (file.isDirectory()) {
                String[] list = file.list();
                if (list == null || list.length == 0) {
                    System.out.println("Directory is empty! Deleting...");
                    file.delete();
                    this.dos.writeBoolean(true);
                } else {
                    System.out.println("Directory is not empty! Unable to delete.");
                    this.dos.writeBoolean(false);
                }
            } else {
                System.out.println("This directory does not exist. Please try again...");
                this.dos.writeBoolean(false);
            }
        } catch(Exception e){
            this.dos.writeBoolean(false);
            e.printStackTrace();
            e.getMessage();

        } finally {
            //
        }
    }

    public void listDirectoryItems(String existingFilePathOnServer) throws IOException {
        FileInputStream fis = null;
        BufferedInputStream bis = null;

        System.out.println("Retrieving file/directory items from: " + existingFilePathOnServer);

        //creating file so we might need FileOutput Stream here.
        File directoryToSend = new File(existingFilePathOnServer);

        try {
            if(directoryToSend.isDirectory()){
                this.dos.writeBoolean(true);
                File [] dir = directoryToSend.listFiles();
                this.dos.writeUTF(Arrays.toString(dir));
                System.out.println("***Done on server...");
            } else {
                System.out.println("ERROR: Directory " + existingFilePathOnServer + " does not exist...");
                this.dos.writeBoolean(false);
            }

        } catch(Exception e){
            this.dos.writeBoolean(false);
            e.getMessage();
            e.printStackTrace();
        }
    }

    public void createDirectory(String directoryPath) throws FileAlreadyExistsException, IOException, NoSuchFileException {
        System.out.println("Where to save this dir: " + directoryPath);

        try {
            File dir = new File(directoryPath);

            if(!dir.exists()){
                boolean dirWasCreated = dir.mkdir();

                if(dirWasCreated){
                    this.dos.writeBoolean(true);
                    System.out.println("***Directory created successfully on SERVER...");
                }else{
                    System.out.println("Sorry could not create specified directory");
                    this.dos.writeBoolean(false);
                }
            } else {
                System.out.println("ERROR: This directory already exists. Please try again...");
                this.dos.writeBoolean(false);
            }

        } catch(Exception e){
            e.printStackTrace();
            this.dos.writeBoolean(false);
            //return to client error
        } finally {
            //close streams
        }
    }
} 