package edu.usc.xinyu.telescope;

import android.os.AsyncTask;
import android.widget.TextView;

import java.io.DataInputStream;
//import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;


/**
 * Created by damonster on 7/7/15.
 */
public class TalkToServer extends AsyncTask<Void, Void, Void> {
    String address;
    int port;
    String bpm = "";
    String bpm0 = "";
    //String msg;
    TextView recordingTextView;

    TalkToServer(String address0, int port0, String msg0, TextView recordingTextView0){
        address = address0;
        port = port0;
        //msg = msg0;
        recordingTextView = recordingTextView0;
    }

    @Override
    protected Void doInBackground(Void... params) {
        Socket socket = null;
        //DataOutputStream dataOutputStream = null;
        DataInputStream dataInputStream = null;

        try{
            socket = new Socket(address, 8080);
            //dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataInputStream = new DataInputStream(socket.getInputStream());

            //if (msg != null) {
            //    dataOutputStream.writeUTF(msg);
            //}

            bpm = dataInputStream.readUTF();

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally{
            if (socket != null){
                try {
                    socket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            //if (dataOutputStream != null){
            //    try {
            //        dataOutputStream.close();
            //    } catch (IOException e) {
            //        // TODO Auto-generated catch block
            //        e.printStackTrace();
            //    }
            //}

            if (dataInputStream != null){
                try {
                    dataInputStream.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        if(bpm != bpm0 || bpm != null){
            bpm0 = bpm;
            recordingTextView.setText(bpm);
        }
        super.onPostExecute(result);
    }
}

