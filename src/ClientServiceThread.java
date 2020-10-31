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

                System.out.println("Client Command Selected: " + userCommand + "\n");

                switch (userCommand) {
                    case "upload" -> {
                        String fileName = this.dis.readUTF();
                        String clientName = this.dis.readUTF();
                        String serverPath = this.dis.readUTF();
                        Long fileSize = this.dis.readLong();

                        String executionPath = System.getProperty("user.dir");

                        File file = new File(executionPath + File.separator + serverPath);

                        System.out.println("FILEPATH ON SERVER: " + file.getAbsolutePath());

                        System.out.println("Now checking if storage file exists...");
                        boolean storageFileExists = checkIfFileStorageExists();

                        if(!storageFileExists){
                            System.out.println("No storage file...creating...");
                            createStorageFile();
                        }

                        boolean fileExistsAndClientIsOwner = searchForUnfinishedFileInStorage(serverPath, clientName);

                        if(!fileExistsAndClientIsOwner){
                            System.out.println("Adding new file to hashmap in case of crash");

                            //add entry into hash map with new client to upload new file or replace file
                            updateHashMap(serverPath, clientName);
                            dos.writeBoolean(false);
                        } else {
                            System.out.println("***You are owner of unfinished file. Sending file position back to client to resume upload***");

                            long filePos = file.length();

                            System.out.println("Current file position " + filePos);

                            //send back offset position to restart upload from where it left off
                            dos.writeBoolean(true);
                            dos.writeLong(filePos);
                        }

                        receive(fileName, clientName, serverPath, fileSize, file, fileExistsAndClientIsOwner);
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

    private static String getExecutionPathOfCurrentClient(){
        String executionPath = null;

        try {
            executionPath = System.getProperty("user.dir");
        } catch(Exception e){
            e.printStackTrace();
        }

        return executionPath;
    }

    private boolean checkIfFileStorageExists(){
        String executionPath = System.getProperty("user.dir");

        boolean exists;

        File file = new File(executionPath + File.separator + "unfinishedFiles.txt");

        exists = file.exists();

        System.out.println("EXISTS ====>  " + exists);

        return exists;
    }

    private void createStorageFile() throws IOException {
        //get server
        String serverExecutionPath = null;

        try {
            serverExecutionPath = System.getProperty("user.dir");
            System.out.print("Executing at => " + serverExecutionPath.replace("\\", "/"));
        } catch(Exception e){
            e.printStackTrace();
        }

        File storageFile = new File(serverExecutionPath + File.separator + "unfinishedFiles.txt");

        boolean fileCreated = storageFile.createNewFile();

        System.out.println("Storage File Now Created: " + fileCreated);

        try {
            //needs to be synchronized because we don't want more than one thread trying to create this file
            synchronized (storageFile){
                FileOutputStream fos = new FileOutputStream(storageFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);

                FileLock lock = fos.getChannel().lock();

                if (fileCreated) {
                    System.out.println("Storage file created: " + storageFile.getName());

                    //create new HashMap and write to text file
                    HashMap<String, String> map = new HashMap<>();

                    oos.writeObject(map);
                }

                lock.release();
                fos.close();
                oos.close();
            }
        } catch (IOException e) {
            System.out.println("An error occurred trying to create storage file.");
            e.printStackTrace();
        }
    }

    private void updateHashMap(String filePath, String clientName) throws IOException, ClassNotFoundException  {
        String executionPath = System.getProperty("user.dir");
        File storageFile = new File(executionPath + File.separator + "unfinishedFiles.txt");

        try  {
            FileInputStream fis = new FileInputStream(storageFile);
            ObjectInputStream ois = new ObjectInputStream(fis);

            @SuppressWarnings("unchecked")
            HashMap<String, String> map = (HashMap<String, String>) ois.readObject();

            String fullPath = executionPath + filePath;

            map.put(fullPath, clientName);

            ois.close();
            fis.close();

            System.out.println("Added " + fullPath + " | " + clientName + " to storage");

            System.out.println("UNFINISHED FILES LIST: " + "\n");

            for(Map.Entry<String,String> m : map.entrySet()){
                System.out.println(m.getKey()+" : "+m.getValue());
            }

            System.out.println("\n");

            FileOutputStream fos = new FileOutputStream(storageFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            oos.writeObject(map);

            fos.close();
            oos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean searchForUnfinishedFileInStorage(String filePath, String clientName) throws IOException, ClassNotFoundException {
        String executionPath = System.getProperty("user.dir");
        File storageFile = new File(executionPath + File.separator + "unfinishedFiles.txt");

        boolean unfinishedFileExistsForCurrentClient = false;
        String fullPath = executionPath + filePath;

        System.out.println("Full Path: " + fullPath);

        FileInputStream fis = new FileInputStream(storageFile);
        ObjectInputStream ois = new ObjectInputStream(fis);

        try {
            if (storageFile.exists()) {
                System.out.println("Storage file exists...CHECK IF FILE EXISTS");

                @SuppressWarnings("unchecked")
                HashMap<String, String> hashmap = (HashMap<String, String>) ois.readObject();

                if (hashmap.containsKey(fullPath)) {
                    System.out.println("This path has an unfinished upload on server...Checking if client is owner...");

                    String client = hashmap.get(fullPath);

                    System.out.println("Client value: " + client + " ||| Client name: " + clientName);

                    if(client.equalsIgnoreCase(clientName)){
                        System.out.println("FileName does exist in hashmap and client matches...");
                        unfinishedFileExistsForCurrentClient = true;

                        System.out.println("UNFINISHED FILES LIST: " + "\n");

                        for(Map.Entry<String,String> m : hashmap.entrySet()){
                            System.out.println(m.getKey()+" : "+m.getValue());
                        }
                    } else {
                        //equals file path but not client
                        System.out.println("Same file path but different client uploaded. Replacing file with new upload from this client...");
                        unfinishedFileExistsForCurrentClient = false;
                        removeFromHashMap(filePath, clientName);
                    }
                } else {
                    System.out.println("FileName does not exist in hashmap");
                    unfinishedFileExistsForCurrentClient = false;
                }
            }

        } catch (Exception e) {
            System.out.println("There was an error finding the storage file: " +  "\n");
            e.printStackTrace();
        }



        System.out.println("DOES THE FILE EXIST IN THE HASHMAP: " + unfinishedFileExistsForCurrentClient);

        return unfinishedFileExistsForCurrentClient;
    }

    private void removeFromHashMap(String filePath, String clientName) throws FileNotFoundException {
        String executionPath = System.getProperty("user.dir");
        File storageFile = new File(executionPath + File.separator + "unfinishedFiles.txt");

        try  {
            FileInputStream fis = new FileInputStream(storageFile);
            ObjectInputStream ois = new ObjectInputStream(fis);

            @SuppressWarnings("unchecked")
            HashMap<String, String> map = (HashMap<String, String>) ois.readObject();

            String fullPath = executionPath + filePath;

            map.remove(fullPath, clientName);

            ois.close();
            fis.close();

            System.out.println("Deleted " + fullPath + " | " + clientName + " from storage" + "\n");

            System.out.println("UNFINISHED FILES LIST AFTER DELETE: " + "\n");

            for(Map.Entry<String,String> m : map.entrySet()){
                System.out.println(m.getKey()+" : "+m.getValue());
            }

            FileOutputStream fos = new FileOutputStream(storageFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            oos.writeObject(map);

            fos.close();
            oos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void shutdown(){
        System.out.println("Terminating program...goodbye.");
        System.exit(0);
    }

    private void removeFile(String serverPath) throws IOException, FileNotFoundException {
        String executionPath = getExecutionPathOfCurrentClient();

        File file = new File(executionPath + File.separator + serverPath);

        try {
            if(file.exists()){
                if(file.delete()){
                    this.dos.writeBoolean(true);
                    System.out.println("File deleted: " + file.getAbsolutePath());
                }
            } else {
                this.dos.writeBoolean(false);
                System.out.println("There was an error. No such file exists.");
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void send(String serverPath) throws IOException {
        String executionPath = getExecutionPathOfCurrentClient();
        File file = new File(executionPath + File.separator + serverPath);

        long filePosition = 0;

        long fileSize = file.length();

        System.out.println("Path of file on server: " + file.getAbsolutePath());

        boolean fileExists = file.exists();

        System.out.println("Does exist: " + fileExists);

        try {
            if(fileExists){
                //send true that it exists
                this.dos.writeBoolean(true);

                RandomAccessFile raf = new RandomAccessFile(file, "rw");

                System.out.println("File exists: " + file.getAbsolutePath() + "...Starting download");

                //send fileSize
                this.dos.writeLong(fileSize);

                int read = 0;
                int remaining = Math.toIntExact(fileSize);
                byte[] buffer = new byte[1024];

                while((read = raf.read(buffer, 0, Math.min(buffer.length, remaining))) > 0){
                    filePosition += read;
                    remaining -= read;
                    System.out.print(
                            "\r Sending file..."
                                    + (int)((double)(filePosition)/fileSize * 100)
                                    + "%");
                    dos.write(buffer);
                }

                if(filePosition >= fileSize){
                    System.out.print(
                            "\r Sending file...100%"
                    );
                    System.out.println("\n\n File Transfer Complete");
                }

            } else {
                System.out.println("File does not exist on server...");
                this.dos.writeBoolean(false);
            }
        } catch(Exception e){
            this.dos.writeBoolean(false);
            System.out.println("An error occurred sending file from Server");
        }
    }

    private void receive(String fileName, String clientName, String serverPath, Long fileSize, File file, Boolean fileExistsAndClientIsOwner) throws IOException {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");

            System.out.println("Random access file created");

            synchronized(raf) {
                try {
                    byte[] buffer = new byte[1024];
                    int read = 0;
                    long filePosition = 0;
                    int remaining = Math.toIntExact(fileSize);
                    Long filePos = file.length();

                    FileLock lock = raf.getChannel().lock();

                    try {
                        if(fileExistsAndClientIsOwner){
                            raf.seek(filePos);
                            filePosition = filePos;
                        }

                        while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                            filePosition += read;
                            remaining -= read;
                            System.out.print(
                                    "\r Downloading file..." +
                                            (int)((double)(filePosition)/fileSize * 100) +
                                            "%");
                            raf.write(buffer, 0, read);

//                            if(filePosition >= 2589725){
//                                System.out.println(" ");
//                                System.out.println("******");
//                                System.out.println("*SIMULATING SERVER CRASH* Crashed: " + fileName + " at " + filePosition + " bytes. Please restart server to resume upload.");
//                                break;
//                            }
                        }

                        if(filePosition >= fileSize){
                            System.out.println("\n File Download Complete");
                            //remove from hashmap since the file completed
                            removeFromHashMap(serverPath, clientName);
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
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void removeDirectory(String existingFilePathOnServer) throws IOException {
        String executionPath = getExecutionPathOfCurrentClient();

        try{
            File file = new File(executionPath + File.separator + existingFilePathOnServer);

            if (file.isDirectory()) {
                String[] list = file.list();
                if (list != null && list.length == 0) {
                    System.out.println("Directory is empty! Deleting...");
                    if(file.delete()){
                        this.dos.writeBoolean(true);
                    }
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
        String executionPath = getExecutionPathOfCurrentClient();

        System.out.println("Retrieving file/directory items from: " + existingFilePathOnServer);

        //creating file so we might need FileOutput Stream here.
        File directoryToSend = new File(executionPath + File.separator + existingFilePathOnServer);

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
            e.printStackTrace();
        }
    }

    public void createDirectory(String directoryPath) throws FileAlreadyExistsException, IOException, NoSuchFileException {
        System.out.println("Where to save this dir: " + directoryPath);
        String executionPath = getExecutionPathOfCurrentClient();

        try {
            File dir = new File(executionPath + File.separator + directoryPath);

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