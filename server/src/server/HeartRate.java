package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;

public class HeartRate{
	ServerSocket serverSocket = null;
	Socket socket = null;
	DataInputStream dataInputStream = null;
	DataOutputStream dataOutputStream = null;
	double heartBeat = 0;
	double heartBeat0 = 0;
	String path = "pulse-cpp-master/heartbeat.txt";
	String path2 = "pulse-cpp-master/heartbeat_save.txt";
	double avgHeartBeat = 0;
	int avgHeartBeatCounter = 0;

	HeartRate(){
		try {
			serverSocket = new ServerSocket(8080);
			System.out.println("Listening :8080");			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void readHeartRate() {		
		try {
			FileReader fr = new FileReader(path);
			BufferedReader textReader = new BufferedReader(fr);
			heartBeat = Double.parseDouble(textReader.readLine());
			Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path2)));
			if (heartBeat != 0 && heartBeat != heartBeat0) {
				writer.write(Long.toString((long) heartBeat));
				avgHeartBeat += heartBeat;
				heartBeat0 = heartBeat;
				System.out.println(avgHeartBeatCounter);
				if ((++avgHeartBeatCounter % 15) == 0) {
					avgHeartBeat = avgHeartBeat / 15;
					System.out.println();
					System.out.println();
					System.out.println();
					System.out.println();
					System.out.println(avgHeartBeat);
					System.out.println();
					System.out.println();
					System.out.println();
					System.out.println();
					avgHeartBeat = 0;
				}
			}
			textReader.close();
			writer.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	public void sendHeartRate() {
		try {
			readHeartRate();
			socket = serverSocket.accept();
			dataInputStream = new DataInputStream(socket.getInputStream());
			dataOutputStream = new DataOutputStream(socket.getOutputStream());
			//System.out.println("ip: " + socket.getInetAddress());
			//System.out.println("message: " + dataInputStream.readUTF());
			dataOutputStream.writeUTF(Double.toString(heartBeat));
			System.out.println(heartBeat);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			if (dataInputStream != null) {
				try {
					dataInputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			if (dataOutputStream != null) {
				try {
					dataOutputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
