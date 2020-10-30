import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.*;

public class ClientServiceThread extends Thread {
    final DataInputStream dis;
    final DataOutputStream dos;
    final Socket clientSocket;

    public ClientServiceThread(Socket clientSocket, DataInputStream inFromClient, DataOutputStream outFromClient) {
        this.dis = inFromClient;
        this.dos = outFromClient;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        String userCommand;
            try {
                userCommand = this.dis.readUTF();

                System.out.println("Client Command Selected: " + userCommand);

                switch (userCommand) {
                    case "upload" -> {
                        String fileName = this.dis.readUTF();
                        String clientName = this.dis.readUTF();
                        String serverPath = this.dis.readUTF();
                        Long fileSize = this.dis.readLong();

                        String filePath = serverPath + File.separator + fileName;
                        System.out.println("FILEPATH ON SERVER: " + filePath);

                        System.out.println("Now checking if storage file exists...");

                        //create txt file to store hashmap if it doesn't already exist
                        boolean storageFileExists = checkIfFileStorageExists();

                        if(!storageFileExists){
                            System.out.println("No storage file file...creating...");
                            createStorageFile(clientName);
                        }

                        System.out.println("Checking if this file never finished uploading. " +
                                "If it does then you must be the owner to continue upload...");

                        boolean fileExistsAndClientIsOwner = searchForUnfinishedFileInStorage(filePath, clientName);

                        if(!fileExistsAndClientIsOwner){
                            //add entry into hash map with new client
                            updateHashMap(filePath, clientName);
                        }

                        receive(fileName, serverPath, fileSize, fileExistsAndClientIsOwner);
                    }

                    case "download" -> {
                        String serverPath = this.dis.readUTF();
                        send(serverPath);
                    }

                    case "dir" -> {
                        String existingFilePathOnServer = this.dis.readUTF();
                        listDirectoryItems(existingFilePathOnServer);
                    }

                    case "mkdir" -> {
                        String filePath = this.dis.readUTF();
                        createDirectory(filePath);
                    }

                    case "rmdir" -> {
                        String existingFilePathOnServer = this.dis.readUTF();
                        removeDirectory(existingFilePathOnServer);
                    }

                    case "rm" -> {
                        String serverPath = this.dis.readUTF();
                        removeFile(serverPath);
                    }

                    case "shutdown" -> shutdown();

                    default -> System.out.println("There was an error reading the user command...");
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }

        try {
            this.dos.close();
            this.dis.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkIfFileStorageExists(){
        String executionPath = System.getProperty("user.dir");

        System.out.println("EXECUTION PATH: " + executionPath);

        File file = new File(executionPath + File.separator + "unfinishedFiles.txt");

        System.out.println("FILE PATH: " + file.getAbsolutePath());

        return file.exists();
    }

    private void createStorageFile(String clientName){
        try {
            File storageFile = new File(clientName + File.separator + "unfinishedFiles.txt");

            //lock as well
            synchronized (storageFile){
                FileOutputStream fos = new FileOutputStream(storageFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);

                FileLock lock = fos.getChannel().lock();

                if (storageFile.createNewFile()) {
                    System.out.println("Storage file created: " + storageFile.getName());

                    //create new HashMap
                    HashMap<String, String> map = new HashMap<>();

                    oos.writeObject(map);

                } else {
                    System.out.println("File already exists.");
                }

                lock.release();
                fos.close();
                oos.close();
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private void updateHashMap(String filePath, String clientName) throws IOException {
        File storageFile = new File(clientName + File.separator + "unfinishedFiles.txt");
        FileInputStream fis = new FileInputStream(storageFile);

        FileOutputStream fos = new FileOutputStream(storageFile);

        try (fis; ObjectInputStream ois = new ObjectInputStream(fis); fos; ObjectOutputStream oos = new ObjectOutputStream(fos)) {

            @SuppressWarnings("unchecked")
            HashMap<String, String> hashmap = (HashMap<String, String>) ois.readObject();

            hashmap.put(filePath, clientName);

            oos.writeObject(hashmap);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean searchForUnfinishedFileInStorage(String filePath, String clientName) throws IOException, ClassNotFoundException {
        //return true if file exists AND current client is owner
        File storageFile = new File(clientName + File.separator + "unfinishedFiles.txt");
        boolean unfinishedFileExistsForCurrentClient = false;

        FileInputStream fis = new FileInputStream(storageFile);

        try (fis; ObjectInputStream ois = new ObjectInputStream(fis)) {
            if (storageFile.exists()) {

                //this should be our stored hash map that we read from the text file
                @SuppressWarnings("unchecked")
                HashMap<String, String> hashmap = (HashMap<String, String>) ois.readObject();

                if (hashmap.containsKey(filePath)) {
                    if (String.valueOf(hashmap.get(filePath)).equalsIgnoreCase(clientName)) {
                        unfinishedFileExistsForCurrentClient = true;
                    }
                } else {
                    System.out.println("FileName does not exist in hashmap");
                    unfinishedFileExistsForCurrentClient = false;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return unfinishedFileExistsForCurrentClient;
    }

    private void removeFromHashMap(){

    }

    private void shutdown(){
        System.out.println("Terminating program...goodbye.");
        System.exit(0);
    }

    private void removeFile(String serverPath) throws IOException, FileNotFoundException {
        File file = new File(serverPath);

        try {
            if(file.exists()){
                this.dos.writeBoolean(true);
                file.delete();

            } else {
                this.dos.writeBoolean(false);
                System.out.println("There was an error. No such file exists.");
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

    private void receive(String fileName, String filePath, Long fileSize, boolean fileExistsAndClientIsOwner) throws IOException {
        String pathToFile = filePath + File.separator + fileName;

        File file = new File(pathToFile);

        RandomAccessFile raf = new RandomAccessFile(file, "rw");

        if(fileExistsAndClientIsOwner){
            long filePos = file.length();

            //send back offset position to restart upload from where it left off
            dos.writeBoolean(true);
            dos.writeLong(filePos);

            //not sure if this is needed here
            raf.seek(filePos);
        }

        //synchronized and lock file
        synchronized(raf) {
            try {
                byte[] buffer = new byte[1024];
                int read = 0;
                int filePosition = 0;
                int remaining = Math.toIntExact(fileSize);

                FileLock lock = raf.getChannel().lock();

                try{
                    while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                        filePosition += read;
                        remaining -= read;
                        System.out.print(
                                "\r Downloading file..." +
                                (int)((double)(filePosition)/fileSize * 100) +
                                "%");
                        raf.write(buffer, 0, read);
                    }

                    if(filePosition == fileSize){
                        System.out.println("\n File Download Complete");
                        //remove from hashmap since the file completed

                    } else {
                        System.out.println("\n There was an interruption when uploading file. Please retry to complete.");
                    }
                } catch(Exception e){
                    System.out.println("\n Something went wrong as the client was uploading a file.");
                    e.printStackTrace();
                } finally {
                    lock.release();
                    raf.close();
                }
            } catch (Exception e) {
                System.out.println("An error occurred attempting to receive file on server.");
                e.printStackTrace();
            } finally {
                dos.flush();
                dis.close();
            }
        }
    }

    private void removeDirectory(String existingFilePathOnServer) throws IOException {
        try{
            File file = new File(existingFilePathOnServer);

            // check if directory is empty
            if (file.isDirectory()) {
                String[] list = file.list();
                if (list.length == 0) {
                    System.out.println("Directory is empty! Deleting...");
                    file.delete();
                    this.dos.writeBoolean(true);
                } else {
                    System.out.println("Directory is not empty! Unable to delete.");
                    this.dos.writeBoolean(false);
                    this.dos.writeInt(1);
                }
            } else {
                System.out.println("This directory does not exist. Please try again...");
                this.dos.writeBoolean(false);
                this.dos.writeInt(2);
            }
        } catch(Exception e){
            e.printStackTrace();
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
                this.dos.writeInt(1);
            }

        } catch(Exception e){
            e.printStackTrace();
            this.dos.writeBoolean(false);
            //return to client error
        }
    }
} 