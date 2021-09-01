package io.ballerina.projects.util;

public class Subscriber {
    public Subscriber() {
        ContentServer.getInstance().registerSubscriber(this);
    }
    
    public void receivedMessage(String s) {
        System.out.println(s);
    }
}