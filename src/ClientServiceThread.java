import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;

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

                //switch statement
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

                    //CHECKING STATUS OF THIS FILE - three states: completely new file, unfinished file, file already exists
//                    String filePosition = searchForUnfinishedFile(fileName, serverPath);

//                    System.out.println("File Position at run(): " + filePosition);

//                    if(filePosition != null){
//                        System.out.println("********** RESUMING FILE UPLOAD FOR " + fileName );
//                        System.out.println("FILEPOS HERE " + filePosition);
//                        //send file position for this file back to client
//                        this.dos.writeBoolean(true);
//                        this.dos.writeUTF(filePosition);
//                    } else {
//                        System.out.println("********** STARTING A NEW FILE UPLOAD FOR " + fileName);
//                        this.dos.writeBoolean(false);
//                    }

                    Long fileSize = this.dis.readLong();

                    receive(fileName, serverPath, fileSize);

                } else if(command.equalsIgnoreCase("download")){
                    String serverPath = this.dis.readUTF();

                    //call send method with specified file path on server
                    send(serverPath);
                } else if(command.equalsIgnoreCase("rm")){
                    String serverPath = this.dis.readUTF();

                    removeFile(serverPath);
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

    private void removeFile(String serverPath) throws IOException, FileNotFoundException {
        File file = new File(serverPath);

        try {
            if(file.exists()){
                this.dos.writeBoolean(true);
                file.delete();

            } else {
                this.dos.writeBoolean(false);
                System.out.println("There was an error. No file exists.");
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void send(String serverPath) throws IOException {
        File file = new File(serverPath);

        try {
            if(file.exists()){
                //send true that it exists
                this.dos.writeBoolean(true);

                //send fileSize
                this.dos.writeLong(file.length());

                //send file name
                this.dos.writeUTF(file.getName());

                FileInputStream fis = new FileInputStream(serverPath);
                byte[] buffer = new byte[1024];

                while(fis.read(buffer) > 0){
                    dos.write(buffer);
                }

            } else {
                //handle file does not exist
                this.dos.writeBoolean(false);
            }
        } catch(Exception e){
            this.dos.writeBoolean(false);
            System.out.println("An error occurred sending file from Server");
        }
    }

    private void receive(String fileName, String filePath, Long fileSize) throws IOException {
        String pathToFile = filePath + File.separator + fileName;
        File file = new File(pathToFile);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        FileChannel channel = raf.getChannel();
        Long filePos = null;
        byte[] buffer = new byte[1024];
        int read = 0;
        int filePosition = 0;
        int remaining = Math.toIntExact(fileSize);

        try {
            if(file.exists() && !file.isDirectory()) {
                if(!channel.isOpen()){
                //get the file length and pass it back to Client to seek()
                filePos = file.length();
                dos.writeLong(filePos);
                //raf.seek(filePos);

                } else {
                    //empty bytes
                    raf.setLength(0);

                    Lock lock = (Lock) channel.lock();

                    while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                        filePosition += read;
                        remaining -= read;
                        //System.out.println("read " + filePosition + " bytes.");
                        raf.write(buffer, 0, read);

                        if(filePosition >= 100000){
                            System.out.println(" ");
                            System.out.println("******");
                            System.out.println("*SIMULATING SERVER CRASH* Crashed: " + fileName + " at " + filePosition + " bytes. Please restart server to resume upload.");
                            break;
                        }
                    }
                }
            } else {
                System.out.println("File does not exist. Continuing upload...\n");
            }
        } catch (Exception e) {
            System.out.println("An error occurred attempting to find the file.");
            e.getMessage();
        } finally {
            dos.flush();
            dis.close();

            System.out.println(" ");
            System.out.println("FINISHED UPLOAD...");
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
                File[] dir = directoryToSend.listFiles();
                this.dos.writeUTF(Arrays.toString(dir));
                System.out.println("Sending directory items...");
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