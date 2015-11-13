package edu.usc.xinyu.telescope;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by damonster on 9/22/15.
 */
class AddressRetriever extends AsyncTask<String, Void, String> {

    AddressRetriever(TextView promptTextView, String serverAddress) {
        this.promptTextView = promptTextView;
        this.serverAddress = serverAddress;
    }

    AddressRetriever(TextView promptTextView){
        this.promptTextView = promptTextView;
    }

    private TextView promptTextView;

    private String serverAddress = "http://192.168.1.111:8090/webcam.ffm";
    private final static String addressServer = "http://web2.clarkson.edu/projects/cosi/sp2015/students/bruskajp/test2.html";
    private final static String LOG_TAG = "AddressRetriever";

    public void setPromptTextView(TextView promptTextView) {
        this.promptTextView = promptTextView;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    @Override
    protected String doInBackground(String... params) {
        promptTextView.setText("Getting server address...");
        try {
            URL url = new URL(addressServer);
            //URLConnection urlConnection = url.openConnection();
            //InputStream inputStream = urlConnection.getInputStream();
            //InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            //BufferedReader in = new BufferedReader(inputStreamReader);

            //while ((this.serverAddress = in.readLine()) != null) {}
            //in.close();
        } catch (MalformedURLException e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String s) {
        promptTextView.setText(serverAddress);
        serverAddress = "http://192.168.1.111:8090/webcam.ffm";
        //serverAddress = "http://192.168.1.102:8090/webcam.ffm";
        //serverAddress = "http://192.168.2.149:8090/webcam.ffm";
        Log.d(LOG_TAG, "Server=" + serverAddress);
    }
}
