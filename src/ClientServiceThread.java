import java.io.*;
import java.net.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

                    //CHECKING STATUS OF THIS FILE - three states: completely new file, unfinished file, file already exists
                    String filePosition = searchForUnfinishedFiles(fileName);

                    System.out.println("File Position at run(): " + filePosition);

                    //if its null, file does not exist in hash map

                    if(filePosition != null){
                        System.out.println("*234234234234********* RESUMING FILE UPLOAD FOR " + fileName);
                        System.out.println("FILEPOST HERE " + filePosition);
                        //send file position for this file back to client
                        this.dos.writeBoolean(true);
                        this.dos.writeUTF(filePosition);
                    } else {
                        System.out.println("********** STARTING A NEW FILE UPLOAD FOR " + fileName);
                        this.dos.writeBoolean(false);
                    }

                    String serverPath = this.dis.readUTF();

                    Long fileSize = this.dis.readLong();

                    System.out.println("STARTING RECEIVING...........");

                    receive(fileName, serverPath, fileSize);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }

        try {
            this.dos.close();
            this.dis.close();
        } catch(IOException e) {
            e.getMessage();
        }
    }

    private String searchForUnfinishedFiles(String fileName) throws IOException, ClassNotFoundException {
        String filePosition = null;
        File unfinishedFiles = new File("unfinishedFiles");

        if(unfinishedFiles.exists()) {

            try {
                FileInputStream fis = new FileInputStream("unfinishedFiles");
                ObjectInputStream ois = new ObjectInputStream(fis);

                @SuppressWarnings("unchecked")
                HashMap<String, Integer> hashmap = (HashMap<String, Integer>) ois.readObject();

                Integer pos = hashmap.get(fileName);

                System.out.println("POSITION::: " + pos);

                ois.close();
                fis.close();

                System.out.println("Deserialized HashMap...Checking for: " + fileName);
                System.out.println(" ");

                System.out.println("FILE POSITION!!!!: " + hashmap.get(fileName));

                if(hashmap.containsKey(fileName)){
                    //search the hashmap for fileName
                    filePosition = String.valueOf(hashmap.get(fileName));
                } else {
                    System.out.println(fileName + " does not exist in hashmap so first upload for this file.");
                }
            } catch(Exception e){
                e.printStackTrace();
            }

        } else {
            System.out.println("Hashmap file does not exist...creating it");
            try {
                FileOutputStream fos = new FileOutputStream("unfinishedFiles");
                ObjectOutputStream oos = new ObjectOutputStream(fos);

                HashMap<String, Integer> map = new HashMap<>();

                System.out.println("File created: " + unfinishedFiles.getName());

                map.put(fileName, null);

                System.out.println("TEST CONTENT: " + map.get(fileName));

//                if(!map.isEmpty()) {
//                    for (Map.Entry<String, Integer> stringIntegerEntry : map.entrySet()) {
//                        Map.Entry obj = (Map.Entry) stringIntegerEntry;
//                        System.out.println("Content: " + obj.getValue());
//                    }
//                }

                //write hashmap to .ser file
                oos.writeObject(map);

                oos.close();
                fos.close();

                System.out.println("DONE CREATING HASHMAP INTO FILE");


            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.getMessage();
            }
        }

        return filePosition;
    }

    private void receive(String fileName, String filePath, Long fileSize) throws IOException {
        try {
            byte[] buffer = new byte[Math.toIntExact(fileSize)];

            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            FileOutputStream fos = new FileOutputStream(filePath + File.separator + fileName);

            int bytesRead = 0;
            int totalRead = 0;
            long remaining = fileSize;

            System.out.println(" ");
            System.out.println("*** UPLOAD PROGRESS ***");

            while((bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                totalRead += bytesRead;
                remaining -= bytesRead;

                System.out.println(remaining + " bytes left to read" + "/" + fileSize + " total bytes");
                fos.write(buffer, 0, bytesRead);

                if(bytesRead != -1){
                    FileOutputStream foss = new FileOutputStream("unfinishedFiles");
                    ObjectOutputStream oos = new ObjectOutputStream(foss);

                    HashMap<String, Integer> map = new HashMap<>();

                    map.put(fileName, totalRead);

                    System.out.println("Updating " + fileName + " with: " + map.get(fileName) + "bytes. Continuing....");

                    oos.writeObject(map);

                    foss.close();
                    oos.close();

                    if(remaining < 212612){
                        System.out.println(" ");
                        System.out.println("******");

                        FileOutputStream fosss = new FileOutputStream("unfinishedFiles");
                        ObjectOutputStream ooss = new ObjectOutputStream(fosss);

                        HashMap<String, Integer> map1 = new HashMap<>();

                        map1.put(fileName, bytesRead);

                        System.out.println("Crashed at: " + map1.get(fileName) + "bytes. Please restart server to resume upload. ");

                        oos.writeObject(map1);
                        System.out.println("Saved bytes read up until: " + totalRead);

                        fosss.close();
                        ooss.close();

                        break;
                    }
                } else if(bytesRead == -1){
                    System.out.println("*** UPLOAD COMPLETE ***");
                }
            }

            fos.close();
            dis.close();

        } catch(Exception e){
            e.printStackTrace();
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