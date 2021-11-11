package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TCPClient {

    private Socket socket;

    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server.
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {
        // TODO Step 1: implement this method
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables
        boolean valid = false;
        if (!isConnectionActive()){
            try {
                socket = new Socket(host, port);
                valid = true;
            }catch (IOException exception){
                valid = false;
            }
        }else {
            //Todo: her burde det kastes en exception eller lignende kanskje?
        }

        sendMessage("joke", socket);
        System.out.println(waitServerResponse());
        return valid;
    }

    public void sendJoke(){
        sendMessage("joke", socket);
    }

    /**
     * Sends a message through the wanted socket.
     * @param messageToSend the message you want to send.
     * @param socket the socket this message is for.
     * @return <code>true</code> if the message has been sent.
     *         <code>false</code> if the message could not be sent.
     */
    private synchronized boolean sendMessage(String messageToSend, Socket socket){
        boolean valid = false;
        if (isConnectionActive()){
            try {
                PrintWriter printWriter = makePrintWriter(socket);
                printWriter.println(messageToSend);
                valid = true;
            }catch (IOException exception){
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Makes a print writer that can be used in the code.
     * @param socket the socket the writer is for.
     * @throws IOException gets thrown if the print writer could not be made.
     * @return the print writer for this socket.
     */
    private PrintWriter makePrintWriter(Socket socket) throws IOException {
        return new PrintWriter(socket.getOutputStream(), true);
    }

    /**
     * Makes a buffered reader for that socket.
     * @param socket the socket you want the reader for.
     * @return a buffered reader that is from this socket.
     * @throws IOException get thrown if the buffered reader could not be made.
     */
    private BufferedReader getBufferedReader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        // TODO Step 4: implement this method
        // Hint: remember to check if connection is active
        if (isConnectionActive()){
            try {
                socket.close();
                onDisconnect();
                socket = null;
            }catch (IOException exception){
                onDisconnect();
            }
        }else {
            onDisconnect();
        }

    }

    /**
     * Checks if the connection is still an object.
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        return socket != null;
    }

    //Todo: Denne metoden under skjønner jeg helt ikke men tror den skal være min "send message". But i like send message better.
    /**
     * Send a command to server.
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd) {
        // TODO Step 2: Implement this method
        // Hint: Remember to check if connection is active
        boolean valid = false;
        if (isConnectionActive()){
            valid = sendMessage(cmd, socket);
        }
        return valid;
    }

    /**
     * Send a public message to all the recipients.
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {
        // TODO Step 2: implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
        return sendMessage("msg " + message, socket);
    }

    /**
     * Send a login request to the chat server.
     * @param username Username to use
     */
    public void tryLogin(String username) {
        // TODO Step 3: implement this method
        // Hint: Reuse sendCommand() method
        sendMessage("login " + username, socket);
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        // TODO Step 5: implement this method
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
        sendMessage("users", socket);
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        // TODO Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.

        return sendMessage("privmsg " + recipient + " " + message, socket);
    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        // TODO Step 8: Implement this method
        // Hint: Reuse sendCommand() method
        sendMessage("help", socket);
    }


    /**
     * Wait for chat server's response
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        // TODO Step 3: Implement this method
        // TODO Step 4: If you get I/O Exception or null from the stream, it means that something has gone wrong
        // with the stream and hence the socket. Probably a good idea to close the socket in that case.
        String response = null;
        if (isConnectionActive()){
            try {
                BufferedReader bufferedReader = getBufferedReader(socket);
                do {
                    response = bufferedReader.readLine();
                }while ((response == null));
            }catch (IOException exception){
                disconnect();
                lastError = "Connection was severed.";
            }
        }
        return response;
    }

    /**
     * Get the last error message
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        if (lastError != null) {
            return lastError;
        } else {
            return "";
        }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(() -> {
            parseIncomingCommands();
        });
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {
            // TODO Step 3: Implement this method
            // Hint: Reuse waitServerResponse() method
            // Hint: Have a switch-case (or other way) to check what type of response is received from the server
            // and act on it.
            // Hint: In Step 3 you need to handle only login-related responses.
            // Hint: In Step 3 reuse onLoginResult() method
            String response = waitServerResponse();

            if (response != null){
                String[] responseInParts = response.split(" ");
                String firstWord = responseInParts[0];
                switch (firstWord) {
                    case "loginok":
                        onLoginResult(true, null);
                        break;
                    case "loginerr":
                        onLoginResult(false, response);
                        break;
                    case "msg":
                        String message = makeArrayToSentenceWithoutTwoFirstWords(responseInParts);
                        onMsgReceived(false, responseInParts[1], message);
                        break;
                    case "privmsg":
                        String message2 = makeArrayToSentenceWithoutTwoFirstWords(responseInParts);
                        onMsgReceived(true, responseInParts[1], message2);
                        break;
                    case "msgerr":
                        onMsgError(response);
                        break;
                    case "cmderr":
                        onCmdError(response);
                        break;
                    case "supported":
                        onSupported(responseInParts);
                        break;
                    case "joke":
                        onMsgReceived(true, "Server", makeArrayToSentenceWithoutFirstWord(responseInParts));
                        break;
                    case "msgok":
                        break;
                    case "users":
                        onUsersList(responseInParts);
                        break;
                    default:
                        System.out.println(response);
                        break;
                }
            }
            // TODO Step 5: update this method, handle user-list response from the server
            // Hint: In Step 5 reuse onUserList() method

            // TODO Step 7: add support for incoming chat messages from other users (types: msg, privmsg)
            // TODO Step 7: add support for incoming message errors (type: msgerr)
            // TODO Step 7: add support for incoming command errors (type: cmderr)
            // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners

            // TODO Step 8: add support for incoming supported command list (type: supported)

        }
    }

    /**
     * Makes an array of strings into a sentence.
     * @param responseInParts the array you want to turn into a sentence.
     * @return the sentence the array makes without the first two words.
     */
    private String makeArrayToSentenceWithoutTwoFirstWords(String[] responseInParts){
        List<String> words = Arrays.stream(responseInParts).filter(mess -> !mess.equals(responseInParts[0])).filter(mess -> !mess.equals(responseInParts[1])).toList();
        return getListAsString(words);
    }

    /**
     * Makes an array to a sentence wihtout the first word.
     * @param responseInParts the array of strings.
     * @return a sentence without the first word in the array.
     */
    public String makeArrayToSentenceWithoutFirstWord(String[] responseInParts){
        List<String> words = Arrays.stream(responseInParts).filter(mess -> !mess.equals(responseInParts[0])).toList();
        return getListAsString(words);
    }

    /**
     * Makes a list of words into a string.
     * @param listOfWords the list with all the words.
     * @return the sentence from that list.
     */
    private String getListAsString(List<String> listOfWords){
        StringBuilder stringBuilder = new StringBuilder();
        listOfWords.forEach(word -> {
            stringBuilder.append(word);
            stringBuilder.append(" ");
        });
        return stringBuilder.toString();
    }


    /**
     * Register a new listener for events (login result, incoming message, etc)
     * @param listener an object that wants updates from this client.
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     * @param listener an object that wants to unsubscribe from this object.
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        // TODO Step 4: Implement this method
        // Hint: all the onXXX() methods will be similar to onLoginResult()
        listeners.forEach(listener -> listener.onDisconnect());
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        // TODO Step 5: Implement this method
        listeners.forEach(listener -> listener.onUserList(users));
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        // TODO Step 7: Implement this method
        TextMessage textMessage = new TextMessage(sender, priv, text);
        listeners.forEach(listener -> listener.onMessageReceived(textMessage));
    }

    /**
     * Notify listeners that our message was not delivered
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        // TODO Step 7: Implement this method
        listeners.forEach(listener -> listener.onMessageError(errMsg));
    }

    /**
     * Notify listeners that command was not understood by the server.
     * @param errMsg Error message0
     */
    private void onCmdError(String errMsg) {
        // TODO Step 7: Implement this method
        listeners.forEach(listener -> listener.onCommandError(errMsg));
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        // TODO Step 8: Implement this method
        listeners.forEach(listener -> listener.onSupportedCommands(commands));
    }


}
