# File Sharing System using Java Sockets 

##### Alan Boyce, SWE622, Programming Assignment 1

##### Deliverables
- Executable and self-contained jar file.
- A read-me text file. 
- Source Code in Java.

## Installation 

Open a terminal window and run the following command to create a 'server' directory and change directories into it.

 ```bash
 mkdir –p server && cd server
 ```
 
Add the pa1.jar file into the server directory. 


In the terminal window executing in the server directory, run the following command to start the server.
 
 ```bash
 java -cp pa1.jar server start 8000
 ```

Change directories out of the server and create a new 'client1' directory.

 ```bash
 cd .. && mkdir -p client1 && cd client1
 ```
 
Run the following command so clients know how to contact the server.

  ```bash
  export PA1_SERVER=localhost:8000
  ```

For Windows machines, run this command instead.
  ```bash
  set PA1_SERVER=localhost:8000
  ```
Add the pa1.jar file into the client directory. 

Files sent to or retrieved from the server will be stored/accessed in the server's / (root) directory. 
If a user specifies a subfolder within the root directory, the file will be stored/accessed within it.

Executable jar file is located /pa1/out/artifacts/pa1_jar/pa1.jar.

## Client Commands 

Run the following commands within a client directory.

Upload
  ```bash
  java -cp pa1.jar client upload <path_on_client> </path/filename/on/server>
  ```

Download
  ```bash
  java -cp pa1.jar client download </path/existing_filename/on/server> <path_on_client>
  ```

List directory items
  ```bash
  java -cp pa1.jar client dir </path/existing_directory/on/server>
  ```

Create a directory 
  ```bash
  java -cp pa1.jar client mkdir </path/new_directory/on/server>
  ```

Remove a directory 
  ```bash
  java -cp pa1.jar client rmdir </path/existing_directory/on/server>
  ```

Remove a file
  ```bash
  java -cp pa1.jar client rm </path/existing_filename/on/server>
  ```

Shutdown server 
  ```bash
  java -cp pa1.jar client shutdown
  ```
