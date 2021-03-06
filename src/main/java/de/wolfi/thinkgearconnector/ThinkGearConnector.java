package de.wolfi.thinkgearconnector;

import com.google.gson.Gson;
import de.wolfi.thinkgearconnector.json.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by root on 16.05.2017.
 */
public class ThinkGearConnector {


    private boolean debug = false;

    public interface EventListener{
        public void processPacket(Packet in);
    }

    private Gson gson;



    class StreamThread extends Thread {
        private List<EventListener> listeners = new ArrayList<>();
        BufferedReader reader;
        OutputStreamWriter writer;
        private Socket socket;
        boolean running = false;
        private StreamThread(Socket socket) {
            try {
                this.socket = socket;
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new OutputStreamWriter(socket.getOutputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            this.running = true;
            while(this.running){
                if(socket.isClosed()){
                    try {
                        throw new InvalidObjectException("Socket is closed");
                    } catch (InvalidObjectException e) {
                        e.printStackTrace();
                    }
                }



                try {
                    if(reader.ready()){
                        String in = reader.readLine();
                        if(in.isEmpty()) continue;
                        if(debug)System.out.println(in);
                            Class<? extends Packet> clazz = null;
                            if(in.contains("status")) clazz = StatusPacket.class;
                            else if(in.contains("eSense")) clazz = ChannelPacket.class;
                            else if(in.contains("blink")) clazz = BlinkPacket.class;
                            else if(in.contains("raw")) clazz = RawPacket.class;

                            Packet packet = gson.fromJson(in, clazz);
                            listeners.forEach((p)->p.processPacket(packet));

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void writeJson(String json){
            Logger.getAnonymousLogger().log(Level.INFO,String.format("Writing: %s",json));
            try {
                writer.write(json);
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void close(){
            this.running = false;
        }


    }

    private String appName, sha_1, host;
    private int port = 13854;
    private StreamThread stream;
    public ThinkGearConnector(String appName, String SHA_1) {
        this.appName = appName;
        this.sha_1 = SHA_1;
        this.host = "127.0.0.1";
        gson = new Gson();
    }

    public void setHost(String host){
        this.setHost(host,this.port);    
    }

    public void setHost(String host, int port) {
        this.host = host;
        this.port = port;
    }


    public void open() throws IOException {
        this.stream = new StreamThread(new Socket(this.host,this.port));
        this.stream.start();
    }

    public void enableDebug(){
        debug = true;
    }
    public void auth(){
        this.stream.writeJson(String.format("{\"appName\":\"%s\",\"appKey\":\"%s\"}\n",this.appName,this.sha_1));
    }

    public void switchOutput(boolean enableRawOutput, String format){
        this.stream.writeJson(String.format("{\"enableRawOutput\":%s,\"format\":\"%s\"}\n",enableRawOutput,format));
    }
    public void registerEventHandler(EventListener e) {
        this.stream.listeners.add(e);
    }


    public void close(){
        this.stream.close();
    }
}
