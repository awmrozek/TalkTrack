package com.amrozek.uploadcalllog;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.view.View;
import android.widget.Button;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "uploadcalllog";
    private static final int REQUEST_CODE_READ_CALL_LOG = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button uploadButton = findViewById(R.id.button_first);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readCallLog();
                readContacts();
            }
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {

            // Request both permissions
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS
            }, REQUEST_CODE_READ_CALL_LOG);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_READ_CALL_LOG) {
            boolean callLogPermissionGranted = false;
            boolean contactsPermissionGranted = false;

            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.READ_CALL_LOG)) {
                    callLogPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                } else if (permissions[i].equals(Manifest.permission.READ_CONTACTS)) {
                    contactsPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }

            if (callLogPermissionGranted) {
                readCallLog();
            } else {
                Log.e(TAG, "Permission denied to read call log");
            }

            if (contactsPermissionGranted) {
                readContacts();
            } else {
                Log.e(TAG, "Permission denied to read contacts");
            }
        }
    }

    private void readContacts() {
        Cursor cursor = getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME));

                // Now retrieve the phone number for this contact
                Cursor phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id},
                        null
                );

                while (phoneCursor != null && phoneCursor.moveToNext()) {
                    String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    //String businessName = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY));
                    Log.d(TAG, "Contact Name: " + name + ", Phone Number: " + phoneNumber);

                    // Create a map to hold the data
                    Map<String, String> callLogData = new HashMap<>();
                    callLogData.put("number", phoneNumber);
                    callLogData.put("name", name);

                    // URL to upload the data
                    String url = "https://10.0.0.113:3000/api/contacts";

                    // Call the upload function
                    UploadData(callLogData, url);
                }

                Log.i(TAG, "Registered Contact ID: " + id + ", Name: " + name);
            }
            cursor.close();
        } else {
            Log.e(TAG, "Cursor is null, unable to read contacts");
        }
    }


    private void readCallLog() {
        Log.i(TAG, "Reading call log...");
        Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                String date = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE));
                String duration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));

                Log.i(TAG, "Number: " + number + ", Type: " + type + ", Date: " + date + ", Duration: " + duration);
                // UploadCallLog(number, duration, date);

                // Create a map to hold the data
                Map<String, String> callLogData = new HashMap<>();
                callLogData.put("number", number);
                callLogData.put("duration", duration);
                callLogData.put("when_placed", date);
                callLogData.put("type", type);

                // URL to upload the data
                String url = "https://10.0.0.113:3000/api/data";

                // Call the upload function
                UploadData(callLogData, url);
            }
            cursor.close();
        } else {
            Log.e(TAG, "Cursor is null, unable to read call log");
        }
    }

    public void UploadData(Map<String, String> data, String urlString) {
        // Create a new thread to perform the network operation
        new Thread(() -> {
            Log.i(TAG, "Start Thread: Uploading data: " + data.toString());
            try {
                // Set up a TrustManager that accepts all certificates
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };

                // Install the all-trusting trust manager
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

                // Set a HostnameVerifier that accepts all hostnames
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Convert the map to a JSON string
                StringBuilder jsonInputString = new StringBuilder("{");
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    jsonInputString.append(String.format("\"%s\": \"%s\",", entry.getKey(), entry.getValue()));
                }
                // Remove the last comma and close the JSON object
                if (jsonInputString.length() > 1) {
                    jsonInputString.setLength(jsonInputString.length() - 1); // Remove last comma
                }
                jsonInputString.append("}");

                // Write the JSON data to the output stream
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Get the response code
                int responseCode = connection.getResponseCode();
                Log.i(TAG, "Response Code: " + responseCode);

                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error uploading call log", e);
            }
        }).start();
    }

}
